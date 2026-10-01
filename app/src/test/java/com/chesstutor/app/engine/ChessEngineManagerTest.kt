package com.chesstutor.app.engine

import com.chesstutor.app.domain.ChessPosition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessEngineManagerTest {

    private val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    private val mateInOneFen = "r1bqkb1r/pppp1ppp/2n5/4p3/2B1n3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 4" // Qxf7#
    private val checkmateFen = "r1bqkb1r/pppp1Qpp/2n5/4p3/2B1n3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4"

    @Test
    fun engineEvaluationFormatsAndCalculatesWinningPercentageCorrectly() {
        val positiveEval = EngineEvaluation(centipawns = 150, mateInMoves = null)
        assertEquals("+1.5", positiveEval.formattedScore)
        assertTrue(positiveEval.winningPercentageWhite > 0.5f)
        assertEquals(150, positiveEval.centipawnsForPlayer(true))
        assertEquals(-150, positiveEval.centipawnsForPlayer(false))

        val negativeEval = EngineEvaluation(centipawns = -80, mateInMoves = null)
        assertEquals("-0.8", negativeEval.formattedScore)
        assertTrue(negativeEval.winningPercentageWhite < 0.5f)

        val evenEval = EngineEvaluation(centipawns = 0, mateInMoves = null)
        assertEquals("0.0", evenEval.formattedScore)
        assertEquals(0.5f, evenEval.winningPercentageWhite, 0.01f)

        val mateWhiteEval = EngineEvaluation(mateInMoves = 2)
        assertEquals("M2", mateWhiteEval.formattedScore)
        assertEquals(0.98f, mateWhiteEval.winningPercentageWhite, 0.001f)
        assertTrue(mateWhiteEval.isForcedMate)

        val mateBlackEval = EngineEvaluation(mateInMoves = -1)
        assertEquals("-M1", mateBlackEval.formattedScore)
        assertEquals(0.02f, mateBlackEval.winningPercentageWhite, 0.001f)
        assertTrue(mateBlackEval.isForcedMate)
    }

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
        assertEquals("e4", evaluation?.bestMoveSan)
        assertEquals(10, evaluation?.depth)
        assertEquals("+0.4", evaluation?.formattedScore)
        assertFalse(evaluation?.isForcedMate == true)
        assertEquals(evaluation, manager.currentEvaluation.value)
    }

    @Test
    fun evaluatePositionDetectsCheckmateImmediately() = runTest {
        val fakeEngine = MockEngineClient()
        val manager = ChessEngineManager(fakeEngine)

        // Side to move is black, and black is mated -> White is winning (+1 mate score for White perspective)
        val evaluation = manager.evaluatePosition(checkmateFen)

        assertNotNull(evaluation)
        assertEquals(1, evaluation?.mateInMoves)
        assertEquals("M1", evaluation?.formattedScore)
        assertEquals(0.98f, evaluation?.winningPercentageWhite ?: 0f, 0.01f)
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

        // Grandmaster mode
        val gmMove = manager.calculateBotMove(startFen, elo = 2800, botDifficulty = "Grandmaster")
        assertNotNull(gmMove)

        // Casual / Novice mode
        val casualMove = manager.calculateBotMove(startFen, elo = 800, botDifficulty = "Casual")
        assertNotNull(casualMove)
    }

    @Test
    fun setSkillLevelConfiguresEngineRating() = runTest {
        val fakeEngine = MockEngineClient()
        val manager = ChessEngineManager(fakeEngine)

        manager.setSkillLevel(1500)
        assertEquals(1500, fakeEngine.configuredRating)
        assertEquals(1500, manager.state.value.activeElo)
    }

    @Test
    fun cancelCalculationStopsEngine() = runTest {
        val fakeEngine = MockEngineClient()
        val manager = ChessEngineManager(fakeEngine)

        manager.cancelCalculation()
        assertTrue(fakeEngine.stopCalled)
    }

    @Test
    fun runDiagnosticsReturnsHealthyStatus() = runTest {
        val fakeEngine = MockEngineClient()
        fakeEngine.nextResponse = PositionAnalysis(
            requestId = 1,
            bestMoveUci = "e2e4",
            centipawns = 25,
            depth = 12,
            principalVariation = listOf("e2e4")
        )

        val manager = ChessEngineManager(fakeEngine)
        val diag = manager.runDiagnostics()

        assertTrue(diag.isAlive)
        assertEquals("e2e4", diag.bestMove)
        assertEquals(25, diag.centipawns)
        assertEquals(12, diag.depth)
        assertNull(diag.launchError)
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
