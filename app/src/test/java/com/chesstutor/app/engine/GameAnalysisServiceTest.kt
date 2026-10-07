package com.chesstutor.app.engine

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.domain.ChessPosition
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAnalysisServiceTest {

    @Test
    fun analyzesPersistedUciMovesAndReportsOnlyUserBlunders() = runTest {
        val engine = object : EngineClient {
            override suspend fun initialize() = Unit
            override suspend fun analyze(request: AnalysisRequest): PositionAnalysis {
                return when (request.requestId) {
                    1 -> PositionAnalysis(1, "d2d4", centipawns = 100, depth = 5)
                    2 -> PositionAnalysis(2, "e7e5", centipawns = -300, depth = 5)
                    else -> PositionAnalysis(2, "e7e5", centipawns = -300, depth = 5)
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
            moveCount = 1,
            userColor = "white",
            finalFen = "",
            uciMoves = "e2e4"
        )

        val result = GameAnalysisService(engine).analyze(game)

        assertEquals(1, result.analyzedMoves.size)
        assertEquals(1, result.userMoveCount)
        assertEquals(1, result.userBlunders.size)
        assertEquals(400, result.centipawnLoss)
        assertEquals(GameAnalysisStatus.COMPLETE, result.status)

        val move = result.userBlunders.first()
        assertTrue(move.userMove)
        assertEquals(1, move.moveNumber)
        assertEquals("e4", move.san)
        assertEquals("e4??", move.annotatedSan)
        assertEquals("d4", move.bestMoveSan)
        assertEquals(MoveClassification.BLUNDER, move.classification)
        assertEquals("+1.0", move.formattedEvalBefore)
        assertEquals("-3.0", move.formattedEvalAfter)
        assertTrue(move.consequence.isNotBlank())
    }

    @Test
    fun blackBlunderProducesLossWhenEvaluationIncreasesForWhite() = runTest {
        val engine = object : EngineClient {
            override suspend fun initialize() = Unit
            override suspend fun analyze(request: AnalysisRequest): PositionAnalysis {
                return when (request.requestId) {
                    // Ply 1: White plays e2e4 (normal: +20 -> +25)
                    1 -> PositionAnalysis(1, "e2e4", centipawns = 20, depth = 5)
                    2 -> PositionAnalysis(2, "e7e5", centipawns = 25, depth = 5)
                    // Ply 2: Black (user) plays f7f6 (blunder: +25 -> +265, loss = 240 cp for Black)
                    3 -> PositionAnalysis(3, "e7e5", centipawns = 25, depth = 5)
                    4 -> PositionAnalysis(4, "d2d4", centipawns = 265, depth = 5)
                    else -> PositionAnalysis(request.requestId, "e2e4", centipawns = 0, depth = 5)
                }
            }
            override suspend fun stop() = Unit
            override suspend fun dispose() = Unit
        }

        val game = GameRecord(
            id = "black-blunder",
            dateMillis = 1L,
            botName = "Wayne",
            botRating = 600,
            result = "1-0",
            pgn = "",
            moveCount = 2,
            userColor = "black",
            finalFen = "",
            uciMoves = "e2e4 f7f6"
        )

        val result = GameAnalysisService(engine).analyze(game)

        assertEquals(2, result.analyzedMoves.size)
        assertEquals(1, result.userMoveCount)
        assertEquals(1, result.userBlunders.size)
        assertEquals(240, result.centipawnLoss)

        val blackMove = result.userBlunders.first()
        assertEquals(2, blackMove.ply)
        assertEquals(1, blackMove.moveNumber)
        assertFalse(blackMove.moverIsWhite)
        assertTrue(blackMove.userMove)
        assertEquals("f6", blackMove.san)
        assertEquals("e5", blackMove.bestMoveSan)
        assertEquals(240, blackMove.centipawnLoss)
        assertEquals(MoveClassification.BLUNDER, blackMove.classification)
    }

    @Test
    fun mixedColorMultiPlyGameEvaluatesEachMoveFromMoverPerspective() = runTest {
        // 1. e4 e5 2. Nf3 Nc6
        val engine = object : EngineClient {
            override suspend fun initialize() = Unit
            override suspend fun analyze(request: AnalysisRequest): PositionAnalysis {
                return when (request.requestId) {
                    // Ply 1 (White e2e4): +20 -> +20 (0 loss, NORMAL)
                    1 -> PositionAnalysis(1, "e2e4", centipawns = 20, depth = 5)
                    2 -> PositionAnalysis(2, "e7e5", centipawns = 20, depth = 5)
                    // Ply 2 (Black e7e5): +20 -> +20 (0 loss, NORMAL)
                    3 -> PositionAnalysis(3, "e7e5", centipawns = 20, depth = 5)
                    4 -> PositionAnalysis(4, "g1f3", centipawns = 20, depth = 5)
                    // Ply 3 (White g1f3): +120 -> +10 (110 loss for White -> MISTAKE)
                    5 -> PositionAnalysis(5, "f1c4", centipawns = 120, depth = 5)
                    6 -> PositionAnalysis(6, "b8c6", centipawns = 10, depth = 5)
                    // Ply 4 (Black b8c6): +10 -> +210 (200 loss for Black -> BLUNDER)
                    7 -> PositionAnalysis(7, "g8f6", centipawns = 10, depth = 5)
                    8 -> PositionAnalysis(8, "f1b5", centipawns = 210, depth = 5)
                    else -> PositionAnalysis(request.requestId, "e2e4", centipawns = 0, depth = 5)
                }
            }
            override suspend fun stop() = Unit
            override suspend fun dispose() = Unit
        }

        val game = GameRecord(
            id = "mixed-plies",
            dateMillis = 1L,
            botName = "Wayne",
            botRating = 600,
            result = "1-0",
            pgn = "",
            moveCount = 4,
            userColor = "white",
            finalFen = "",
            uciMoves = "e2e4 e7e5 g1f3 b8c6"
        )

        val result = GameAnalysisService(engine).analyze(game)

        assertEquals(4, result.analyzedMoves.size)
        assertEquals(2, result.userMoveCount)
        assertEquals(0, result.userBlunders.size)
        assertEquals(1, result.userMistakes.size)

        val ply1 = result.analyzedMoves[0]
        assertEquals("e4", ply1.san)
        assertEquals(MoveClassification.NORMAL, ply1.classification)
        assertTrue(ply1.userMove)

        val ply2 = result.analyzedMoves[1]
        assertEquals("e5", ply2.san)
        assertEquals(MoveClassification.NORMAL, ply2.classification)
        assertFalse(ply2.userMove)

        val ply3 = result.analyzedMoves[2]
        assertEquals("Nf3", ply3.san)
        assertEquals("Bc4", ply3.bestMoveSan)
        assertEquals(110, ply3.centipawnLoss)
        assertEquals(MoveClassification.MISTAKE, ply3.classification)
        assertTrue(ply3.userMove)

        val ply4 = result.analyzedMoves[3]
        assertEquals("Nc6", ply4.san)
        assertEquals("Nf6", ply4.bestMoveSan)
        assertEquals(200, ply4.centipawnLoss)
        assertEquals(MoveClassification.BLUNDER, ply4.classification)
        assertFalse(ply4.userMove)
    }

    @Test
    fun invalidUciDoesNotCrashAndMarksSkippedMove() = runTest {
        val engine = object : EngineClient {
            override suspend fun initialize() = Unit
            override suspend fun analyze(request: AnalysisRequest): PositionAnalysis {
                return PositionAnalysis(request.requestId, "e2e4", centipawns = 20, depth = 5)
            }
            override suspend fun stop() = Unit
            override suspend fun dispose() = Unit
        }

        val game = GameRecord(
            id = "invalid-uci",
            dateMillis = 1L,
            botName = "Wayne",
            botRating = 600,
            result = "1-0",
            pgn = "",
            moveCount = 2,
            userColor = "white",
            finalFen = "",
            uciMoves = "e2e4 e7e5 z9z9 g1f3"
        )

        val result = GameAnalysisService(engine).analyze(game)

        assertEquals(2, result.analyzedMoves.size)
        assertEquals(1, result.skippedMoves)
        assertEquals(GameAnalysisStatus.PARTIALLY_COMPLETE, result.status)
    }

    @Test
    fun singleMoveEngineFailureDoesNotDestroyWholeAnalysis() = runTest {
        val engine = object : EngineClient {
            override suspend fun initialize() = Unit
            override suspend fun analyze(request: AnalysisRequest): PositionAnalysis {
                if (request.requestId == 3) {
                    error("Transient engine failure on ply 2")
                }
                return PositionAnalysis(request.requestId, "e2e4", centipawns = 15, depth = 5)
            }
            override suspend fun stop() = Unit
            override suspend fun dispose() = Unit
        }

        val game = GameRecord(
            id = "partial-failure",
            dateMillis = 1L,
            botName = "Wayne",
            botRating = 600,
            result = "1-0",
            pgn = "",
            moveCount = 3,
            userColor = "white",
            finalFen = "",
            uciMoves = "e2e4 e7e5 g1f3"
        )

        val result = GameAnalysisService(engine).analyze(game)

        // Ply 1 and Ply 3 succeed; Ply 2 is skipped without breaking board state for Ply 3
        assertEquals(2, result.analyzedMoves.size)
        assertEquals(1, result.skippedMoves)
        assertEquals(GameAnalysisStatus.PARTIALLY_COMPLETE, result.status)
        assertEquals("e4", result.analyzedMoves[0].san)
        assertEquals("Nf3", result.analyzedMoves[1].san)
        assertEquals(3, result.analyzedMoves[1].ply)
    }

    @Test
    fun legacyGameWithoutPersistedMovesProducesLegacyUnavailableStatus() = runTest {
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
        assertEquals(GameAnalysisStatus.LEGACY_UNAVAILABLE, result.status)
    }

    @Test
    fun emptyGameReturnsValidEmptyCompleteResult() = runTest {
        val game = GameRecord(
            id = "empty-game",
            dateMillis = 1L,
            botName = "Wayne",
            botRating = 600,
            result = "*",
            pgn = "",
            moveCount = 0,
            userColor = "white",
            finalFen = "",
            uciMoves = ""
        )

        val result = GameAnalysisService(object : EngineClient {
            override suspend fun initialize() = Unit
            override suspend fun analyze(request: AnalysisRequest): PositionAnalysis {
                error("Empty games must not invoke the engine")
            }
            override suspend fun stop() = Unit
            override suspend fun dispose() = Unit
        }).analyze(game)

        assertTrue(result.analyzedMoves.isEmpty())
        assertEquals(0, result.skippedMoves)
        assertEquals(GameAnalysisStatus.COMPLETE, result.status)
    }

    @Test
    fun sanConversionCoversNormalCaptureCheckMateCastlingAndPromotion() {
        // 1. Normal, Capture, Check, Castling
        val castlingMoves = ChessPosition.replayUciToSan(
            "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5c6 d7c6 e1g1"
        )
        assertEquals(
            listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Bxc6", "dxc6", "O-O"),
            castlingMoves
        )

        // 2. Scholar's Mate (Checkmate '#') and Check ('+')
        val scholarsMate = ChessPosition.replayUciToSan(
            "e2e4 e7e5 f1c4 b8c6 d1h5 g8f6 h5f7"
        )
        assertEquals(
            listOf("e4", "e5", "Bc4", "Nc6", "Qh5", "Nf6", "Qxf7#"),
            scholarsMate
        )

        val checkPos = ChessPosition("rnbqkbnr/ppp2ppp/3p4/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 3")
        assertEquals("Bb5+", checkPos.toSan("f1b5"))

        // 3. Promotion
        val promoPos = ChessPosition("7k/P7/8/8/8/8/8/4K3 w - - 0 1")
        val promoSan = promoPos.toSan("a7a8q")
        assertNotNull(promoSan)
        assertTrue(promoSan!!.startsWith("a8=Q"))
    }
}

