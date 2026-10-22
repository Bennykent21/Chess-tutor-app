package com.chesstutor.app.domain.puzzle

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The five worlds of the level path (plan section 6.3.4). Names and colours are original.
 * [accent] names a design-system accent; the UI maps it to a colour.
 */
enum class PuzzleWorld(val number: Int, val title: String, val tagline: String, val accent: WorldAccent, val badge: String) {
    MEADOW(1, "Sunrise Meadow", "First steps: mates in one and loose pieces", WorldAccent.LIME, "Meadow Explorer"),
    TIDES(2, "Tide Pools", "Forks, pins and the first combinations", WorldAccent.TEAL, "Tide Reader"),
    CAVES(3, "Crystal Caves", "Hidden moves and deeper tactics", WorldAccent.VIOLET, "Cave Diver"),
    RIDGE(4, "Ember Ridge", "Sharp attacks and precise endgames", WorldAccent.CORAL, "Ridge Climber"),
    SUMMIT(5, "Summit of Stars", "Master-level puzzles", WorldAccent.GOLD, "Star Seeker");

    val levels: IntRange get() = ((number - 1) * PuzzleLevels.PER_WORLD + 1)..(number * PuzzleLevels.PER_WORLD)

    companion object {
        fun of(level: Int): PuzzleWorld = entries.first { level in it.levels }
    }
}

enum class WorldAccent { LIME, TEAL, VIOLET, CORAL, GOLD }

/** The level path: 50 levels of rising difficulty, each a short set of puzzles. */
object PuzzleLevels {
    const val COUNT = 50
    const val PER_WORLD = 10
    const val PUZZLES_PER_LEVEL = 5
    /** Solve this many of a level's puzzles without revealing the answer to pass it. */
    const val TO_PASS = 4
    const val FIRST_RATING = 400
    const val LAST_RATING = 2400

    val range: IntRange = 1..COUNT

    /** Level 1 is about 400, level 50 about 2400, evenly spread (tune with real data). */
    fun targetRating(level: Int): Int {
        val n = level.coerceIn(1, COUNT)
        return (FIRST_RATING + (n - 1) * (LAST_RATING - FIRST_RATING) / (COUNT - 1.0)).roundToInt()
    }

    /**
     * The puzzles for one play of [level]. Different plays ([playIndex]) bring different
     * puzzles at the same difficulty, but the same play always gives the same set, so a
     * level survives the app restarting mid-way. Primary themes are varied where possible,
     * and [avoid] (for example puzzles already seen) is skipped while enough remain.
     */
    fun puzzlesFor(level: Int, playIndex: Int, pack: PuzzlePack, avoid: Set<String> = emptySet()): List<Puzzle> {
        if (pack.isEmpty) return emptyList()
        val target = targetRating(level)
        // Widen around the target until there is a choice of unseen puzzles (or nothing more to find).
        var window = 60
        var nearby: List<Puzzle>
        var fresh: List<Puzzle>
        while (true) {
            nearby = pack.puzzles.filter { abs(it.rating - target) <= window }
            fresh = nearby.filter { it.id !in avoid }
            if (fresh.size >= PUZZLES_PER_LEVEL * 3 || window > 1200) break
            window *= 2
        }
        val pool = if (fresh.size >= PUZZLES_PER_LEVEL) fresh else nearby
        val shuffled = pool.sortedBy { it.id }.shuffled(Random(level * 7919L + playIndex * 104_729L))
        val picked = mutableListOf<Puzzle>()
        val themes = mutableSetOf<String>()
        for (puzzle in shuffled) {
            if (picked.size == PUZZLES_PER_LEVEL) break
            if (themes.add(puzzle.primaryTheme)) picked += puzzle
        }
        for (puzzle in shuffled) {
            if (picked.size == PUZZLES_PER_LEVEL) break
            if (puzzle !in picked) picked += puzzle
        }
        return picked.sortedBy { it.rating }
    }

    /** A level is open once the one before it is passed. */
    fun isUnlocked(level: Int, passed: Set<Int>): Boolean = level == 1 || (level - 1) in passed

    /** The level to offer next: the first one not yet passed. */
    fun current(passed: Set<Int>): Int = range.firstOrNull { it !in passed } ?: COUNT
}

/** One finished puzzle as the reward rules see it. */
data class SolveRecord(
    val solved: Boolean,
    val clean: Boolean,
    val revealed: Boolean,
    val elapsedMs: Long,
    /** Consecutive clean solves including this one (0 if this one was not clean). */
    val cleanStreak: Int,
)

/** XP (plan section 6.3.4): base per solve, bonuses for speed and streak, none when revealed. */
object XpRules {
    const val CLEAN = 10
    const val HELPED = 4
    const val FAST_BONUS = 5
    const val QUICK_BONUS = 2
    const val FAST_MS = 20_000L
    const val QUICK_MS = 40_000L
    const val MAX_STREAK_BONUS = 5
    const val LEVEL_PASSED = 25
    const val WORLD_COMPLETED = 100

    fun forSolve(record: SolveRecord): Int {
        if (record.revealed || !record.solved) return 0
        if (!record.clean) return HELPED
        val speed = when {
            record.elapsedMs <= FAST_MS -> FAST_BONUS
            record.elapsedMs <= QUICK_MS -> QUICK_BONUS
            else -> 0
        }
        val streak = (record.cleanStreak - 1).coerceIn(0, MAX_STREAK_BONUS)
        return CLEAN + speed + streak
    }
}

/** The player's level from total XP; each level needs a little more than the last. */
data class PlayerLevel(val level: Int, val xpIntoLevel: Int, val xpForNext: Int) {
    val progress: Float get() = if (xpForNext == 0) 0f else xpIntoLevel.toFloat() / xpForNext

    companion object {
        fun xpNeeded(level: Int): Int = 50 + 10 * (level - 1)

        fun fromXp(totalXp: Int): PlayerLevel {
            var level = 1
            var left = totalXp.coerceAtLeast(0)
            while (left >= xpNeeded(level)) {
                left -= xpNeeded(level)
                level++
            }
            return PlayerLevel(level, left, xpNeeded(level))
        }
    }
}

/** Days in a row with at least one solved puzzle. Today still counts as "kept" until midnight. */
object DayStreak {
    fun compute(activeDays: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in activeDays) today else today.minusDays(1)
        var count = 0
        while (day in activeDays) {
            count++
            day = day.minusDays(1)
        }
        return count
    }
}

/** One play of a level: how many puzzles were solved without revealing the answer. */
data class LevelRun(val level: Int, val solvedWithoutReveal: Int, val played: Int) {
    val finished: Boolean get() = played >= PuzzleLevels.PUZZLES_PER_LEVEL
    val passed: Boolean get() = finished && solvedWithoutReveal >= PuzzleLevels.TO_PASS
}
