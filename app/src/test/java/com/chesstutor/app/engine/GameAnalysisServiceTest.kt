package com.chesstutor.app.engine

import com.chesstutor.app.data.model.GameRecord
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAnalysisServiceTest {

    @Test
    fun analyzesPersistedUciMovesAndReportsOnlyUserBlunders() = runTest {
        val engine = object : EngineClient {
            override suspend fun initialize() = Unit
            override suspend fun analyze(request: AnalysisRequest): PositionAnalysis {
                return when (request.requestId) {
                    1 -> PositionAnalysis(1, "e2e4", centipawns = 100, depth = 5)
                    2 -> PositionAnalysis(2, "d2d4", centipawns = -300, depth = 5)
                    3 -> PositionAnalysis(3, "e7e5", centipawns = -100, depth = 5)
                    else -> PositionAnalysis(4, "g1f3", centipawns = 100, depth = 5)
                }
            }
            override suspend fun stop() = Unit
            override suspend fun dispose() = Unit
        }

        val game = GameRecord(
            id = "analysis-1",
            dateMillis = 1L,
            botName = "Wayne",
            botRating = 600,
            result = "1-0",
            pgn = "",
            moveCount = 2,
            userColor = "white",
            finalFen = "",
            uciMoves = "e2e4 e7e5"
        )

        val result = GameAnalysisService(engine).analyze(game)

        assertEquals(2, result.analyzedMoves.size)
        assertEquals(1, result.userMoveCount)
        assertEquals(1, result.userBlunders.size)
        assertEquals(400, result.centipawnLoss)
        assertTrue(result.userBlunders.first().userMove)
    }

    @Test
    fun legacyGameWithoutPersistedMovesProducesEmptyAnalysis() = runTest {
        val game = GameRecord(
            id = "legacy",
            dateMillis = 1L,
            botName = "Wayne",
            botRating = 600,
            result = "1-0",
            pgn = "legacy",
            moveCount = 2,
            userColor = "white",
            finalFen = ""
        )

        val result = GameAnalysisService(object : EngineClient {
            override suspend fun initialize() = Unit
            override suspend fun analyze(request: AnalysisRequest): PositionAnalysis {
                error("Legacy games must not invoke the engine")
            }
            override suspend fun stop() = Unit
            override suspend fun dispose() = Unit
        }).analyze(game)

        assertTrue(result.analyzedMoves.isEmpty())
        assertEquals(0, result.skippedMoves)
    }
}
