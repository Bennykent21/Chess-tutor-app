package com.chesstutor.app.domain.puzzle

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/** Picks the next puzzle: close to the player's puzzle rating, optionally for one theme. */
class PuzzleSelector(
    private val pack: PuzzlePack,
    private val random: Random = Random.Default
) {
    /**
     * @param userRating the player's current puzzle rating
     * @param theme restrict to one primary theme (e.g. "fork"), or null for any
     * @param excludeIds puzzles to skip (already solved or just seen)
     * @param window starting rating window (+/-); it doubles until something is found so the
     *               player is never stuck with an empty result just because the pack is sparse
     * @return a puzzle, or null only if no puzzle matches the theme/exclusions at all
     */
    fun next(
        userRating: Int,
        theme: String? = null,
        excludeIds: Set<String> = emptySet(),
        window: Int = 150
    ): Puzzle? {
        val pool = pack.puzzles.filter {
            it.id !in excludeIds && (theme == null || it.primaryTheme == theme)
        }
        if (pool.isEmpty()) return null

        var w = window.coerceAtLeast(1)
        while (true) {
            val near = pool.filter { kotlin.math.abs(it.rating - userRating) <= w }
            if (near.isNotEmpty()) return near[random.nextInt(near.size)]
            if (w > 4000) return pool[random.nextInt(pool.size)]
            w *= 2
        }
    }
}

/** Standard Elo update used for the player's puzzle rating. */
object PuzzleRating {
    const val MIN = 400
    const val MAX = 3000

    fun expectedScore(userRating: Int, puzzleRating: Int): Double =
        1.0 / (1.0 + 10.0.pow((puzzleRating - userRating) / 400.0))

    /** @param solved true only for a clean solve (no mistakes, hints or reveal) */
    fun update(userRating: Int, puzzleRating: Int, solved: Boolean, k: Int = 32): Int {
        val score = if (solved) 1.0 else 0.0
        val next = userRating + k * (score - expectedScore(userRating, puzzleRating))
        return next.roundToInt().coerceIn(MIN, MAX)
    }
}
