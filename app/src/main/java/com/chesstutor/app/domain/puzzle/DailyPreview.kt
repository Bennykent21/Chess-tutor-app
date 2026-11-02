package com.chesstutor.app.domain.puzzle

/** The position today's puzzle opens on, from the side that solves it. */
data class DailyPreview(val puzzleId: String, val fen: String, val whiteAtBottom: Boolean) {
    companion object {
        /** Null for a puzzle that cannot be set up. */
        fun of(puzzle: Puzzle): DailyPreview? =
            PuzzleSession.start(puzzle)?.let { DailyPreview(puzzle.id, it.fen, whiteAtBottom = it.solverColor == 'w') }
    }
}
