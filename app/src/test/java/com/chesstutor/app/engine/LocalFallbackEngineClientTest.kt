package com.chesstutor.app.engine

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFallbackEngineClientTest {

    private val startFen =
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    @Test
    fun returnsLegalMoveForNonTerminalPosition() = runTest {
        val result = LocalFallbackEngineClient().analyze(
            AnalysisRequest(requestId = 1, fen = startFen, depth = 4)
        )

        assertNotEquals("0000", result.bestMoveUci)
        assertEquals(4, result.depth)
        assertEquals(listOf(result.bestMoveUci), result.principalVariation)
    }

    @Test
    fun returnsCheckmateAs0000WithMateScore() = runTest {
        val checkmateFen = "7k/5Q2/7K/8/8/8/8/8 b - - 0 1"

        val result = LocalFallbackEngineClient().analyze(
            AnalysisRequest(requestId = 2, fen = checkmateFen)
        )

        assertEquals("0000", result.bestMoveUci)
        assertEquals(-10000, result.centipawns)
        assertEquals(0, result.mateInMoves)
    }

    @Test
    fun returnsStalemateAs0000WithoutMateScore() = runTest {
        val stalemateFen = "7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"

        val result = LocalFallbackEngineClient().analyze(
            AnalysisRequest(requestId = 3, fen = stalemateFen)
        )

        assertEquals("0000", result.bestMoveUci)
        assertEquals(0, result.centipawns)
        assertTrue(result.mateInMoves == null)
    }

    @Test
    fun rejectsInvalidFen() = runTest {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                LocalFallbackEngineClient().analyze(
                    AnalysisRequest(requestId = 4, fen = "not-a-fen")
                )
            }
        }
    }
}
