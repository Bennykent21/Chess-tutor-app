package com.chesstutor.app.feature.home

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.repository.InMemoryGameRepository
import com.chesstutor.app.domain.MoveChoice
import com.chesstutor.app.domain.bot.BotRoster
import com.chesstutor.app.domain.game.GameRules
import com.chesstutor.app.domain.game.SideChoice
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.domain.puzzle.PuzzleStats
import com.chesstutor.app.engine.EngineEvaluation
import com.chesstutor.app.engine.EngineHealthReport
import com.chesstutor.app.engine.EngineLine
import com.chesstutor.app.engine.EngineService
import com.chesstutor.app.engine.EngineStatus
import com.chesstutor.app.feature.play.BotGame
import com.chesstutor.app.feature.play.GamePhase
import com.chesstutor.app.feature.play.InMemoryActiveGameStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private class FakeEngine : EngineService {
        override val status: StateFlow<EngineStatus> = MutableStateFlow(EngineStatus())
        override suspend fun bestMove(fen: String, movetimeMs: Int): MoveChoice? = null
        override suspend fun evaluate(fen: String, movetimeMs: Int): EngineEvaluation? = null
        override suspend fun analyse(fen: String, lines: Int, movetimeMs: Int): List<EngineLine> {
            val first = LegalMoveGenerator.generateLegalMoves(Position.fromFen(fen)).firstOrNull() ?: return emptyList()
            return listOf(EngineLine(1, listOf(first.uci), 0))
        }
        override suspend fun botMove(fen: String, elo: Int): MoveChoice? = null
        override suspend fun healthCheck(): EngineHealthReport? = null
    }

    private val dispatcher = StandardTestDispatcher()
    private val test = TestScope(dispatcher)
    private val store = InMemoryActiveGameStore()
    private val games = InMemoryGameRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun model() = HomeViewModel(
        FakeEngine(), { OpeningTree.EMPTY }, games, flowOf(PuzzleStats(dayStreak = 3)), store,
        random = Random(1), clock = { test.testScheduler.currentTime },
    )

    private fun BotGame.play(uci: String) {
        session.onMove(LegalMoveGenerator.generateLegalMoves(session.game.position).first { it.uci == uci })
        test.runCurrent()
    }

    @Test
    fun anUnfinishedGameIsSavedAndCanBeResumed() {
        val vm = model()
        test.backgroundScope.launch(dispatcher) { vm.home.collect {} }
        vm.start(BotRoster.byId("juno")!!, SideChoice.WHITE, GameRules())
        test.runCurrent()
        val game = vm.game.value!!
        game.play("e2e4")
        test.advanceTimeBy(5_000)
        test.runCurrent()
        assertEquals(2, game.session.moves.size)
        assertEquals(listOf("e2e4", game.session.moves[1].uci), store.load()?.moves)

        vm.closeGame()
        test.runCurrent()
        assertNull(vm.game.value)
        val card = vm.home.value.resume!!
        assertEquals("juno", card.bot.id)
        assertEquals(2, card.moveCount)
        assertEquals(3, vm.home.value.dayStreak)

        vm.resume()
        test.runCurrent()
        val resumed = vm.game.value!!
        assertEquals(2, resumed.session.moves.size)
        assertEquals(GamePhase.PLAYER_TURN, resumed.state.value.phase)
    }

    @Test
    fun aFinishedGameClearsTheSaveAndRematchSwapsColours() {
        val vm = model()
        vm.start(BotRoster.byId("juno")!!, SideChoice.WHITE, GameRules())
        test.runCurrent()
        val game = vm.game.value!!
        game.play("e2e4")
        test.advanceTimeBy(5_000)
        test.runCurrent()
        assertNotNull(store.load())
        game.resign()
        test.runCurrent()
        assertNull("a finished game is not offered for resuming", store.load())

        vm.rematch()
        test.runCurrent()
        assertEquals(PieceColor.BLACK, vm.game.value!!.setup.playerColor)
        vm.closeGame()
    }

    @Test
    fun quickMatchPlaysTheRecommendedBot() {
        val vm = model()
        test.backgroundScope.launch(dispatcher) { vm.home.collect {} }
        vm.setPlayerRating(800)
        test.runCurrent()
        assertEquals("Wayne", vm.home.value.recommendation?.bot?.name)
        vm.quickMatch()
        test.runCurrent()
        assertEquals("wayne", vm.game.value!!.setup.bot.id)
        vm.closeGame()
    }
}
