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

    /**
     * Regression: UCI text carries no castling or en passant flag, but the legal-move list
     * does, so comparing whole Move objects rejected every castling and en passant answer
     * (analysis then showed "Thinking" forever whenever castling was best).
     */
    @Test
    fun acceptsCastlingAndEnPassantBestMovesAndLines() {
        val castlingFen = "r3k2r/pppq1ppp/2npbn2/2b1p3/2B1P3/2NPBN2/PPPQ1PPP/R3K2R w KQkq - 4 8"
        val castled = EngineResultValidator.validate(
            AnalysisRequest(10, castlingFen, multiPv = 2),
            PositionAnalysis(
                10, "e1g1", centipawns = 20,
                lines = listOf(EngineLine(1, listOf("e1g1", "e8g8")), EngineLine(2, listOf("e1c1", "e8c8"))),
            ),
            EngineResultValidator.ScorePerspective.SIDE_TO_MOVE,
        )
        assertEquals("e1g1", castled.bestMoveUci)
        assertEquals(listOf("e1g1", "e1c1"), castled.lines.map { it.firstMove })

        val enPassantFen = "rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3"
        val captured = EngineResultValidator.validate(
            AnalysisRequest(11, enPassantFen),
            PositionAnalysis(11, "e5f6", centipawns = 40),
        )
        assertEquals("e5f6", captured.bestMoveUci)
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
