package com.chesstutor.app.domain.puzzle

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.GameStatus
import com.chesstutor.app.core.Position
import com.chesstutor.app.feature.puzzles.goalExample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RushRecordsTest {

    @Test
    fun aRushKeepsOnlyItsBestScore() {
        val records = InMemoryRushRecords()
        records.record(RushKind.THREE, 12)
        records.record(RushKind.THREE, 9)
        records.record(RushKind.SURVIVAL, 4)
        assertEquals(mapOf(RushKind.THREE to 12, RushKind.SURVIVAL to 4), records.best.value)
        records.record(RushKind.THREE, 15)
        assertEquals(15, records.best.value[RushKind.THREE])
        records.record(RushKind.FIVE, 0)
        assertEquals("no run solved nothing worth keeping", null, records.best.value[RushKind.FIVE])
    }

    @Test
    fun eachRushKnowsItsClock() {
        assertEquals(RushKind.THREE, RushKind.of(3))
        assertEquals(RushKind.FIVE, RushKind.of(5))
        assertEquals(RushKind.SURVIVAL, RushKind.of(null))
    }

    @Test
    fun theGoalTilesShowRealPositions() {
        PuzzleGoal.entries.forEach { goal ->
            val position = Position.tryFromFen(goalExample(goal)).getOrNull()
            assertTrue("$goal's example reads", position != null && LegalMoveGenerator.generateLegalMoves(position).isNotEmpty())
        }
        val mate = Position.fromFen(goalExample(PuzzleGoal.CHECKMATE))
        val rd8 = LegalMoveGenerator.generateLegalMoves(mate).first { it.uci == "d1d8" }
        assertEquals("the checkmate tile's idea is a real mate", GameStatus.CHECKMATE, LegalMoveGenerator.getGameStatus(LegalMoveGenerator.makeMove(mate, rd8)))
    }
}
