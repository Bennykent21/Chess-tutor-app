package com.chesstutor.app.domain.account

import com.chesstutor.app.core.PieceColor
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Game accuracy exactly as Lichess publishes it (lila `AccuracyPercent` and `WinPercent`), so
 * a game reviewed here and on Lichess gives close numbers. Chess.com's "CAPS" formula is not
 * public, so its numbers can differ by several points either way.
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
        val raw = 103.1668100711649 * exp(-0.04354415386753951 * (before - after)) - 3.166924740191411
        return (raw + 1).coerceIn(0.0, 100.0)
    }

    /**
     * Each side's accuracy from White's winning chances at every position ([whiteWins][0] is
     * the start). Moves in sharp stretches of the game weigh more (the spread of winning
     * chances around them), then the weighted mean and the harmonic mean are averaged.
     */
    fun gameAccuracy(whiteWins: List<Double>, firstMover: PieceColor = PieceColor.WHITE): Map<PieceColor, Double?> {
        val moves = whiteWins.size - 1
        if (moves < 1) return mapOf(PieceColor.WHITE to null, PieceColor.BLACK to null)
        val windowSize = (moves / 10).coerceIn(2, 8)
        val first = whiteWins.take(windowSize)
        val windows = List((windowSize - 2).coerceAtLeast(0)) { first } + whiteWins.windowed(windowSize, partialWindows = moves + 1 < windowSize)
        val weights = windows.map { standardDeviation(it).coerceIn(0.5, 12.0) }

        val byColor = HashMap<PieceColor, MutableList<Pair<Double, Double>>>()
        for (i in 0 until moves) {
            val color = if (i % 2 == 0) firstMover else firstMover.opposite()
            val (before, after) = if (color == PieceColor.WHITE) whiteWins[i] to whiteWins[i + 1] else (100 - whiteWins[i]) to (100 - whiteWins[i + 1])
            byColor.getOrPut(color) { mutableListOf() } += moveAccuracy(before, after) to weights.getOrElse(i) { weights.last() }
        }
        return PieceColor.entries.associateWith { color ->
            val list = byColor[color].orEmpty()
            if (list.isEmpty()) null else {
                val weighted = list.sumOf { it.first * it.second } / list.sumOf { it.second }
                val harmonic = list.size / list.sumOf { 1.0 / it.first.coerceAtLeast(MIN_ACCURACY) }
                (weighted + harmonic) / 2
            }
        }
    }

    private fun standardDeviation(xs: List<Double>): Double {
        val mean = xs.average()
        return sqrt(xs.sumOf { (it - mean) * (it - mean) } / xs.size)
    }

    private const val CEILING = 1000
    /** Keeps a 0% move from dividing by zero in the harmonic mean (it still pulls the result right down). */
    private const val MIN_ACCURACY = 0.01
}
