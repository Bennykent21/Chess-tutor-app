package com.chesstutor.app.board

import com.chesstutor.app.core.GameState
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything the board needs to draw one moment of an activity. */
data class BoardSessionState(
    val position: Position,
    /** The position before [lastMove], so the board can animate it. */
    val previous: Position? = null,
    val lastMove: Move? = null,
    /** Counts moves and takebacks, so the same move played twice still animates. */
    val version: Int = 0,
    /** True right after a move is played; takebacks and resets jump without sliding. */
    val animateLastMove: Boolean = false,
    /** The colour the player may move now; null when the board is read-only. */
    val movable: PieceColor? = position.sideToMove,
    val legalMoves: List<Move> = LegalMoveGenerator.generateLegalMoves(position),
)

/**
 * The activity's brain (plan section 4.1): it owns the position and decides what a move
 * means. Each feature implements it (games, lessons, puzzles, review, analysis).
 */
interface BoardSession {
    val state: StateFlow<BoardSessionState>

    /** Called with a legal move the player made. Returns the feedback to play. */
    fun onMove(move: Move): BoardFeedback
}

/** Both sides move, with takebacks. Used by the Board lab and as the base for analysis. */
class FreePlaySession(start: Position = Position.initial()) : BoardSession {
    private val history = ArrayDeque<GameState>().apply { add(GameState(start)) }
    private val moves = ArrayDeque<Move>()
    private val mutableState = MutableStateFlow(BoardSessionState(start))
    override val state: StateFlow<BoardSessionState> = mutableState.asStateFlow()

    val canUndo: Boolean get() = moves.isNotEmpty()

    override fun onMove(move: Move): BoardFeedback {
        val current = history.last()
        val next = current.play(move)
        history.addLast(next)
        moves.addLast(move)
        mutableState.value = BoardSessionState(
            position = next.position,
            previous = current.position,
            lastMove = move,
            version = mutableState.value.version + 1,
            animateLastMove = true,
            movable = if (next.isOver) null else next.position.sideToMove,
        )
        return MoveEffects.feedbackFor(current.position, move, next.position)
    }

    fun undo() {
        if (moves.isEmpty()) return
        history.removeLast()
        moves.removeLast()
        val current = history.last()
        mutableState.value = BoardSessionState(
            position = current.position,
            previous = if (history.size >= 2) history[history.size - 2].position else null,
            lastMove = moves.lastOrNull(),
            version = mutableState.value.version + 1,
        )
    }

    fun reset(position: Position = Position.initial()) {
        history.clear()
        history.addLast(GameState(position))
        moves.clear()
        mutableState.value = BoardSessionState(position, version = mutableState.value.version + 1)
    }
}
