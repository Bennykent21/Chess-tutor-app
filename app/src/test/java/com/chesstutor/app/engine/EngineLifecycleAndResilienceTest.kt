package com.chesstutor.app.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineLifecycleAndResilienceTest {

    @Test
    fun engineResultValidatorRejectsMismatchedRequestId() {
        val request = AnalysisRequest(
            requestId = 42,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        )
        val analysis = PositionAnalysis(
            requestId = 99,
            bestMoveUci = "e2e4"
        )
        try {
            EngineResultValidator.validate(request, analysis)
            throw AssertionError("Expected request ID mismatch to fail")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("mismatch") == true)
        }
    }

    @Test
    fun engineResultValidatorRejectsIllegalMove() {
        val request = AnalysisRequest(
            requestId = 1,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        )
        val analysis = PositionAnalysis(
            requestId = 1,
            bestMoveUci = "e2e5" // Illegal move in starting position
        )
        try {
            EngineResultValidator.validate(request, analysis)
            throw AssertionError("Expected illegal move e2e5 to fail validation")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("illegal move") == true)
        }
    }

    @Test
    fun engineResultValidatorAcceptsTerminalPositionZeroMove() {
        // Scholar's mate final position
        val checkmateFen = "r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4"
        val request = AnalysisRequest(
            requestId = 10,
            fen = checkmateFen
        )
        val terminalAnalysis = PositionAnalysis(
            requestId = 10,
            bestMoveUci = "0000",
            mateInMoves = 0
        )
        val validated = EngineResultValidator.validate(request, terminalAnalysis)
        assertEquals("0000", validated.bestMoveUci)
    }

    @Test
    fun localFallbackEngineClientAnswersQuicklyWithLegalMove() = runBlocking {
        val client = LocalFallbackEngineClient()
        client.initialize()

        val request = AnalysisRequest(
            requestId = 1,
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            movetimeMs = 50
        )
        val result = client.analyze(request)
        assertEquals(1, result.requestId)
        assertNotNull(result.bestMoveUci)
        assertTrue(result.bestMoveUci.length in 4..5)
    }

    @Test
    fun stockfishProcessEngineClientDisposesSafelyWithoutThrowing() = runBlocking {
        val client = StockfishProcessEngineClient(
            binaryPath = "/invalid/path/to/stockfish",
            fallbackClient = LocalFallbackEngineClient()
        )
        assertFalse(client.isAlive)
        // Disposing an uninitialized or failed engine should be safe and idempotent
        client.dispose()
        client.dispose()
        assertFalse(client.isAlive)
    }
}
