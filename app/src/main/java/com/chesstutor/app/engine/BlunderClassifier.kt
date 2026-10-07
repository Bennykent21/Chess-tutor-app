package com.chesstutor.app.engine

import kotlin.math.roundToInt

enum class BlunderKind {
    NONE,
    MISSED_FORCED_MATE,
    WALKED_INTO_FORCED_MATE,
    CENTIPAWN_LOSS,
    UNKNOWN
}

data class BlunderVerdict(
    val kind: BlunderKind,
    val centipawnLoss: Int? = null,
    val winProbabilityLoss: Double = 0.0
) {
    val isBlunder get() = kind != BlunderKind.NONE && kind != BlunderKind.UNKNOWN
}

class BlunderClassifier(
    private val thresholdCentipawns: Int = 150,
    private val thresholdWinProbability: Double = 18.0
) {
    fun classify(
        beforeMove: PositionAnalysis,
        afterMove: PositionAnalysis,
        moverIsWhite: Boolean = true
    ): BlunderVerdict {
        val beforeMateForMover = moverRelativeMate(beforeMove.mateInMoves, moverIsWhite)
        val afterMateForMover = moverRelativeMate(afterMove.mateInMoves, moverIsWhite)
        val beforeCpForMover = moverRelativeCp(beforeMove.centipawns, moverIsWhite)
        val afterCpForMover = moverRelativeCp(afterMove.centipawns, moverIsWhite)

        val hadForcedMate = beforeMove.isForcedMate && (beforeMateForMover ?: 0) > 0
        val deliveredCheckmate = isDeliveredCheckmateForMover(afterMove, afterCpForMover)
        val stillForcingMate = deliveredCheckmate || (afterMove.isForcedMate && (afterMateForMover ?: 0) > 0)

        if (hadForcedMate && !stillForcingMate) {
            val winAfter = WinProbability.fromScore(afterCpForMover, afterMateForMover)
            return BlunderVerdict(
                kind = BlunderKind.MISSED_FORCED_MATE,
                centipawnLoss = beforeCpForMover?.let { (it - (afterCpForMover ?: 0)).coerceAtLeast(thresholdCentipawns) },
                winProbabilityLoss = (100.0 - winAfter).coerceAtLeast(thresholdWinProbability)
            )
        }
        if (hadForcedMate && stillForcingMate) {
            return BlunderVerdict(BlunderKind.NONE, centipawnLoss = 0, winProbabilityLoss = 0.0)
        }

        val opponentNowMatingUs = afterMove.isForcedMate && (afterMateForMover ?: 0) < 0
        val wasAlreadyLosingToMate = beforeMove.isForcedMate && (beforeMateForMover ?: 0) < 0
        if (opponentNowMatingUs && !wasAlreadyLosingToMate) {
            val winBefore = WinProbability.fromScore(beforeCpForMover, beforeMateForMover)
            return BlunderVerdict(
                kind = BlunderKind.WALKED_INTO_FORCED_MATE,
                centipawnLoss = beforeCpForMover?.let { (it - (afterCpForMover ?: -10000)).coerceAtLeast(thresholdCentipawns) },
                winProbabilityLoss = winBefore.coerceAtLeast(thresholdWinProbability)
            )
        }
        if (wasAlreadyLosingToMate && opponentNowMatingUs) {
            return BlunderVerdict(BlunderKind.NONE, centipawnLoss = 0, winProbabilityLoss = 0.0)
        }

        if (beforeMove.isForcedMate || afterMove.isForcedMate) {
            return BlunderVerdict(BlunderKind.UNKNOWN)
        }

        val before = beforeCpForMover ?: return BlunderVerdict(BlunderKind.UNKNOWN)
        val after = afterCpForMover ?: return BlunderVerdict(BlunderKind.UNKNOWN)
        val loss = (before - after).coerceAtLeast(0)

        val winBefore = WinProbability.fromCentipawns(before)
        val winAfter = WinProbability.fromCentipawns(after)
        val winLoss = (winBefore - winAfter).coerceAtLeast(0.0)

        // If the mover is still overwhelmingly winning (+600 cp or more) and lost < 8% win probability,
        // do not flag a minor evaluation fluctuation as a blunder.
        val stillOverwhelminglyWinning = before >= 600 && after >= 500 && winLoss < 8.0
        val isSignificantLoss = !stillOverwhelminglyWinning &&
            (loss >= thresholdCentipawns || winLoss >= thresholdWinProbability)

        return if (isSignificantLoss) {
            BlunderVerdict(
                kind = BlunderKind.CENTIPAWN_LOSS,
                centipawnLoss = loss.coerceAtLeast((winLoss * 10.0).roundToInt()),
                winProbabilityLoss = winLoss
            )
        } else {
            BlunderVerdict(BlunderKind.NONE, centipawnLoss = loss, winProbabilityLoss = winLoss)
        }
    }

    private fun moverRelativeMate(whitePerspectiveMate: Int?, moverIsWhite: Boolean): Int? {
        if (whitePerspectiveMate == null) return null
        if (whitePerspectiveMate == 0) return 0
        return if (moverIsWhite) whitePerspectiveMate else -whitePerspectiveMate
    }

    private fun moverRelativeCp(whitePerspectiveCp: Int?, moverIsWhite: Boolean): Int? {
        if (whitePerspectiveCp == null) return null
        return if (moverIsWhite) whitePerspectiveCp else -whitePerspectiveCp
    }

    private fun isDeliveredCheckmateForMover(afterMove: PositionAnalysis, afterCpForMover: Int?): Boolean {
        if (afterMove.mateInMoves == 0) {
            return afterMove.bestMoveUci == "0000" || (afterCpForMover ?: 0) >= 0
        }
        if (afterMove.bestMoveUci == "0000" && (afterCpForMover ?: 0) >= 9000) {
            return true
        }
        return false
    }
}
