package com.chesstutor.app.domain.puzzle

import java.time.LocalDate
import kotlin.math.abs
import kotlin.random.Random

/** How hard a themed run should be, relative to the player's puzzle rating. */
enum class Difficulty(val label: String, val offset: Int) {
    EASIER("Easier", -250),
    MATCHED("Matched", 0),
    HARDER("Harder", 250),
}

/** Rating bands for a custom run. */
enum class RatingBand(val label: String, val range: IntRange) {
    BEGINNER("400 to 800", 400..799),
    CASUAL("800 to 1200", 800..1199),
    CLUB("1200 to 1600", 1200..1599),
    STRONG("1600 to 2000", 1600..1999),
    EXPERT("2000 and up", 2000..3000),
}

/** Puzzle choice for each mode of plan section 6.3.2. Pure, so it is unit tested. */
object PuzzleModes {

    /**
     * The daily puzzle: the same puzzle for everyone on a date, chosen from the pack by the
     * date alone, so no server is needed. Picked from mid-rated puzzles so most players can
     * have a go.
     */
    fun daily(pack: PuzzlePack, date: LocalDate): Puzzle? {
        val pool = pack.puzzles.filter { it.rating in 1000..1800 }.ifEmpty { pack.puzzles }
        if (pool.isEmpty()) return null
        val index = Math.floorMod(date.toEpochDay() * 2_654_435_761L, pool.size.toLong()).toInt()
        return pool.sortedBy { it.id }[index]
    }

    /** The pattern tags found in [pack] among puzzles with [goal], most common first. */
    fun patternsFor(goal: PuzzleGoal, pack: PuzzlePack): List<String> =
        pack.puzzles.asSequence()
            .filter { PuzzleGoal.of(it) == goal }
            .flatMap { p -> (p.themes + p.primaryTheme).filter(PuzzleThemes::isKnown) }
            .groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }

    /** [count] puzzles for a goal (and optional pattern) near [rating], widening as needed. */
    fun themed(
        pack: PuzzlePack,
        goal: PuzzleGoal,
        pattern: String?,
        rating: Int,
        count: Int,
        seed: Long,
        avoid: Set<String> = emptySet(),
    ): List<Puzzle> {
        val matching = pack.puzzles.filter { p ->
            PuzzleGoal.of(p) == goal && (pattern == null || pattern in p.themes || p.primaryTheme == pattern)
        }
        return nearRating(matching, rating, count, seed, avoid)
    }

    /** [count] puzzles with any of [themes] near [rating]: a lesson's "Related puzzles". */
    fun related(
        pack: PuzzlePack,
        themes: Collection<String>,
        rating: Int,
        count: Int,
        seed: Long,
        avoid: Set<String> = emptySet(),
    ): List<Puzzle> {
        val wanted = themes.toSet()
        val matching = pack.puzzles.filter { p -> p.primaryTheme in wanted || p.themes.any { it in wanted } }
        return nearRating(matching, rating, count, seed, avoid)
    }

    /** [count] puzzles in [band], optionally with [pattern]. */
    fun custom(pack: PuzzlePack, band: RatingBand, pattern: String?, count: Int, seed: Long, avoid: Set<String> = emptySet()): List<Puzzle> {
        val matching = pack.puzzles.filter { p ->
            p.rating in band.range && (pattern == null || pattern in p.themes || p.primaryTheme == pattern)
        }
        val fresh = matching.filter { it.id !in avoid }
        val pool = if (fresh.size >= count) fresh else matching
        return pool.sortedBy { it.id }.shuffled(Random(seed)).take(count).sortedBy { it.rating }
    }

    /** Puzzles to practise again, in the order given, skipping any no longer in the pack. */
    fun mistakes(pack: PuzzlePack, ids: List<String>, count: Int = 10): List<Puzzle> =
        ids.mapNotNull(pack::get).take(count)

    private fun nearRating(pool: List<Puzzle>, rating: Int, count: Int, seed: Long, avoid: Set<String>): List<Puzzle> {
        if (pool.isEmpty()) return emptyList()
        var window = 100
        var near: List<Puzzle>
        while (true) {
            near = pool.filter { abs(it.rating - rating) <= window && it.id !in avoid }
            if (near.size >= count * 2 || window > 2000) break
            window *= 2
        }
        if (near.size < count) near = pool.filter { abs(it.rating - rating) <= window }
        return near.sortedBy { it.id }.shuffled(Random(seed)).take(count).sortedBy { it.rating }
    }
}

/**
 * Timed rush (plan section 6.3.2): puzzles get harder as you solve them. Starts a little
 * below the player's rating and climbs with every solve; never serves a puzzle twice.
 */
class RushSelector(
    private val pack: PuzzlePack,
    private val startRating: Int,
    seed: Long,
) {
    private val random = Random(seed)
    private val used = HashSet<String>()

    fun next(solvedSoFar: Int): Puzzle? {
        val target = startRating + solvedSoFar * STEP
        var window = 75
        while (window <= 3000) {
            val near = pack.puzzles.filter { abs(it.rating - target) <= window && it.id !in used }
            if (near.isNotEmpty()) return near[random.nextInt(near.size)].also { used += it.id }
            window *= 2
        }
        return null
    }

    companion object {
        /** Rating added per solve. */
        const val STEP = 60

        /** Where a rush starts for a player rated [rating]. */
        fun startFor(rating: Int): Int = (rating - 300).coerceAtLeast(PuzzleRating.MIN)
    }
}
