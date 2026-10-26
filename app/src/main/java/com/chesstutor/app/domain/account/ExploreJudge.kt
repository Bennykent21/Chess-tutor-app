package com.chesstutor.app.domain.account

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Position
import com.chesstutor.app.engine.EngineLine

/** One move judged on the Explore board: its verdict and the coach's words. */
data class JudgedMove(val move: ReviewedMove, val comment: MoveComment)

/**
 * Explore (D32): every move on the free board is judged like a move in a game review, from
 * the engine's view of the position before and after it. Pure, so it is unit tested.
 */
object ExploreJudge {

    /** The engine's view of [position] from its candidate [lines] (best first), or the score of a finished game. */
    fun evalOf(position: Position, lines: List<EngineLine>, lite: Boolean = false): PlyEval? {
        GameReviewer.terminalEval(0, position)?.let { return it }
        val best = lines.firstOrNull() ?: return null
        val first = best.firstMove ?: return null
        val second = lines.getOrNull(1)
        return PlyEval(
            ply = 0,
            centipawns = best.centipawns.takeIf { best.mateInMoves == null },
            mate = best.mateInMoves,
            bestMove = first,
            line = best.moves.take(GameAnalyzer.MAX_LINE),
            depth = best.depth,
            lite = lite,
            secondCentipawns = second?.centipawns.takeIf { second?.mateInMoves == null },
            secondMate = second?.mateInMoves,
        )
    }

    /**
     * Judges [uci] played from [before] (the position, and the engine's view of it) to the
     * engine's view of the position after it. Null when the move is not legal there.
     */
    fun judge(before: Position, uci: String, beforeEval: PlyEval, afterEval: PlyEval): JudgedMove? {
        val legal = LegalMoveGenerator.generateLegalMoves(before).firstOrNull { it.uci == uci } ?: return null
        val after = LegalMoveGenerator.makeMove(before, legal)
        val review = GameReviewer.review(before.toFen(), listOf(uci), listOf(beforeEval.copy(ply = 0), afterEval.copy(ply = 1)))
        val move = review.moves.firstOrNull() ?: return null
        return JudgedMove(move, ReviewCommentary.comment(move, afterEval.copy(ply = 1), after))
    }
}
