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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GameAnalyzerTest {

    /** Answers every position with a level score and e2e4, except the ones in [silentAt] (by call number). */
    class FakeEngine(var silentAt: Set<Int> = emptySet()) : EngineService {
        var calls = 0
        var depths = mutableListOf<Int>()
        override val status = MutableStateFlow(EngineStatus())
        override suspend fun bestMove(fen: String, movetimeMs: Int): MoveChoice? = null
        override suspend fun evaluate(fen: String, movetimeMs: Int): EngineEvaluation? = null
        override suspend fun analyse(fen: String, lines: Int, movetimeMs: Int): List<EngineLine> =
            if (calls in silentAt) emptyList() else listOf(EngineLine(rank = 1, moves = listOf("e2e4", "e7e5"), centipawns = 15, depth = 14))
        override suspend fun analyseToDepth(fen: String, lines: Int, depth: Int): List<EngineLine> {
            calls++
            depths += depth
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
        val progress = mutableListOf<Pair<Int, Int>>()
        val analyzer = GameAnalyzer(engine, repo, clock = { 42 })
        val summary = analyzer.analyse(game) { done, total -> progress += done to total }

        assertEquals(5, engine.calls)
        assertEquals("every position was searched (none cached)", 5, analyzer.lastSearched)
        assertEquals(List(5) { GameAnalyzer.DEFAULT_DEPTH }, engine.depths)
        assertEquals((1..5).map { it to 5 }, progress)
        assertEquals(5, repo.evals("g").size)
        assertEquals(listOf("e2e4", "e7e5"), repo.evals("g").first().line)
        assertEquals(4, summary.plies)
        assertEquals(42L, summary.analysedAt)
        assertFalse(summary.lite)
        assertTrue(summary.whiteAccuracy!! > 90)

        GameAnalyzer(engine, repo).analyse(game)
        assertEquals("cached positions are not searched again", 5, engine.calls)
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
        assertEquals("only the three missing positions were searched", 6, engine.calls)
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
