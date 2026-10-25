package com.chesstutor.app.feature.account

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.account.InMemoryGameAnalysisRepository
import com.chesstutor.app.data.repository.InMemoryGameRepository
import com.chesstutor.app.data.repository.InMemoryReviewRepository
import com.chesstutor.app.domain.account.GameAnalyzerTest
import com.chesstutor.app.engine.EngineLine
import com.chesstutor.app.engine.EngineService
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.domain.puzzle.PuzzleStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val tree by lazy { File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream) }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun record(id: String, date: Long, moves: String = "e2e4 e7e5 g1f3 b8c6 f1c4") = GameRecord(
        id = id, dateMillis = date, botName = "Bot $id", botRating = 1200, result = "1-0", pgn = "",
        moveCount = 5, userColor = "white", finalFen = "", uciMoves = moves,
    )

    @Test
    fun namesSavedGamesNewestFirstAndFollowsChanges() = runTest(dispatcher) {
        val repo = InMemoryGameRepository()
        repo.saveGame(record("old", date = 1))
        val vm = AccountViewModel(repo, { tree }, flowOf(PuzzleStats(rating = 1234)), worker = dispatcher)
        backgroundScope.launch { vm.games.collect {} }
        backgroundScope.launch { vm.stats.collect {} }
        assertTrue("loading until the openings are read", vm.games.value.loading)
        advanceUntilIdle()

        val first = vm.games.value
        assertFalse(first.loading)
        assertEquals(listOf("old"), first.games.map { it.id })
        assertTrue(first.games.single().openingName!!.startsWith("Italian Game"))
        assertEquals(1234, vm.stats.value.rating)

        repo.saveGame(record("new", date = 2, moves = "d2d4 d7d5 c2c4"))
        advanceUntilIdle()
        val second = vm.games.value
        assertEquals(listOf("new", "old"), second.games.map { it.id })
        assertSame("an unchanged game is not named again", first.game("old"), second.game("old"))

        vm.deleteGame("old")
        advanceUntilIdle()
        assertEquals(listOf("new"), vm.games.value.games.map { it.id })
        assertNull(vm.games.value.game("old"))
    }

    @Test
    fun analysesInTheBackgroundAndPractisesMistakes() = runTest(dispatcher) {
        val repo = InMemoryGameRepository()
        repo.saveGame(record("g", date = 1))
        val analysis = InMemoryGameAnalysisRepository()
        val reviews = InMemoryReviewRepository()
        val engine = GameAnalyzerTest.FakeEngine()
        val vm = AccountViewModel(repo, { tree }, flowOf(PuzzleStats()), analysis, engine, reviews, worker = dispatcher, clock = { 99 })
        backgroundScope.launch { vm.games.collect {} }
        backgroundScope.launch { vm.review.collect {} }
        vm.openReview("g")
        advanceUntilIdle()
        assertNull("nothing analysed yet", vm.review.value.summary)
        assertTrue(vm.review.value.review!!.moves.isEmpty())

        vm.analyse("g")
        assertTrue("queued straight away", vm.jobs.value.getValue("g").waiting)
        advanceUntilIdle()
        assertTrue("finished jobs disappear", vm.jobs.value.isEmpty())
        assertEquals(6, engine.calls)
        val state = vm.review.value
        assertTrue(state.review!!.complete)
        assertEquals(6, state.evals.size)
        assertEquals(99L, state.summary!!.analysedAt)
        assertEquals(state.summary, vm.games.value.analysed["g"])

        vm.analyse("g")
        advanceUntilIdle()
        assertEquals("a finished analysis is not redone", 6, engine.calls)
        vm.analyse("g", again = true)
        advanceUntilIdle()
        assertEquals("analyse again starts over", 12, engine.calls)

        val move = state.review!!.moves[2]
        vm.practise(state.game!!, move)
        advanceUntilIdle()
        val item = reviews.getById("game_g_3")!!
        assertEquals(move.fenBefore, item.fen)
        assertEquals(listOf(move.bestMove), item.acceptedMovesUci)

        vm.deleteGame("g")
        advanceUntilIdle()
        assertTrue(analysis.evals("g").isEmpty())
        assertTrue(vm.games.value.analysed.isEmpty())
    }

    @Test
    fun stoppingKeepsWhatWasDone() = runTest(dispatcher) {
        val repo = InMemoryGameRepository()
        repo.saveGame(record("g", date = 1))
        val analysis = InMemoryGameAnalysisRepository()
        val engine = object : EngineService by GameAnalyzerTest.FakeEngine() {
            override suspend fun analyseToDepth(fen: String, lines: Int, depth: Int): List<EngineLine> {
                delay(1_000)
                return listOf(EngineLine(rank = 1, moves = listOf("e2e4"), centipawns = 0))
            }
        }
        val vm = AccountViewModel(repo, { tree }, flowOf(PuzzleStats()), analysis, engine, null, worker = dispatcher)
        backgroundScope.launch { vm.games.collect {} }
        advanceUntilIdle()
        vm.analyse("g")
        advanceTimeBy(2_500)
        assertEquals(2, vm.jobs.value.getValue("g").done)
        vm.stopAnalysis("g")
        advanceUntilIdle()
        assertTrue(vm.jobs.value.isEmpty())
        assertEquals(2, analysis.evals("g").size)
    }

    @Test
    fun aMissingOpeningDatabaseStillListsTheGames() = runTest(dispatcher) {
        val repo = InMemoryGameRepository()
        repo.saveGame(record("a", date = 1))
        val vm = AccountViewModel(repo, { error("no asset") }, flowOf(PuzzleStats()), worker = dispatcher)
        backgroundScope.launch { vm.games.collect {} }
        advanceUntilIdle()
        assertEquals(listOf("a"), vm.games.value.games.map { it.id })
        assertNull(vm.games.value.games.single().opening)
    }
}
