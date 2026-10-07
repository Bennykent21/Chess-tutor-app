package com.chesstutor.app.engine

import kotlin.math.exp

/**
 * Unified win-probability conversion from centipawns or mate distance.
 *
 * Uses the standard Lichess / Stockfish WDL logistic curve:
 *   win% = 50 + 50 * (2 / (1 + exp(-0.00368208 * cp)) - 1)
 */
object WinProbability {
    private const val K = 0.00368208

    /**
     * Returns win percentage in [0.0, 100.0] for the side whose centipawn advantage is [cp].
     */
    fun fromCentipawns(cp: Int): Double {
        val clamped = cp.coerceIn(-4000, 4000)
        return 50.0 + 50.0 * (2.0 / (1.0 + exp(-K * clamped.toDouble())) - 1.0)
    }

    /**
     * Returns win percentage in [0.0, 100.0] for a position with optional [centipawns] and [mateInMoves]
     * already expressed from the perspective of the target player.
     */
    fun fromScore(centipawns: Int?, mateInMoves: Int?): Double {
        if (mateInMoves != null) {
            return when {
                mateInMoves > 0 -> 100.0
                mateInMoves < 0 -> 0.0
                else -> if ((centipawns ?: 0) >= 0) 100.0 else 0.0
            }
        }
        return fromCentipawns(centipawns ?: 0)
    }

    /**
     * Returns White's UI bar fraction in [0.0, 1.0] from White-perspective [centipawns] and [mateInMoves].
     */
    fun whiteBarFraction(centipawns: Int?, mateInMoves: Int?): Float {
        if (mateInMoves != null) {
            return if (mateInMoves > 0) 1.0f else 0.0f
        }
        if (centipawns == null) return 0.5f
        val pct = fromCentipawns(centipawns) / 100.0
        return pct.toFloat().coerceIn(0.03f, 0.97f)
    }
}
