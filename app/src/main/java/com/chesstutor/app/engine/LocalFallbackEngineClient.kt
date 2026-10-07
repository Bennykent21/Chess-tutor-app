package com.chesstutor.app.engine

import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Position
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class LocalFallbackEngineClient(
    private val heuristicEngine: HeuristicEngineAdapter = HeuristicEngineAdapter(),
    private val calculationDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default
) : EngineClient {
    private val evaluationCache = ConcurrentHashMap<String, PositionAnalysis>()

    override suspend fun initialize() = Unit

    override suspend fun analyze(request: AnalysisRequest): PositionAnalysis = withContext(calculationDispatcher) {
        val targetDepth = (request.depth ?: 3).coerceIn(2, 4)
        val cacheKey = "${request.fen}|$targetDepth"
        evaluationCache[cacheKey]?.let { cached ->
            return@withContext cached.copy(requestId = request.requestId)
        }

        val pos = Position.tryFromFen(request.fen).getOrElse {
            throw IllegalArgumentException("Invalid FEN supplied to fallback engine")
        }
        val legalMoves = LegalMoveGenerator.generateLegalMoves(pos)

        if (legalMoves.isEmpty()) {
            val inCheck = LegalMoveGenerator.isKingInCheck(pos, pos.sideToMove)
            val terminal = EngineResultValidator.validate(
                request,
                PositionAnalysis(
                    requestId = request.requestId,
                    bestMoveUci = "0000",
                    centipawns = if (inCheck) -10000 else 0,
                    mateInMoves = if (inCheck) -1 else null,
                    principalVariation = emptyList(),
                    depth = targetDepth
                ),
                EngineResultValidator.ScorePerspective.SIDE_TO_MOVE
            )
            evaluationCache[cacheKey] = terminal
            return@withContext terminal
        }

        val (bestMove, eval) = heuristicEngine.findBestMove(pos, depth = targetDepth)
        val normalizedMate = when (eval.mateInMoves) {
            0 -> if ((eval.centipawns ?: 0) >= 0) 1 else -1
            else -> eval.mateInMoves
        }
        val result = EngineResultValidator.validate(
            request,
            PositionAnalysis(
                requestId = request.requestId,
                bestMoveUci = bestMove.uci,
                centipawns = eval.centipawns,
                mateInMoves = normalizedMate,
                principalVariation = listOf(bestMove.uci),
                depth = targetDepth
            ),
            EngineResultValidator.ScorePerspective.WHITE
        )
        if (evaluationCache.size > 512) {
            evaluationCache.clear()
        }
        evaluationCache[cacheKey] = result
        result
    }

    override suspend fun stop() = Unit
    override suspend fun dispose() = Unit
}
