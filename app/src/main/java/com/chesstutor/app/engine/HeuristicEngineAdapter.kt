package com.chesstutor.app.engine

import com.example.chess.core.Move
import com.example.chess.core.Position
import com.example.chess.engine.LocalChessEngine

/**
 * Application boundary for the legacy pure-Kotlin heuristic engine.
 *
 * The chess core remains the source of truth for rules. This adapter keeps
 * engine implementation details out of application-facing engine clients.
 */
class HeuristicEngineAdapter(
    private val engine: LocalChessEngine = LocalChessEngine()
) {
    suspend fun selectMove(position: Position, rating: Int): Move =
        engine.selectMoveForElo(position, rating)
}
