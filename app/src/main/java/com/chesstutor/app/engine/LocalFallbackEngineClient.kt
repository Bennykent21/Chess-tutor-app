package com.chesstutor.app.engine

import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Position
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalFallbackEngineClient(
    private val heuristicEngine: HeuristicEngineAdapter = HeuristicEngineAdapter(),
    private val calculationDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default
) : EngineClient {
    override suspend fun initialize() = Unit

    override suspend fun analyze(request: AnalysisRequest): PositionAnalysis = withContext(calculationDispatcher) {
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

        val (bestMove, eval) = heuristicEngine.findBestMove(pos, depth = (request.depth ?: 3).coerceIn(2, 4))
        EngineResultValidator.validate(
            request,
            PositionAnalysis(
                requestId = request.requestId,
                bestMoveUci = bestMove.uci,
                centipawns = eval.centipawns,
                mateInMoves = eval.mateInMoves,
                principalVariation = listOf(bestMove.uci),
                depth = request.depth ?: 3
            ),
            EngineResultValidator.ScorePerspective.WHITE
        )
    }

    override suspend fun stop() = Unit
    override suspend fun dispose() = Unit
}
