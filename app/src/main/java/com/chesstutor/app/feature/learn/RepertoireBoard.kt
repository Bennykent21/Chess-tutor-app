package com.chesstutor.app.feature.learn

import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardSession
import com.chesstutor.app.board.BoardSessionState
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.Position
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The board of an opening trainer run (Phase 10): it shows the run's line so far from the
 * start, so the move strip follows it, and hands the player's moves to the run. The run decides
 * what a move means ([submit]); a refused move snaps back. Pure apart from the board state.
 */
class RepertoireBoard(
    /** The moves on the board now, from the start, and whether the player may move. */
    private val line: () -> Pair<List<String>, Boolean>,
    private val submit: (Move) -> BoardFeedback,
) : BoardSession {
    private var version = 0
    private val mutableState = MutableStateFlow(build(animate = false))
    override val state: StateFlow<BoardSessionState> = mutableState.asStateFlow()

    override fun onMove(move: Move): BoardFeedback {
        val feedback = submit(move)
        refresh(animate = true)
        return feedback
    }

    /** Draws the run again: after the opponent's move (sliding) or a new card (jumping). */
    fun refresh(animate: Boolean) {
        version++
        mutableState.value = build(animate)
    }

    private fun build(animate: Boolean): BoardSessionState {
        val (ucis, mayMove) = line()
        val start = Position.initial()
        var position = start
        var previous: Position? = null
        val moves = ArrayList<Move>(ucis.size)
        for (uci in ucis) {
            val move = LegalMoveGenerator.generateLegalMoves(position).firstOrNull { it.uci == uci } ?: break
            previous = position
            position = LegalMoveGenerator.makeMove(position, move)
            moves += move
        }
        return BoardSessionState(
            position = position,
            previous = previous,
            lastMove = moves.lastOrNull(),
            version = version,
            animateLastMove = animate && moves.isNotEmpty(),
            movable = if (mayMove) position.sideToMove else null,
            legalMoves = if (mayMove) LegalMoveGenerator.generateLegalMoves(position) else emptyList(),
            start = start,
            moves = moves,
        )
    }
}
