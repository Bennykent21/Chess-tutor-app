package com.chesstutor.app.domain.account

import com.chesstutor.app.core.PieceColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Grading moves once the game is decided (Phase 13B): mates and lost material count even when the winning chances hardly move. */
class DecidedPositionsTest {

    private fun cp(value: Int) = SideScore(value, null)
    private fun mate(moves: Int) = SideScore(null, moves)
    private fun judge(before: SideScore, after: SideScore, playedBest: Boolean = false) = MoveQuality.judge(0.0, playedBest, before, after)

    @Test
    fun allowingAForcedMateIsABlunderUnlessTheGameWasHopeless() {
        assertEquals(MoveQuality.BLUNDER, judge(cp(-300), mate(-5)))
        assertEquals(MoveQuality.BLUNDER, judge(cp(-959), mate(-4)))
        assertEquals(MoveQuality.MISTAKE, judge(cp(-1400), mate(-6)))
        assertEquals("had a mate and now gets mated", MoveQuality.BLUNDER, judge(mate(3), mate(-2)))
    }

    @Test
    fun lettingOnesOwnMateGoDependsOnWhatIsLeft() {
        assertEquals(MoveQuality.INACCURACY, judge(mate(4), cp(1500)))
        assertEquals(MoveQuality.MISTAKE, judge(mate(4), cp(959)))
        assertEquals(MoveQuality.BLUNDER, judge(mate(4), cp(200)))
    }

    @Test
    fun hurryingOnesOwnMateIsAnErrorButHoldingOnIsNot() {
        assertEquals(MoveQuality.MISTAKE, judge(mate(-10), mate(-4)))
        assertEquals(MoveQuality.INACCURACY, judge(mate(-6), mate(-3)))
        assertEquals(MoveQuality.BEST, judge(mate(-6), mate(-6)))
        assertEquals("one move sooner is noise at this depth", MoveQuality.BEST, judge(mate(-6), mate(-5)))
    }

    @Test
    fun givingAwayMaterialInALostGameStillCounts() {
        assertEquals("a queen while losing", MoveQuality.BLUNDER, judge(cp(-800), cp(-1700)))
        assertEquals("a rook while losing", MoveQuality.MISTAKE, judge(cp(-700), cp(-1200)))
        assertEquals("still clearly winning: the chances decide", MoveQuality.BEST, judge(cp(1800), cp(1200)))
    }

    @Test
    fun aBiggerWinLetGoWhileAheadIsAnError() {
        assertEquals("a queen left hanging, a rook up", MoveQuality.MISTAKE, judge(cp(1400), cp(600)))
        assertEquals("a smaller edge kept", MoveQuality.INACCURACY, judge(cp(700), cp(400)))
        assertEquals("a pawn's difference is not an error", MoveQuality.BEST, judge(cp(700), cp(550)))
        assertEquals("crushing either way", MoveQuality.BEST, judge(cp(1800), cp(1200)))
    }

    @Test
    fun theEnginesOwnMoveIsNeverPunished() {
        assertEquals(MoveQuality.BEST, judge(cp(-959), mate(-4), playedBest = true))
        assertEquals(MoveQuality.BEST, judge(mate(-10), mate(-4), playedBest = true))
    }

    @Test
    fun aMateOnTheBoardReadsAsWonForTheSideThatGaveIt() {
        val mated = PlyEval(ply = 9, centipawns = -PlyEval.MATED_SCORE, mate = 0, bestMove = "")
        assertEquals(SideScore(PlyEval.MATED_SCORE, 1), SideScore.of(mated, PieceColor.BLACK))
        assertEquals(SideScore(-PlyEval.MATED_SCORE, -1), SideScore.of(mated, PieceColor.WHITE))
    }

    @Test
    fun oneMoveAtZeroNoLongerHalvesAGamesAccuracy() {
        // White's chances at every position: steady, then one move from 80% to 0%.
        val wins = List(30) { if (it % 2 == 0) 55.0 else 45.0 } + listOf(80.0, 0.0) + List(10) { 0.5 }
        val accuracy = LichessAccuracy.gameAccuracy(wins)[PieceColor.BLACK]!!
        assertTrue("Black's accuracy is $accuracy", accuracy > 40.0)
    }
}
