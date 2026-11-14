package com.chesstutor.app.domain.train

import com.chesstutor.app.core.GameState
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PositionJudgeTest {

    private fun score(cp: Int? = null, mate: Int? = null) = LearnerScore(cp, mate)

    @Test
    fun aWinSlipsToADrawAndIsWonByPromotingWithAWinningScore() {
        val win = PositionGoal.WIN
        assertEquals(PositionOutcome.PLAYING, PositionJudge.scored(win, score(mate = 11), promoted = false, learnerMoves = 3))
        assertEquals(PositionOutcome.SLIPPED, PositionJudge.scored(win, score(cp = 0), promoted = false, learnerMoves = 3))
        assertEquals(PositionOutcome.SLIPPED, PositionJudge.scored(win, score(mate = -4), promoted = false, learnerMoves = 3))
        assertEquals(PositionOutcome.WON, PositionJudge.scored(win, score(cp = 7000), promoted = true, learnerMoves = 9))
        assertEquals("a queen lost at once is not a win", PositionOutcome.SLIPPED, PositionJudge.scored(win, score(cp = 20), promoted = true, learnerMoves = 9))
    }

    @Test
    fun aConvertGrowsTheEdgeOrLosesIt() {
        val convert = PositionGoal.CONVERT
        assertEquals(PositionOutcome.PLAYING, PositionJudge.scored(convert, score(cp = 250), false, 4))
        assertEquals(PositionOutcome.WON, PositionJudge.scored(convert, score(cp = 520), false, 4))
        assertEquals(PositionOutcome.WON, PositionJudge.scored(convert, score(mate = 6), false, 4))
        assertEquals(PositionOutcome.SLIPPED, PositionJudge.scored(convert, score(cp = 40), false, 4))
    }

    @Test
    fun aHoldLastsOrIsLost() {
        val hold = PositionGoal.HOLD
        assertEquals(PositionOutcome.PLAYING, PositionJudge.scored(hold, score(cp = -60), false, 3))
        assertEquals(PositionOutcome.HELD, PositionJudge.scored(hold, score(cp = -60), false, PositionJudge.HOLD_MOVES))
        assertEquals(PositionOutcome.SLIPPED, PositionJudge.scored(hold, score(cp = -7000), false, 3))
        assertEquals(PositionOutcome.SLIPPED, PositionJudge.scored(hold, score(mate = -9), false, 3))
    }

    @Test
    fun theScoreIsTurnedToTheLearnersSide() {
        assertEquals(score(cp = -120), LearnerScore.of(120, null, PieceColor.BLACK))
        assertEquals(score(mate = 5), LearnerScore.of(null, -5, PieceColor.BLACK))
        assertEquals(score(cp = 120), LearnerScore.of(120, null, PieceColor.WHITE))
    }

    @Test
    fun aMateOrADrawOnTheBoardEndsIt() {
        val mated = GameState(Position.fromFen("R5k1/5ppp/8/8/8/8/8/6K1 b - - 0 1"))
        assertEquals(PositionOutcome.WON, PositionJudge.ended(PositionGoal.WIN, mated, PieceColor.WHITE))
        val stalemate = GameState(Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"))
        assertEquals(PositionOutcome.SLIPPED, PositionJudge.ended(PositionGoal.WIN, stalemate, PieceColor.WHITE))
        val bare = GameState(Position.fromFen("8/8/4k3/8/8/4K3/8/8 w - - 0 1"))
        assertEquals(PositionOutcome.HELD, PositionJudge.ended(PositionGoal.HOLD, bare, PieceColor.BLACK))
        assertNull(PositionJudge.ended(PositionGoal.WIN, GameState(Position.initial()), PieceColor.WHITE))
    }
}

/** Every bundled position loads, and the sets read as planned. */
class PositionAssetsTest {
    private val sets = PositionParser.parse(File("src/main/assets/positions/positions.json").readText())

    @Test
    fun theSetsLoadWithUniquePositionsThatCanBePlayed() {
        assertEquals(setOf(PositionKind.ENDGAME, PositionKind.MIDDLEGAME), sets.map { it.kind }.toSet())
        val all = sets.flatMap { it.positions }
        assertEquals("ids are unique", all.size, all.map { it.id }.toSet().size)
        assertEquals("no position twice", all.size, all.map { it.fen }.toSet().size)
        all.forEach { p -> assertTrue("${p.id} has no moves", LegalMoveGenerator.generateLegalMoves(Position.fromFen(p.fen)).isNotEmpty()) }
        assertEquals("middlegames are converted", setOf(PositionGoal.CONVERT), sets.filter { it.kind == PositionKind.MIDDLEGAME }.flatMap { it.positions }.map { it.goal }.toSet())
        val rook = sets.single { it.id == "rook-endings" }.positions
        assertEquals(listOf(PositionGoal.WIN, PositionGoal.HOLD, PositionGoal.HOLD), rook.map { it.goal })
        assertEquals("Hold the draw", rook.last().aim)
    }

    @Test
    fun aSlippedPositionWaitsForTomorrowAndAWonOneIsDone() {
        val set = sets.first()
        val (a, b) = set.positions
        val records = mapOf(a.id to PositionRecord(dueDay = 11), b.id to PositionRecord(won = true))
        assertEquals(set.positions.drop(2), PositionQueue.of(set, records, today = 10))
        assertEquals(listOf(a) + set.positions.drop(2), PositionQueue.of(set, records, today = 11))
        val allWon = set.positions.associate { it.id to PositionRecord(won = true) }
        assertEquals("a finished set plays again", set.positions, PositionQueue.of(set, allWon, today = 11))
    }
}
