package com.chesstutor.app.domain.account

import com.chesstutor.app.core.PieceColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LichessAccuracyTest {

    @Test
    fun winPercentMatchesLichess() {
        assertEquals(50.0, LichessAccuracy.winPercent(0, null), 1e-9)
        assertEquals("scores are capped at ten pawns", LichessAccuracy.winPercent(1000, null), LichessAccuracy.winPercent(5000, null), 1e-9)
        assertEquals(97.5447, LichessAccuracy.winPercent(null, 3), 0.0001)
        assertEquals("a delivered mate (mate 0) reads from the score's sign", 2.4553, LichessAccuracy.winPercent(-10_000, 0), 0.0001)
    }

    @Test
    fun moveAccuracyFollowsChessComsSteeperCurve() {
        assertEquals(100.0, LichessAccuracy.moveAccuracy(50.0, 50.0), 1e-9)
        assertEquals("gaining chances is perfect", 100.0, LichessAccuracy.moveAccuracy(40.0, 60.0), 1e-9)
        // Lichess gives a ten-point drop 64.6; chess.com's numbers fit a steeper curve (D89).
        assertEquals(30.17, LichessAccuracy.moveAccuracy(60.0, 50.0), 0.01)
        assertEquals(0.0, LichessAccuracy.moveAccuracy(100.0, 0.0), 1e-9)
    }

    @Test
    fun gameAccuracyAveragesEachSidesMovesAfterTheBook() {
        assertNull(LichessAccuracy.gameAccuracy(listOf(50.0))[PieceColor.WHITE])
        val calm = LichessAccuracy.gameAccuracy(List(21) { 52.0 })
        assertEquals(100.0, calm[PieceColor.WHITE]!!, 1e-9)
        assertEquals(100.0, calm[PieceColor.BLACK]!!, 1e-9)

        // Black blunders once (ply 6): White stays perfect, Black's five moves average one at zero.
        val wins = listOf(52.0, 52.0, 52.0, 52.0, 52.0, 52.0, 90.0, 90.0, 90.0, 90.0, 90.0)
        val acc = LichessAccuracy.gameAccuracy(wins)
        assertEquals(100.0, acc[PieceColor.WHITE]!!, 1e-9)
        assertEquals(80.0, acc[PieceColor.BLACK]!!, 1e-9)
        assertEquals("book moves don't count", 75.0, LichessAccuracy.gameAccuracy(wins, bookPlies = 2)[PieceColor.BLACK]!!, 1e-9)

        val blackFirst = LichessAccuracy.gameAccuracy(listOf(50.0, 30.0), firstMover = PieceColor.BLACK)
        assertEquals("Black gained, so Black's move was perfect", 100.0, blackFirst[PieceColor.BLACK]!!, 1e-9)
        assertNull(blackFirst[PieceColor.WHITE])
    }
}
