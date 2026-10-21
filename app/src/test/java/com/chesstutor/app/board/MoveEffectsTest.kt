package com.chesstutor.app.board

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoveEffectsTest {

    private fun sq(name: String) = Square.fromAlgebraic(name)

    /** The legal move with this UCI text, so flags like castling are what the rules say. */
    private fun legal(position: Position, uci: String): Move =
        LegalMoveGenerator.generateLegalMoves(position).first { it.uci == uci }

    private fun effects(fen: String, uci: String): Pair<MoveAnimationPlan, BoardFeedback> {
        val before = Position.fromFen(fen)
        val move = legal(before, uci)
        val after = LegalMoveGenerator.makeMove(before, move)
        return MoveEffects.animationPlan(before, move) to MoveEffects.feedbackFor(before, move, after)
    }

    @Test
    fun aQuietMoveSlidesOnePieceWithALightTick() {
        val (plan, feedback) = effects(Position.STARTING_FEN, "e2e4")
        assertEquals(listOf(PieceSlide(Piece(PieceType.PAWN, PieceColor.WHITE), sq("e2"), sq("e4"))), plan.slides)
        assertNull(plan.captured)
        assertEquals(BoardFeedback(BoardSound.MOVE, HapticPattern.TICK), feedback)
    }

    @Test
    fun aCaptureFadesTheTakenPieceWithAFirmBuzz() {
        val (plan, feedback) = effects("rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2", "e4d5")
        assertEquals(sq("d5") to Piece(PieceType.PAWN, PieceColor.BLACK), plan.captured)
        assertEquals(BoardFeedback(BoardSound.CAPTURE, HapticPattern.FIRM), feedback)
    }

    @Test
    fun castlingSlidesKingAndRook() {
        val (kingside, feedback) = effects("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1", "e1g1")
        assertEquals(listOf(sq("e1") to sq("g1"), sq("h1") to sq("f1")), kingside.slides.map { it.from to it.to })
        assertEquals(BoardSound.CASTLE, feedback.sound)

        val (queenside, _) = effects("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R b KQkq - 0 1", "e8c8")
        assertEquals(listOf(sq("e8") to sq("c8"), sq("a8") to sq("d8")), queenside.slides.map { it.from to it.to })
    }

    @Test
    fun enPassantRemovesThePawnBesideTheTarget() {
        val (plan, feedback) = effects("rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3", "e5f6")
        assertEquals(sq("f5") to Piece(PieceType.PAWN, PieceColor.BLACK), plan.captured)
        assertEquals(BoardSound.CAPTURE, feedback.sound)
    }

    @Test
    fun promotionSlidesThePawnAndSoundsLikeAPromotion() {
        // King on h7: a knight on e8 would check g7, and check outranks promotion.
        val (plan, feedback) = effects("8/4P2k/8/8/8/8/6K1/8 w - - 0 1", "e7e8n")
        assertEquals(Piece(PieceType.PAWN, PieceColor.WHITE), plan.slides.single().piece)
        assertEquals(BoardSound.PROMOTE, feedback.sound)
    }

    @Test
    fun checkBeatsEveryOtherSound() {
        // Promoting to a queen on e8 checks the king on g8 along the rank.
        val (_, promoteCheck) = effects("6k1/4P3/8/8/8/8/8/K7 w - - 0 1", "e7e8q")
        assertEquals(BoardFeedback(BoardSound.CHECK, HapticPattern.FIRM), promoteCheck)
        // Bxf7+ captures and checks.
        val (_, captureCheck) = effects("r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 2 3", "c4f7")
        assertEquals(BoardSound.CHECK, captureCheck.sound)
    }
}
