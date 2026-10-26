package com.chesstutor.app.feature.home

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.bot.InMemoryBotRepository
import com.chesstutor.app.data.repository.InMemoryGameRepository
import com.chesstutor.app.domain.MoveChoice
import com.chesstutor.app.domain.account.ThemeScore
import com.chesstutor.app.domain.account.TrainingReport
import com.chesstutor.app.domain.bot.BotRoster
import com.chesstutor.app.domain.game.NextStep
import com.chesstutor.app.domain.learn.Course
import com.chesstutor.app.domain.learn.Lesson
import com.chesstutor.app.domain.learn.LessonCategory
import com.chesstutor.app.domain.learn.LessonLevel
import com.chesstutor.app.domain.learn.LessonLibrary
import com.chesstutor.app.domain.learn.LessonProgress
import com.chesstutor.app.domain.learn.LessonStep
import com.chesstutor.app.domain.game.GameRules
import com.chesstutor.app.domain.game.SideChoice
import com.chesstutor.app.domain.opening.BattleMode
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
import org.junit.Assert.assertTrue
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
    private val bots = InMemoryBotRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun model() = HomeViewModel(
        FakeEngine(), { OpeningTree.EMPTY }, games, bots, flowOf(PuzzleStats(dayStreak = 3)), store,
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

    @Test
    fun aBuiltBotIsSavedPlayableAtOnceEditableAndDeletable() {
        val vm = model()
        test.backgroundScope.launch(dispatcher) { vm.home.collect {} }
        vm.newBot()
        assertNull("no name, no save", vm.saveDraft())
        vm.updateDraft { it.copy(name = "Captain Fork", rating = 1500, face = "kofi").copy(rules = GameRules(takebacks = false)) }
        val id = vm.saveDraft()!!
        assertEquals("Captain Fork", vm.findBot(id)?.name)
        assertEquals("its saved rules open with its page", false, vm.rulesFor(id).takebacks)
        test.runCurrent()
        assertEquals(listOf("Captain Fork"), vm.home.value.myBots.map { it.profile.name })

        vm.editBot(id)
        vm.updateDraft { it.copy(name = "Major Pin") }
        assertEquals(id, vm.saveDraft())
        test.runCurrent()
        assertEquals(listOf("Major Pin"), vm.home.value.myBots.map { it.profile.name })

        vm.start(vm.findBot(id)!!, SideChoice.WHITE, vm.rulesFor(id))
        test.runCurrent()
        vm.game.value!!.play("e2e4")
        test.advanceTimeBy(5_000)
        test.runCurrent()
        vm.closeGame()
        test.runCurrent()
        assertEquals("a game against my own bot resumes too", "Major Pin", vm.home.value.resume?.bot?.name)

        vm.deleteBot(id)
        test.runCurrent()
        assertTrue(vm.home.value.myBots.isEmpty())
        assertNull("a deleted bot's game is no longer offered", vm.home.value.resume)
    }

    @Test
    fun aLessonPresetsOpeningBattleWithItsMainLineAndSide() {
        val tree = OpeningTree.parse(
            sequenceOf(
                "B20\tSicilian Defense\te2e4 c7c5",
                "B50\tSicilian Defense\te2e4 c7c5 g1f3 d7d6",
                "B90\tSicilian Defense: Najdorf Variation\te2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6",
            ),
        )
        val vm = HomeViewModel(
            FakeEngine(), { tree }, games, bots, flowOf(PuzzleStats()), store,
            random = Random(1), clock = { test.testScheduler.currentTime },
        )
        vm.presetBattle("Sicilian Defense", PieceColor.BLACK)
        assertEquals(SideChoice.BLACK, vm.battle.value.side)
        test.runCurrent()
        val line = vm.battle.value.line!!
        assertEquals("the longest line with exactly that name", listOf("e2e4", "c7c5", "g1f3", "d7d6"), line.moves)
        assertEquals(BattleMode.PLAY, vm.battle.value.mode)

        vm.presetBattle("Sicilian Defense", null)
        assertEquals("a course for both sides lets the side be random", SideChoice.RANDOM, vm.battle.value.side)
    }

    @Test
    fun theContinueCardShowsTheNextLessonAndRecommendedFollowsTraining() {
        val start = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        fun lesson(id: String) = Lesson(id, "Lesson $id", "", listOf(LessonStep.Explain("x", start)))
        val library = LessonLibrary(listOf(Course("italian", LessonCategory.OPENINGS, "Italian Game", "", LessonLevel.BEGINNER, 0, listOf(lesson("o1"), lesson("o2")))))
        val forks = ThemeScore("fork", attempts = 6, clean = 1)
        val report = TrainingReport(emptyList(), 6, 1, emptyList(), listOf(forks), 1, 2)
        val vm = HomeViewModel(
            FakeEngine(), { OpeningTree.EMPTY }, games, bots, flowOf(PuzzleStats()), store,
            random = Random(1), clock = { test.testScheduler.currentTime },
            learning = HomeLearning(
                training = flowOf(report),
                loadLessons = { library },
                lessonProgress = flowOf(mapOf("o1" to LessonProgress("o1", completed = true))),
            ),
        )
        test.backgroundScope.launch(dispatcher) { vm.home.collect {} }
        test.runCurrent()

        val next = vm.home.value.nextLesson!!
        assertEquals("o2", next.lessonId)
        assertEquals("Italian Game", next.courseTitle)
        assertEquals(2, next.number)
        assertEquals(2, next.total)
        assertEquals(NextStep.Theme(forks), vm.home.value.nextStep)

        vm.setDueMistakes(4)
        test.runCurrent()
        assertEquals(NextStep.Mistakes(4), vm.home.value.nextStep)
    }
}
