package com.chesstutor.app.domain.account

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewCommentaryTest {

    private val start = Position.STARTING_FEN
    private val moves = listOf("e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6", "h5f7")
    private val positions = GameReviewer.positions(start, moves)
    private val evals = listOf(
        PlyEval(0, 20, null, "e2e4"), PlyEval(1, 20, null, "e7e5"), PlyEval(2, 20, null, "g1f3"),
        PlyEval(3, 20, null, "g8f6"), PlyEval(4, 30, null, "d1h5"), PlyEval(5, 50, null, "g7g6"),
        PlyEval(6, null, 1, "h5f7"), GameReviewer.terminalEval(7, positions.last())!!,
    )
    private val review = GameReviewer.review(start, moves, evals, bookPlies = 2)

    private fun comment(ply: Int, opening: String? = null) = ReviewCommentary.comment(review.move(ply)!!, evals[ply], positions[ply], opening)

    @Test
    fun commentsFollowTheMovesQuality() {
        assertEquals(MoveComment("e4 is a book move", "A known move in the Italian Game."), comment(1, "Italian Game: Giuoco Piano"))
        assertEquals("A known opening move.", comment(2).detail)
        assertEquals(MoveComment("Bc4 is best", "As strong as the engine's first choice, Nf3."), comment(3))
        assertEquals(MoveComment("Nf6 is a blunder", "It allows mate in 1, starting with Qxf7#. Best was g6."), comment(6))
        assertEquals(MoveComment("Qxf7# is best", "Checkmate!"), comment(7))
    }

    @Test
    fun aMoveThatHangsAPieceSaysWhatIsLost() {
        // 1.e4 d5?? (an invented blunder): exd5 wins the pawn.
        val line = listOf("e2e4", "d7d5")
        val pos = GameReviewer.positions(start, line)
        val ev = listOf(PlyEval(0, 20, null, "e2e4"), PlyEval(1, 20, null, "e7e5"), PlyEval(2, 400, null, "e4d5"))
        val move = GameReviewer.review(start, line, ev).move(2)!!
        assertEquals(MoveQuality.BLUNDER, move.quality)
        assertEquals("It lets exd5 win your pawn. Best was e5.", ReviewCommentary.comment(move, ev[2], pos[2]).detail)
    }

    @Test
    fun evaluationLabels() {
        assertEquals("+0.74", ReviewCommentary.evalLabel(PlyEval(0, 74, null, "")))
        assertEquals("-1.20", ReviewCommentary.evalLabel(PlyEval(0, -120, null, "")))
        assertEquals("0.00", ReviewCommentary.evalLabel(PlyEval(0, 0, null, "")))
        assertEquals("M3", ReviewCommentary.evalLabel(PlyEval(0, null, 3, "")))
        assertEquals("-M2", ReviewCommentary.evalLabel(PlyEval(0, null, -2, "")))
        assertEquals("1-0", ReviewCommentary.evalLabel(PlyEval(0, 10_000, 0, "")))
        assertEquals("", ReviewCommentary.evalLabel(null))
    }

    @Test
    fun theGameVerdictSpeaksToThePlayer() {
        assertEquals("The turning point was 3... Nf6. g6 would have kept you in the game.", ReviewCommentary.summary(review, PieceColor.BLACK, GameOutcome.LOSS))
        assertEquals("A clean win: not a single mistake or blunder. Well played!", ReviewCommentary.summary(review, PieceColor.WHITE, GameOutcome.WIN))
    }
}
