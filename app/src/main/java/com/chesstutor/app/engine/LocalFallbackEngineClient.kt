package com.chesstutor.app.engine

import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Position
import com.example.chess.engine.LocalChessEngine
import com.example.chess.engine.TrainingLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalFallbackEngineClient : EngineClient {
    private val localEngine = LocalChessEngine()

    override suspend fun initialize() {
        // Ready immediately
    }

    override suspend fun analyze(request: AnalysisRequest): PositionAnalysis = withContext(Dispatchers.Default) {
        val pos = Position.tryFromFen(request.fen).getOrElse { Position.initial() }
        val legalMoves = LegalMoveGenerator.generateLegalMoves(pos)

        if (legalMoves.isEmpty()) {
            return@withContext PositionAnalysis(
                requestId = request.requestId,
                bestMoveUci = "0000",
                centipawns = if (LegalMoveGenerator.isKingInCheck(pos, pos.sideToMove)) -10000 else 0,
                mateInMoves = if (LegalMoveGenerator.isKingInCheck(pos, pos.sideToMove)) 0 else null,
                principalVariation = emptyList(),
                depth = request.depth ?: 3
            )
        }

        val eval = localEngine.evaluatePosition(pos, depth = request.depth ?: 3)
        val bestMove = localEngine.selectMove(pos, TrainingLevel.EXPERT_1800)

        // The fallback engine is strictly for move generation when Stockfish is unavailable.
        // We do NOT export shallow alpha-beta centipawns to avoid unverified coaching claims.
        PositionAnalysis(
            requestId = request.requestId,
            bestMoveUci = bestMove.uci,
            centipawns = null,
            mateInMoves = null,
            principalVariation = listOf(bestMove.uci),
            depth = request.depth ?: 3
        )
    }

    override suspend fun stop() {
    }

    override suspend fun dispose() {
    }
}
