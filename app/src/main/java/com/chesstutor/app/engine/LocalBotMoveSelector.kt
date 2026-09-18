package com.chesstutor.app.engine

import com.example.chess.core.Move
import com.example.chess.core.Position
import com.example.chess.engine.LocalChessEngine

/**
 * Application-facing bot selector.
 *
 * Keeps the heuristic fallback engine behind the app engine boundary so UI
 * state management does not depend directly on the implementation package.
 */
class LocalBotMoveSelector {
    private val engine = LocalChessEngine()

    suspend fun selectMove(position: Position, rating: Int): Move {
        return engine.selectMoveForElo(position, rating)
    }
}
