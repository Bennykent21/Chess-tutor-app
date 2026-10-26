package com.chesstutor.app.domain.account

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialMovesTest {

    private fun play(fen: String, uci: String): Triple<Position, Move, Position> {
        val before = Position.fromFen(fen)
        val move = LegalMoveGenerator.generateLegalMoves(before).first { it.uci == uci }
        return Triple(before, move, LegalMoveGenerator.makeMove(before, move))
    }

    @Test
    fun aBishopForAPawnOnH7IsASacrificeButAFairTradeIsNot() {
        // Bxh7+: the king takes the undefended bishop, so a bishop goes for a pawn.
        val (before, move, after) = play("6k1/5ppp/8/8/8/3B4/5PPP/6K1 w - - 0 1", "d3h7")
        assertEquals(200, SpecialMoves.sacrificed(before, move, after))
        assertTrue(SpecialMoves.isBrilliant(before, move, after, MoveQuality.BEST, winBefore = 60.0, winAfter = 59.0))
        // Not brilliant when it costs chances, when already won, or when the result is bad.
        assertFalse(SpecialMoves.isBrilliant(before, move, after, MoveQuality.BEST, winBefore = 60.0, winAfter = 50.0))
        assertFalse(SpecialMoves.isBrilliant(before, move, after, MoveQuality.BEST, winBefore = 98.0, winAfter = 98.0))
        assertFalse(SpecialMoves.isBrilliant(before, move, after, MoveQuality.BEST, winBefore = 40.0, winAfter = 40.0))
        assertFalse(SpecialMoves.isBrilliant(before, move, after, MoveQuality.INACCURACY, winBefore = 60.0, winAfter = 59.0))

        // Nxe5 dxe5: a knight for a knight.
        val (b2, m2, a2) = play("4k3/8/3p4/4n3/8/5N2/8/4K3 w - - 0 1", "f3e5")
        assertEquals(0, SpecialMoves.sacrificed(b2, m2, a2))
        assertFalse(SpecialMoves.isBrilliant(b2, m2, a2, MoveQuality.BEST, winBefore = 50.0, winAfter = 50.0))
    }

    @Test
    fun aPieceOnlyAPinnedAttackerCouldTakeIsNotGivenUp() {
        // After Nd5 the knight looks attacked by the e6 pawn, but that pawn is pinned to its king by the e1 rook.
        val (before, move, after) = play("4k3/8/4p3/8/8/2N5/8/4RK2 w - - 0 1", "c3d5")
        assertEquals(0, SpecialMoves.sacrificed(before, move, after))
    }

    @Test
    fun aGreatMoveIsTheOnlyGoodOneButNotAnObviousRecapture() {
        val (before, move, _) = play("4k3/8/8/3p4/8/8/8/3QK3 w - - 0 1", "d1d5")
        assertTrue(SpecialMoves.isGreat(before, move, MoveQuality.BEST, winBefore = 70.0, secondWin = 40.0, winAfter = 70.0, previous = null))
        assertFalse("the second move was nearly as good", SpecialMoves.isGreat(before, move, MoveQuality.BEST, 70.0, 62.0, 70.0, null))
        assertFalse("no second line known", SpecialMoves.isGreat(before, move, MoveQuality.BEST, 70.0, null, 70.0, null))
        assertFalse("not the best move", SpecialMoves.isGreat(before, move, MoveQuality.GOOD, 70.0, 40.0, 68.0, null))
        assertFalse("still lost afterwards", SpecialMoves.isGreat(before, move, MoveQuality.BEST, 25.0, 5.0, 25.0, null))
        assertFalse("taking back on the square just captured on", SpecialMoves.isGreat(before, move, MoveQuality.BEST, 70.0, 40.0, 70.0, previous = "c6d5"))
    }

    @Test
    fun theReviewLabelsAnOnlyMoveGreat() {
        val evals = listOf(
            PlyEval(0, centipawns = 30, mate = null, bestMove = "e2e4", secondCentipawns = -250, secondMate = null),
            PlyEval(1, centipawns = 30, mate = null, bestMove = "e7e5"),
        )
        val review = GameReviewer.review(Position.STARTING_FEN, listOf("e2e4"), evals)
        assertEquals(MoveQuality.GREAT, review.moves.single().quality)
        assertTrue(MoveQuality.GREAT.isTop)
        assertEquals("e4 is a great move", ReviewCommentary.comment(review.moves.single(), evals[1], null).headline)
        // Without the second line (an analysis from before version 12) it is just best.
        assertEquals(MoveQuality.BEST, GameReviewer.review(Position.STARTING_FEN, listOf("e2e4"), evals.map { it.copy(secondCentipawns = null) }).moves.single().quality)
    }
}
