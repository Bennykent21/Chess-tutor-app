package com.chesstutor.app.engine

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChessEngineManagerTest {

    private val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    private val mateInOneFen = "r1bqkb1r/pppp1ppp/2n5/4p3/2B1n3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 4" // Qxf7#
    private val checkmateFen = "r1bqkb1r/pppp1Qpp/2n5/4p3/2B1n3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4"

    @Test
    fun evaluatePositionProducesNormalizedEngineEvaluation() = runTest {
        val fakeEngine = MockEngineClient()
        fakeEngine.nextResponse = PositionAnalysis(
            requestId = 1,
            bestMoveUci = "e2e4",
            centipawns = 40,
            depth = 10,
            principalVariation = listOf("e2e4", "e7e5")
        )

        val manager = ChessEngineManager(fakeEngine)
        val evaluation = manager.evaluatePosition(startFen, depth = 10)

        assertNotNull(evaluation)
        assertEquals(40, evaluation?.centipawns)
        assertEquals("e2e4", evaluation?.bestMoveUci)
        assertEquals(10, evaluation?.depth)
        assertNull(evaluation?.mateInMoves)
    }

    @Test
    fun evaluatePositionDetectsCheckmateImmediately() = runTest {
        val fakeEngine = MockEngineClient()
        val manager = ChessEngineManager(fakeEngine)

        // Side to move is black, and black is mated -> White is winning (+1 mate score for White perspective)
        val evaluation = manager.evaluatePosition(checkmateFen)

        assertNotNull(evaluation)
        assertEquals(1, evaluation?.mateInMoves)
    }

    @Test
    fun calculateBestMoveReturnsMatchingMoveChoice() = runTest {
        val fakeEngine = MockEngineClient()
        fakeEngine.nextResponse = PositionAnalysis(
            requestId = 1,
            bestMoveUci = "f3f7",
            mateInMoves = 1,
            depth = 8
        )

        val manager = ChessEngineManager(fakeEngine)
        val move = manager.calculateBestMove(mateInOneFen)

        assertNotNull(move)
        assertEquals("f3", move?.from)
        assertEquals("f7", move?.to)
        assertEquals("Qxf7#", move?.san)
        assertEquals('q', move?.piece)
    }

    @Test
    fun calculateBotMoveSelectsValidMove() = runTest {
        val fakeEngine = MockEngineClient()
        fakeEngine.nextResponse = PositionAnalysis(
            requestId = 1,
            bestMoveUci = "e2e4",
            centipawns = 30
        )

        val manager = ChessEngineManager(fakeEngine)

        val strong = manager.calculateBotMove(startFen, elo = 2800)
        assertNotNull(strong)
        assertEquals("the strength goes to the engine with the request", 2800, fakeEngine.configuredRating)

        val weak = manager.calculateBotMove(startFen, elo = 800)
        assertNotNull(weak)
    }

    private class MockEngineClient : EngineClient {
        var initialized = false
        var stopCalled = false
        var disposed = false
        var configuredRating: Int? = null
        var nextResponse: PositionAnalysis? = null

        override suspend fun initialize() {
            initialized = true
        }

        override suspend fun analyze(request: AnalysisRequest): PositionAnalysis {
            return nextResponse ?: PositionAnalysis(
                requestId = request.requestId,
                bestMoveUci = "e2e4",
                centipawns = 20,
                depth = request.depth
            )
        }

        override suspend fun stop() {
            stopCalled = true
        }

        override suspend fun dispose() {
            disposed = true
        }

        override suspend fun setStrengthRating(rating: Int) {
            configuredRating = rating
        }
    }
}
