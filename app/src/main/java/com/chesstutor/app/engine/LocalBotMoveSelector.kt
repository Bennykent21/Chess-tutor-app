package com.chesstutor.app.engine

import com.example.chess.core.Move
import com.example.chess.core.Position

/**
 * Application-facing bot selector.
 *
 * Keeps the heuristic fallback engine behind the app engine boundary so UI
 * state management does not depend directly on the implementation package.
 */
class LocalBotMoveSelector(
    private val heuristicEngine: HeuristicEngineAdapter = HeuristicEngineAdapter()
) {
    suspend fun selectMove(position: Position, rating: Int): Move =
        heuristicEngine.selectMove(position, rating)
}
