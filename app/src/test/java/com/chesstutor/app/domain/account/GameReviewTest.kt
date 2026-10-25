package com.chesstutor.app.domain.account

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameReviewTest {

    private val start = Position.STARTING_FEN

    /** Scholar's mate: 3...Nf6?? walks into Qxf7#. */
    private val moves = listOf("e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6", "h5f7")

    private val evals = listOf(
        PlyEval(0, 20, null, "e2e4"),
        PlyEval(1, 20, null, "e7e5"),
        PlyEval(2, 20, null, "g1f3"),
        PlyEval(3, 20, null, "g8f6"),
        PlyEval(4, 30, null, "d1h5"),
        PlyEval(5, 50, null, "g7g6"),
        PlyEval(6, null, 1, "h5f7"),
        GameReviewer.terminalEval(7, GameReviewer.positions(start, moves).last())!!,
    )

    @Test
    fun thresholdsFollowLostWinningChances() {
        assertEquals(MoveQuality.BEST, MoveQuality.of(30.0, playedBest = true))
        assertEquals(MoveQuality.BEST, MoveQuality.of(0.8, playedBest = false))
        assertEquals(MoveQuality.GOOD, MoveQuality.of(4.0, playedBest = false))
        assertEquals(MoveQuality.INACCURACY, MoveQuality.of(8.0, playedBest = false))
        assertEquals(MoveQuality.MISTAKE, MoveQuality.of(15.0, playedBest = false))
        assertEquals(MoveQuality.BLUNDER, MoveQuality.of(25.0, playedBest = false))
        assertTrue(MoveQuality.BLUNDER.isError)
        assertFalse(MoveQuality.GOOD.isError)
    }

    @Test
    fun finishedGamesAreScoredWithoutTheEngine() {
        val mate = GameReviewer.terminalEval(7, GameReviewer.positions(start, moves).last())!!
        assertEquals(0, mate.mate)
        assertEquals("Black is mated, so White has won", 100.0, mate.whiteWin, 0.001)
        val stalemate = GameReviewer.terminalEval(0, Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"))!!
        assertNull(stalemate.mate)
        assertEquals(50.0, stalemate.whiteWin, 0.001)
        assertNull("moves remain", GameReviewer.terminalEval(0, Position.initial()))
    }

    @Test
    fun judgesEveryMoveAndFindsTheBlunder() {
        val review = GameReviewer.review(start, moves, evals)
        assertTrue(review.complete)
        assertEquals(7, review.moves.size)
        assertEquals(8, review.graph.size)
        assertEquals(1f, review.graph.last(), 0.001f)

        val blunder = review.move(6)!!
        assertEquals("Nf6", blunder.san)
        assertEquals(PieceColor.BLACK, blunder.mover)
        assertEquals(MoveQuality.BLUNDER, blunder.quality)
        assertEquals("g6", blunder.bestSan)
        assertEquals("3...", blunder.numberLabel)
        assertEquals(0.0, blunder.winAfter, 0.001)

        assertEquals("Qxf7#", review.move(7)!!.san)
        assertEquals(MoveQuality.BEST, review.move(7)!!.quality)
        assertEquals(listOf(6), review.keyMoments(PieceColor.BLACK).map { it.ply })
        assertTrue(review.keyMoments(PieceColor.WHITE).isEmpty())

        val white = review.report(PieceColor.WHITE)
        val black = review.report(PieceColor.BLACK)
        assertEquals(4, white.count(MoveQuality.BEST))
        assertEquals(1, black.count(MoveQuality.BLUNDER))
        assertTrue("White played perfectly: ${white.accuracy}", white.accuracy!! > 99.0)
        assertTrue("one blunder drags Black down: ${black.accuracy}", black.accuracy!! < 80.0)
    }

    @Test
    fun aPartialAnalysisReviewsWhatItCan() {
        val review = GameReviewer.review(start, moves, evals.take(4))
        assertFalse(review.complete)
        assertEquals(3, review.moves.size)
        assertEquals(4, review.graph.size)
        assertNull(review.move(4))
        assertEquals(7, review.totalPlies)
    }

    @Test
    fun bookMovesAreMarkedAndCountAsAccurate() {
        val review = GameReviewer.review(start, moves, evals, bookPlies = 2)
        assertEquals(listOf(MoveQuality.BOOK, MoveQuality.BOOK), review.moves.take(2).map { it.quality })
        assertEquals(1, review.report(PieceColor.WHITE).count(MoveQuality.BOOK))
        assertEquals(100.0, review.moves.first().accuracy, 0.001)
    }

    @Test
    fun accuracyPunishesBigLosses() {
        assertEquals(100.0, GameReviewer.moveAccuracy(0.0), 0.01)
        assertTrue(GameReviewer.moveAccuracy(10.0) in 60.0..70.0)
        assertEquals(0.0, GameReviewer.moveAccuracy(100.0), 0.01)
        assertNull(GameReviewer.accuracyOf(emptyList()))
        val steady = GameReviewer.accuracyOf(listOf(90.0, 90.0))!!
        val spiky = GameReviewer.accuracyOf(listOf(100.0, 80.0))!!
        assertEquals(90.0, steady, 0.001)
        assertTrue("same average, but a bad move weighs more", spiky < steady)
    }
}
