package com.chesstutor.app.feature.play

import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardSession
import com.chesstutor.app.board.BoardSessionState
import com.chesstutor.app.board.MoveEffects
import com.chesstutor.app.core.GameState
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The board side of a game against a bot: the player may move only their own pieces, only
 * on their turn, and only while [open]. [BotGame] plays the bot's moves with [play].
 */
class PlaySession(
    start: Position,
    private val player: PieceColor,
    private val onPlayerMove: (Move) -> Unit,
) : BoardSession {
    private val history = ArrayDeque<GameState>().apply { add(GameState(start)) }
    private val played = ArrayDeque<Move>()
    private val mutableState = MutableStateFlow(BoardSessionState(start, movable = null))
    override val state: StateFlow<BoardSessionState> = mutableState.asStateFlow()

    /** False while the bot thinks and after the game ends. */
    var open: Boolean = false
        set(value) {
            field = value
            mutableState.value = mutableState.value.copy(movable = movableNow())
        }

    val game: GameState get() = history.last()
    val start: Position get() = history.first().position
    val moves: List<Move> get() = played.toList()

    override fun onMove(move: Move): BoardFeedback {
        if (mutableState.value.movable != player) return MoveEffects.ILLEGAL
        val feedback = play(move)
        onPlayerMove(move)
        return feedback
    }

    /** Plays a legal move for either side and returns its sound. */
    fun play(move: Move): BoardFeedback {
        val before = history.last()
        val next = before.play(move)
        history.addLast(next)
        played.addLast(move)
        publish(previous = before.position, animate = true)
        return MoveEffects.feedbackFor(before.position, move, next.position)
    }

    /** Takes back [plies] moves (fewer if the game is shorter). */
    fun undo(plies: Int) {
        repeat(plies.coerceAtMost(played.size)) {
            history.removeLast()
            played.removeLast()
        }
        publish(previous = history.getOrNull(history.size - 2)?.position, animate = false)
    }

    private fun movableNow(): PieceColor? = if (open && game.position.sideToMove == player && !game.isOver) player else null

    private fun publish(previous: Position?, animate: Boolean) {
        val current = history.last()
        mutableState.value = BoardSessionState(
            position = current.position,
            previous = previous,
            lastMove = played.lastOrNull(),
            version = mutableState.value.version + 1,
            animateLastMove = animate,
            movable = movableNow(),
            start = history.first().position,
            moves = played.toList(),
        )
    }
}
