package com.chesstutor.app.engine

enum class BlunderKind {
    NONE,
    MISSED_FORCED_MATE,
    WALKED_INTO_FORCED_MATE,
    CENTIPAWN_LOSS,
    UNKNOWN
}

data class BlunderVerdict(val kind: BlunderKind, val centipawnLoss: Int? = null) {
    val isBlunder get() = kind != BlunderKind.NONE && kind != BlunderKind.UNKNOWN
}

class BlunderClassifier(private val thresholdCentipawns: Int = 150) {
    fun classify(beforeMove: PositionAnalysis, afterMove: PositionAnalysis): BlunderVerdict {
        val hadForcedMate = beforeMove.isForcedMate && (beforeMove.mateInMoves ?: 0) > 0
        val stillForcingMate = afterMove.isForcedMate && (afterMove.mateInMoves ?: 0) < 0
        if (hadForcedMate && !stillForcingMate) return BlunderVerdict(BlunderKind.MISSED_FORCED_MATE)

        val opponentNowMatingUs = afterMove.isForcedMate && (afterMove.mateInMoves ?: 0) < 0
        val wasAlreadyLosingToMate = beforeMove.isForcedMate && (beforeMove.mateInMoves ?: 0) < 0
        if (opponentNowMatingUs && !wasAlreadyLosingToMate) return BlunderVerdict(BlunderKind.WALKED_INTO_FORCED_MATE)

        if (beforeMove.isForcedMate || afterMove.isForcedMate) return BlunderVerdict(BlunderKind.UNKNOWN)

        val before = beforeMove.centipawns ?: return BlunderVerdict(BlunderKind.UNKNOWN)
        val after = afterMove.centipawns ?: return BlunderVerdict(BlunderKind.UNKNOWN)
        val loss = before - (-after)

        return if (loss >= thresholdCentipawns) BlunderVerdict(BlunderKind.CENTIPAWN_LOSS, loss)
        else BlunderVerdict(BlunderKind.NONE)
    }
}
