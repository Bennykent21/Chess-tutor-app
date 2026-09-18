package com.chesstutor.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EngineResultValidatorTest {

    private val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    private val blackToMoveFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1"

    @Test
    fun acceptsLegalEngineMove() {
        val result = EngineResultValidator.validate(
            AnalysisRequest(1, startFen),
            PositionAnalysis(1, "e2e4", centipawns = 35)
        )
        assertEquals("e2e4", result.bestMoveUci)
        assertEquals(35, result.centipawns)
    }

    @Test
    fun rejectsIllegalEngineMove() {
        assertThrows(IllegalArgumentException::class.java) {
            EngineResultValidator.validate(
                AnalysisRequest(2, startFen),
                PositionAnalysis(2, "e2e5")
            )
        }
    }

    @Test
    fun rejectsMalformedEngineMove() {
        assertThrows(IllegalArgumentException::class.java) {
            EngineResultValidator.validate(
                AnalysisRequest(3, startFen),
                PositionAnalysis(3, "not-a-move")
            )
        }
    }

    @Test
    fun accepts0000OnlyForTerminalPosition() {
        val checkmateFen = "7k/5Q2/7K/8/8/8/8/8 b - - 0 1"
        val result = EngineResultValidator.validate(
            AnalysisRequest(4, checkmateFen),
            PositionAnalysis(4, "0000", centipawns = -10000, mateInMoves = 0)
        )
        assertEquals("0000", result.bestMoveUci)

        assertThrows(IllegalArgumentException::class.java) {
            EngineResultValidator.validate(
                AnalysisRequest(5, startFen),
                PositionAnalysis(5, "0000")
            )
        }
    }

    @Test
    fun normalizesStockfishSideToMoveScoreToWhitePerspective() {
        val result = EngineResultValidator.validate(
            AnalysisRequest(6, blackToMoveFen),
            PositionAnalysis(6, "e7e5", centipawns = 80, mateInMoves = 3),
            EngineResultValidator.ScorePerspective.SIDE_TO_MOVE
        )
        assertEquals(-80, result.centipawns)
        assertEquals(-3, result.mateInMoves)
    }

    @Test
    fun keepsWhitePerspectiveCloudScoreUnchanged() {
        val result = EngineResultValidator.validate(
            AnalysisRequest(7, blackToMoveFen),
            PositionAnalysis(7, "e7e5", centipawns = -80, mateInMoves = -3),
            EngineResultValidator.ScorePerspective.WHITE
        )
        assertEquals(-80, result.centipawns)
        assertEquals(-3, result.mateInMoves)
    }

    @Test
    fun rejectsMismatchedRequestId() {
        assertThrows(IllegalArgumentException::class.java) {
            EngineResultValidator.validate(
                AnalysisRequest(8, startFen),
                PositionAnalysis(99, "e2e4")
            )
        }
    }
}
