package com.chesstutor.app.engine

import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Move
import com.example.chess.core.Position
import com.example.chess.core.PieceColor

/**
 * Validates engine output against the exact position that was analyzed.
 *
 * Engine output is untrusted at the application boundary: a malformed UCI
 * move must never reach the UI or mutate a game.
 */
object EngineResultValidator {

    enum class ScorePerspective {
        /** UCI Stockfish scores are relative to the side to move. */
        SIDE_TO_MOVE,

        /** AnalysisService and cloud clients use a normalized White perspective. */
        WHITE
    }

    fun validate(
        request: AnalysisRequest,
        analysis: PositionAnalysis,
        scorePerspective: ScorePerspective = ScorePerspective.WHITE,
    ): PositionAnalysis {
        require(analysis.requestId == request.requestId) {
            "Engine request id mismatch: expected ${request.requestId}, got ${analysis.requestId}"
        }

        val position = Position.tryFromFen(request.fen).getOrElse {
            throw IllegalArgumentException("Engine request contains invalid FEN")
        }

        val legalMoves = LegalMoveGenerator.generateLegalMoves(position)
        val bestMove = analysis.bestMoveUci.trim()

        if (bestMove == "0000") {
            require(legalMoves.isEmpty()) {
                "Engine returned 0000 for a position with ${legalMoves.size} legal moves"
            }
        } else {
            val parsed = try {
                Move.fromUci(bestMove)
            } catch (e: Exception) {
                throw IllegalArgumentException("Engine returned malformed UCI move: $bestMove", e)
            }

            require(parsed in legalMoves) {
                "Engine returned illegal move $bestMove for FEN ${request.fen}"
            }
        }

        return analysis.normalizedScore(position.sideToMove, scorePerspective)
    }

    private fun PositionAnalysis.normalizedScore(
        sideToMove: PieceColor,
        perspective: ScorePerspective,
    ): PositionAnalysis {
        if (perspective == ScorePerspective.WHITE || sideToMove == PieceColor.WHITE) {
            return this
        }

        return copy(
            centipawns = centipawns?.let { -it },
            mateInMoves = mateInMoves?.let { -it },
        )
    }
}
