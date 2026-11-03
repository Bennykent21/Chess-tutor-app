package com.chesstutor.app.domain.puzzle

import kotlin.math.pow
import kotlin.math.roundToInt

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
