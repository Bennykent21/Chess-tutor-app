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
    fun classify(
        beforeMove: PositionAnalysis,
        afterMove: PositionAnalysis,
        moverIsWhite: Boolean = true
    ): BlunderVerdict {
        val beforeMateForMover = beforeMove.mateInMoves?.let { if (moverIsWhite) it else -it }
        val afterMateForMover = afterMove.mateInMoves?.let {
            if (it == 0) 0 else if (moverIsWhite) it else -it
        }

        val hadForcedMate = beforeMove.isForcedMate && (beforeMateForMover ?: 0) > 0
        val deliveredCheckmate = afterMove.mateInMoves == 0 && (
            afterMove.bestMoveUci == "0000" ||
                (if (moverIsWhite) (afterMove.centipawns ?: 0) >= 0 else (afterMove.centipawns ?: 0) <= 0)
            )
        val stillForcingMate = deliveredCheckmate || (afterMove.isForcedMate && (afterMateForMover ?: 0) > 0)
        if (hadForcedMate && !stillForcingMate) return BlunderVerdict(BlunderKind.MISSED_FORCED_MATE)
        if (hadForcedMate && stillForcingMate) return BlunderVerdict(BlunderKind.NONE)

        val opponentNowMatingUs = afterMove.isForcedMate && (afterMateForMover ?: 0) < 0
        val wasAlreadyLosingToMate = beforeMove.isForcedMate && (beforeMateForMover ?: 0) < 0
        if (opponentNowMatingUs && !wasAlreadyLosingToMate) return BlunderVerdict(BlunderKind.WALKED_INTO_FORCED_MATE)

        if (beforeMove.isForcedMate || afterMove.isForcedMate) return BlunderVerdict(BlunderKind.UNKNOWN)

        val before = beforeMove.centipawns ?: return BlunderVerdict(BlunderKind.UNKNOWN)
        val after = afterMove.centipawns ?: return BlunderVerdict(BlunderKind.UNKNOWN)
        val loss = if (moverIsWhite) before - after else after - before

        return if (loss >= thresholdCentipawns) BlunderVerdict(BlunderKind.CENTIPAWN_LOSS, loss)
        else BlunderVerdict(BlunderKind.NONE)
    }
}
