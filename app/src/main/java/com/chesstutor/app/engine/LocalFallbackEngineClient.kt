package com.chesstutor.app.engine

import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Position
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalFallbackEngineClient(
    private val heuristicEngine: HeuristicEngineAdapter = HeuristicEngineAdapter()
) : EngineClient {
    override suspend fun initialize() = Unit

    override suspend fun analyze(request: AnalysisRequest): PositionAnalysis = withContext(Dispatchers.Default) {
        val pos = Position.tryFromFen(request.fen).getOrElse {
            throw IllegalArgumentException("Invalid FEN supplied to fallback engine")
        }
        val legalMoves = LegalMoveGenerator.generateLegalMoves(pos)

        if (legalMoves.isEmpty()) {
            return@withContext EngineResultValidator.validate(
                request,
                PositionAnalysis(
                    requestId = request.requestId,
                    bestMoveUci = "0000",
                    centipawns = if (LegalMoveGenerator.isKingInCheck(pos, pos.sideToMove)) -10000 else 0,
                    mateInMoves = if (LegalMoveGenerator.isKingInCheck(pos, pos.sideToMove)) 0 else null,
                    principalVariation = emptyList(),
                    depth = request.depth ?: 3
                ),
                EngineResultValidator.ScorePerspective.SIDE_TO_MOVE
            )
        }

        val bestMove = heuristicEngine.selectMove(pos, rating = 1800)
        EngineResultValidator.validate(
            request,
            PositionAnalysis(
                requestId = request.requestId,
                bestMoveUci = bestMove.uci,
                centipawns = null,
                mateInMoves = null,
                principalVariation = listOf(bestMove.uci),
                depth = request.depth ?: 3
            )
        )
    }

    override suspend fun stop() = Unit
    override suspend fun dispose() = Unit
}
