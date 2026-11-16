package com.chesstutor.app.domain.account

import com.chesstutor.app.data.account.InMemoryGameAnalysisRepository
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.domain.MoveChoice
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.engine.EngineEvaluation
import com.chesstutor.app.engine.EngineHealthReport
import com.chesstutor.app.engine.EngineLine
import com.chesstutor.app.engine.EngineService
import com.chesstutor.app.engine.EngineSource
import com.chesstutor.app.engine.EngineStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GameAnalyzerTest {

    /**
     * Answers every position with a level score and e2e4 at the depth asked, except the calls in
     * [silentAt] (by call number); from call [liteFrom] on, the Lite engine is answering.
     */
    class FakeEngine(var silentAt: Set<Int> = emptySet(), var liteFrom: Int = Int.MAX_VALUE) : EngineService {
        var calls = 0
        var depths = mutableListOf<Int>()
        var times = mutableListOf<Int>()
        private var lastDepth = 0
        override val status = MutableStateFlow(EngineStatus())
        override suspend fun bestMove(fen: String, movetimeMs: Int): MoveChoice? = null
        override suspend fun evaluate(fen: String, movetimeMs: Int): EngineEvaluation? = null
        override suspend fun analyse(fen: String, lines: Int, movetimeMs: Int): List<EngineLine> =
            if (calls in silentAt) emptyList() else listOf(EngineLine(rank = 1, moves = listOf("e2e4", "e7e5"), centipawns = 15, depth = lastDepth))
        override suspend fun analyseWithin(fen: String, lines: Int, depth: Int, movetimeMs: Int): List<EngineLine> {
            calls++
            depths += depth
            times += movetimeMs
            lastDepth = depth
            if (calls >= liteFrom) status.value = EngineStatus(lastAnswerSource = EngineSource.LITE)
            return analyse(fen, lines, 0)
        }
        override suspend fun botMove(fen: String, elo: Int): MoveChoice? = null
        override suspend fun healthCheck(): EngineHealthReport? = null
    }

    private val game = GameLibrary.summarise(
        GameRecord(
            id = "g", dateMillis = 1, botName = "Pip", botRating = 800, result = "1-0", pgn = "",
            moveCount = 4, userColor = "white", finalFen = "", uciMoves = "e2e4 e7e5 g1f3 b8c6",
        ),
        OpeningTree.EMPTY,
    )

    @Test
    fun analysesEveryPositionOnceAndSavesTheSummary() = runTest {
        val engine = FakeEngine()
        val repo = InMemoryGameAnalysisRepository()
        val progress = mutableListOf<Triple<Int, Int, Boolean>>()
        val analyzer = GameAnalyzer(engine, repo, clock = { 42 })
        val summary = analyzer.analyse(game) { done, total, deeper -> progress += Triple(done, total, deeper) }

        assertEquals("a quick pass, then a deeper one", 10, engine.calls)
        assertEquals("every position was searched twice (none cached)", 10, analyzer.lastSearched)
        assertEquals(List(5) { GameAnalyzer.QUICK_DEPTH } + List(5) { GameAnalyzer.DEFAULT_DEPTH }, engine.depths)
        assertEquals(List(5) { GameAnalyzer.QUICK_MOVETIME_MS } + List(5) { GameAnalyzer.DEFAULT_MOVETIME_MS }, engine.times)
        assertEquals((1..5).map { Triple(it, 5, false) } + (1..5).map { Triple(it, 5, true) }, progress)
        assertEquals(5, repo.evals("g").size)
        assertTrue("the deeper answers replaced the quick ones", repo.evals("g").all { it.depth == GameAnalyzer.DEFAULT_DEPTH })
        assertEquals(listOf("e2e4", "e7e5"), repo.evals("g").first().line)
        assertEquals(4, summary.plies)
        assertEquals(42L, summary.analysedAt)
        assertFalse(summary.lite)
        assertTrue(summary.whiteAccuracy!! > 90)

        GameAnalyzer(engine, repo).analyse(game)
        assertEquals("cached positions are not searched again", 10, engine.calls)
    }

    @Test
    fun aSilentEngineStopsTheAnalysisAndItResumesLater() = runTest {
        // Call 3 (the third position) gets no answer, even from the timed fallback.
        val engine = FakeEngine(silentAt = setOf(3))
        val repo = InMemoryGameAnalysisRepository()
        try {
            GameAnalyzer(engine, repo).analyse(game)
            fail("expected the analysis to stop")
        } catch (e: AnalysisFailedException) {
            assertEquals(2, e.ply)
        }
        assertEquals(2, repo.evals("g").size)

        engine.silentAt = emptySet()
        GameAnalyzer(engine, repo).analyse(game)
        assertEquals(5, repo.evals("g").size)
        assertEquals("the three missing positions, then all five deeper", 11, engine.calls)
    }

    @Test
    fun aSilentEngineInTheDeeperPassKeepsTheQuickAnswer() = runTest {
        val engine = FakeEngine(silentAt = setOf(7))
        val repo = InMemoryGameAnalysisRepository()
        GameAnalyzer(engine, repo).analyse(game)
        assertEquals(listOf(18, 10, 18, 18, 18), repo.evals("g").sortedBy { it.ply }.map { it.depth })
    }

    @Test
    fun aLiteAnswerNeverReplacesStockfishs() = runTest {
        // Stockfish answered the quick pass, then gave up: the deeper pass gets only Lite answers.
        val engine = FakeEngine(liteFrom = 6)
        val repo = InMemoryGameAnalysisRepository()
        val summary = GameAnalyzer(engine, repo).analyse(game)
        assertFalse(summary.lite)
        assertTrue(repo.evals("g").none { it.lite })
        assertTrue(repo.evals("g").all { it.depth == GameAnalyzer.QUICK_DEPTH })
    }

    @Test
    fun aReviewStoppedInTheDeeperPassHasNoSummaryAndResumesThere() = runTest {
        val engine = FakeEngine()
        val repo = InMemoryGameAnalysisRepository()
        try {
            GameAnalyzer(engine, repo).analyse(game) { done, _, deeper -> if (deeper && done == 2) throw IllegalStateException("left the screen") }
            fail("expected the analysis to stop")
        } catch (e: IllegalStateException) {
            assertEquals("left the screen", e.message)
        }
        assertTrue("no summary, so the games list starts it again", repo.observeSummaries().first().isEmpty())
        assertEquals(7, engine.calls)

        GameAnalyzer(engine, repo).analyse(game)
        assertEquals("only the three positions not yet searched deeply", 10, engine.calls)
        assertTrue(repo.observeSummaries().first().containsKey("g"))
    }

    @Test
    fun liteEngineAnswersAreFlagged() = runTest {
        val engine = FakeEngine().apply { status.value = EngineStatus(lastAnswerSource = EngineSource.LITE) }
        val repo = InMemoryGameAnalysisRepository()
        val summary = GameAnalyzer(engine, repo).analyse(game)
        assertTrue(summary.lite)
        assertTrue(repo.evals("g").all { it.lite })
    }
}
