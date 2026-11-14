package com.chesstutor.app.feature.train

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.domain.train.InMemoryPositionRecords
import com.chesstutor.app.domain.train.PositionGoal
import com.chesstutor.app.domain.train.PositionOutcome
import com.chesstutor.app.domain.train.PositionParser
import com.chesstutor.app.domain.train.PositionRecord
import com.chesstutor.app.engine.EngineEvaluation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class PositionTrainerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sets = PositionParser.parse(File("src/main/assets/positions/positions.json").readText())
    private val day0: Instant = Instant.parse("2026-11-14T09:00:00Z")
    private var now = day0

    /** What the fake engine says, by FEN; anything else is a quiet, level position. */
    private val answers = mutableMapOf<String, EngineEvaluation>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.model(records: InMemoryPositionRecords = InMemoryPositionRecords()): PositionTrainerViewModel {
        val vm = PositionTrainerViewModel(records, { sets }, { fen -> answers[fen] ?: EngineEvaluation(centipawns = 0) }, clock = { now }, zone = ZoneOffset.UTC)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        return vm
    }

    private fun TestScope.play(vm: PositionTrainerViewModel, uci: String) {
        val board = vm.run.value!!.board
        board.onMove(LegalMoveGenerator.generateLegalMoves(board.game.position).single { it.uci == uci })
        advanceUntilIdle()
    }

    /** The position after [uci] from the board's current one. */
    private fun after(vm: PositionTrainerViewModel, uci: String): String {
        val position = vm.run.value!!.board.game.position
        return LegalMoveGenerator.makeMove(position, LegalMoveGenerator.generateLegalMoves(position).single { it.uci == uci }).toFen()
    }

    private fun today() = day0.atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

    @Test
    fun stockfishRepliesAndTheHintShowsThePieceThenTheSquare() = runTest(dispatcher) {
        val vm = model()
        val start = sets.single { it.id == "king-pawn" }.positions.first().fen
        answers[start] = EngineEvaluation(mateInMoves = 11, bestMoveUci = "e6d6")
        vm.start("king-pawn")
        advanceUntilIdle()
        assertEquals(PositionGoal.WIN, vm.run.value!!.position.goal)
        assertFalse(vm.run.value!!.thinking)
        vm.hint()
        assertEquals("e6", vm.run.value!!.hintFrom)
        vm.hint()
        assertEquals("d6", vm.run.value!!.hintTo)

        answers[after(vm, "e6d6")] = EngineEvaluation(mateInMoves = 10, bestMoveUci = "e8d8", principalVariation = listOf("e8d8", "e5e6"))
        play(vm, "e6d6")
        val run = vm.run.value!!
        assertEquals(PositionOutcome.PLAYING, run.outcome)
        assertEquals("Stockfish's reply is on the board", "e8d8", run.board.moves.last().uci)
        assertNull("the hint is gone after a move", run.hintFrom)
        vm.hint()
        assertEquals("the next hint is the line's next move", "e5", vm.run.value!!.hintFrom)
    }

    @Test
    fun aMoveThatDrawsLetsItSlipAndThePositionComesBackTomorrow() = runTest(dispatcher) {
        val records = InMemoryPositionRecords()
        val vm = model(records)
        val set = sets.single { it.id == "king-pawn" }
        answers[set.positions.first().fen] = EngineEvaluation(mateInMoves = 11, bestMoveUci = "e6d6")
        vm.start("king-pawn")
        advanceUntilIdle()
        play(vm, "e6d5")
        val run = vm.run.value!!
        assertEquals(PositionOutcome.SLIPPED, run.outcome)
        assertEquals(PositionEnd.DRAWN, run.end)
        assertEquals("1.Kd5", run.slip)
        assertEquals("Kd6", run.better)
        assertEquals(PositionRecord(dueDay = today() + 1), records.all().value[set.positions.first().id])

        vm.start("king-pawn")
        advanceUntilIdle()
        assertEquals("today it waits", set.positions[1], vm.run.value!!.position)
        now = day0 + Duration.ofDays(1)
        vm.start("king-pawn")
        advanceUntilIdle()
        assertEquals("tomorrow it is first again", set.positions.first(), vm.run.value!!.position)
    }

    @Test
    fun aHoldIsLostByAMoveThatLosesFromBlacksSide() = runTest(dispatcher) {
        val vm = model()
        vm.start("king-pawn-hold")
        advanceUntilIdle()
        answers[after(vm, "e7d7")] = EngineEvaluation(centipawns = 7000, bestMoveUci = "f5f6")
        play(vm, "e7d7")
        val run = vm.run.value!!
        assertEquals(PositionEnd.LOST, run.end)
        assertEquals("1...Kd7", run.slip)
    }

    @Test
    fun aConvertedEdgeIsWonAndCounted() = runTest(dispatcher) {
        val records = InMemoryPositionRecords()
        val vm = model(records)
        val set = sets.single { it.id == "extra-pawn" }
        vm.start("extra-pawn")
        advanceUntilIdle()
        answers[after(vm, "h2h3")] = EngineEvaluation(centipawns = 620, bestMoveUci = "g8h8")
        play(vm, "h2h3")
        assertEquals(PositionEnd.CONVERTED, vm.run.value!!.end)
        assertEquals(PositionRecord(won = true), records.all().value[set.positions.first().id])
        assertEquals(1, vm.state.value.sets.single { it.set.id == "extra-pawn" }.won)
        assertNotNull(vm.run.value!!.next)
        vm.next()
        advanceUntilIdle()
        assertEquals(set.positions[1], vm.run.value!!.position)
    }

    @Test
    fun givingUpNamesNoMoveAndRestartPlaysTheSamePosition() = runTest(dispatcher) {
        val vm = model()
        vm.start("queen-mate")
        advanceUntilIdle()
        vm.giveUp()
        assertEquals(PositionEnd.GAVE_UP, vm.run.value!!.end)
        assertNull(vm.run.value!!.slip)
        vm.again()
        advanceUntilIdle()
        assertEquals(PositionOutcome.PLAYING, vm.run.value!!.outcome)
        assertTrue(vm.run.value!!.board.moves.isEmpty())
    }
}
