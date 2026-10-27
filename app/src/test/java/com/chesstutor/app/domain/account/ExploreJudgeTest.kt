package com.chesstutor.app.domain.account

import com.chesstutor.app.core.Position
import com.chesstutor.app.engine.EngineLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreJudgeTest {

    private val start = Position.initial()

    @Test
    fun theEnginesLinesBecomeAVerdictWithTheSecondLine() {
        val eval = ExploreJudge.evalOf(start, listOf(
            EngineLine(rank = 1, moves = listOf("e2e4", "e7e5"), centipawns = 30, depth = 12),
            EngineLine(rank = 2, moves = listOf("d2d4"), centipawns = 25, depth = 12),
        ))!!
        assertEquals("e2e4", eval.bestMove)
        assertEquals(30, eval.centipawns)
        assertEquals(25, eval.secondCentipawns)
        assertNull("no lines, no verdict", ExploreJudge.evalOf(start, emptyList()))
        // A finished game is scored without the engine: fool's mate, White is mated.
        val mated = Position.fromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3")
        assertEquals(0, ExploreJudge.evalOf(mated, emptyList())!!.mate)
    }

    @Test
    fun aMoveIsJudgedLikeAGameReviewMove() {
        val before = PlyEval(0, centipawns = 30, mate = null, bestMove = "e2e4")
        val fine = ExploreJudge.judge(start, "e2e4", before, PlyEval(1, centipawns = 30, mate = null, bestMove = "e7e5"))!!
        assertEquals(MoveQuality.BEST, fine.move.quality)
        assertEquals("e4 is best", fine.comment.headline)

        val bad = ExploreJudge.judge(start, "f2f3", before, PlyEval(1, centipawns = -450, mate = null, bestMove = "e7e5"))!!
        assertEquals(MoveQuality.BLUNDER, bad.move.quality)
        assertEquals("e4", bad.move.bestSan)
        assertTrue(bad.comment.headline, bad.comment.headline.startsWith("f3 is a blunder"))
        assertNull("not a legal move", ExploreJudge.judge(start, "e2e5", before, before))
    }
}
