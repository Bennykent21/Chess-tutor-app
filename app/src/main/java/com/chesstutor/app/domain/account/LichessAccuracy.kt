package com.chesstutor.app.domain.account

import com.chesstutor.app.core.PieceColor
import kotlin.math.exp

/**
 * Game accuracy on Lichess's winning chances (lila `WinPercent`) and the shape of its move
 * accuracy (`AccuracyPercent`), tuned to chess.com (D89): over six games the developer reviewed on
 * chess.com, the plain mean of each side's non-book moves on a steeper curve came within about four
 * points of chess.com's accuracy, where Lichess's volatility weighting was off by about seven.
 * Chess.com's "CAPS" formula is not public, so its numbers still differ a little.
 */
object LichessAccuracy {

    /** Lichess's winning chances, 0 to 100: scores are capped at ten pawns and a mate counts as ten pawns. */
    fun winPercent(centipawns: Int?, mate: Int?): Double {
        val cp = when {
            mate != null && mate != 0 -> if (mate > 0) CEILING else -CEILING
            mate == 0 -> if ((centipawns ?: 0) >= 0) CEILING else -CEILING
            else -> (centipawns ?: 0).coerceIn(-CEILING, CEILING)
        }
        return 50 + 50 * (2 / (1 + exp(-0.00368208 * cp)) - 1)
    }

    /** One move's accuracy from the mover's winning chances before and after (with Lichess's one-point bonus). */
    fun moveAccuracy(before: Double, after: Double): Double {
        if (after >= before) return 100.0
        val raw = 103.1668100711649 * exp(-STEEPNESS * (before - after)) - 3.166924740191411
        return (raw + 1).coerceIn(0.0, 100.0)
    }

    /**
     * Each side's accuracy from White's winning chances at every position ([whiteWins][0] is
     * the start): the plain mean of its moves' accuracies after the first [bookPlies] plies.
     */
    fun gameAccuracy(whiteWins: List<Double>, firstMover: PieceColor = PieceColor.WHITE, bookPlies: Int = 0): Map<PieceColor, Double?> {
        val byColor = HashMap<PieceColor, MutableList<Double>>()
        for (i in bookPlies until whiteWins.size - 1) {
            val color = if (i % 2 == 0) firstMover else firstMover.opposite()
            val (before, after) = if (color == PieceColor.WHITE) whiteWins[i] to whiteWins[i + 1] else (100 - whiteWins[i]) to (100 - whiteWins[i + 1])
            byColor.getOrPut(color) { mutableListOf() } += moveAccuracy(before, after)
        }
        return PieceColor.entries.associateWith { color -> byColor[color]?.average() }
    }

    /** Lichess uses 0.0435; chess.com's accuracies fit 0.116 (0.110 to 0.124 with any one game left out). */
    private const val STEEPNESS = 0.116

    private const val CEILING = 1000
}
