package com.chesstutor.app.feature.learn

import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardSession
import com.chesstutor.app.board.BoardSessionState
import com.chesstutor.app.board.MoveEffects
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.Position
import com.chesstutor.app.domain.opening.TrainerLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the player's move did in a drill. */
enum class DrillMoveResult { RIGHT, WRONG, DONE }

/**
 * One opening line on the board (plan 6C): the player makes their side's moves from memory;
 * the other side's book moves are played by [playReply] (the screen calls it after a short
 * pause). A wrong move is not played: the board stays, the slip is counted and the right move
 * can be shown. Pure apart from the board state, so it is unit tested.
 */
class OpeningDrillSession(val line: TrainerLine) : BoardSession {
    private val start = Position.initial()
    private var current = start
    private var ply = 0
    private var version = 0
    private val played = mutableListOf<Move>()

    /** Wrong moves and shown answers in this run. */
    var slips: Int = 0
        private set

    /** The right move after a slip or a "Show me", until it is played. */
    var shownAnswer: Move? = null
        private set

    var lastResult: DrillMoveResult? = null
        private set

    private val mutableState = MutableStateFlow(stateOf(previous = null, last = null))
    override val state: StateFlow<BoardSessionState> = mutableState.asStateFlow()

    val isFinished: Boolean get() = ply >= line.moves.size
    val isClean: Boolean get() = slips == 0

    /** True when the next move is the other side's book move. */
    val replyDue: Boolean get() = !isFinished && !line.isPlayerPly(ply)

    /** The player's moves made so far, and in total. */
    val progress: Pair<Int, Int> get() = (0 until ply).count { line.isPlayerPly(it) } to line.playerMoves

    override fun onMove(move: Move): BoardFeedback {
        if (isFinished || replyDue) return MoveEffects.ILLEGAL
        val expected = legal(line.moves[ply]) ?: return MoveEffects.ILLEGAL
        if (move.uci != expected.uci) {
            slips++
            shownAnswer = expected
            lastResult = DrillMoveResult.WRONG
            // Same position, new version: the board snaps the piece back.
            publish(previous = mutableState.value.previous, last = mutableState.value.lastMove, animate = false)
            return MoveEffects.WRONG
        }
        val before = current
        advance(expected)
        lastResult = if (isFinished) DrillMoveResult.DONE else DrillMoveResult.RIGHT
        publish(previous = before, last = expected, animate = true)
        return if (isFinished) MoveEffects.CORRECT else MoveEffects.feedbackFor(before, expected, current)
    }

    /** Plays the other side's book move. False when it is not their turn. */
    fun playReply(): Boolean {
        if (!replyDue) return false
        val move = legal(line.moves[ply]) ?: return false
        val before = current
        advance(move)
        if (isFinished) lastResult = DrillMoveResult.DONE
        publish(previous = before, last = move, animate = true)
        return true
    }

    /** "Show me": the right move appears as an arrow, and the run no longer counts as clean. */
    fun showAnswer(): Move? {
        if (isFinished || replyDue) return null
        val move = legal(line.moves[ply]) ?: return null
        if (shownAnswer == null) slips++
        shownAnswer = move
        publish(previous = mutableState.value.previous, last = mutableState.value.lastMove, animate = false)
        return move
    }

    private fun advance(move: Move) {
        current = LegalMoveGenerator.makeMove(current, move)
        played += move
        ply++
        shownAnswer = null
    }

    private fun legal(uci: String): Move? = LegalMoveGenerator.generateLegalMoves(current).firstOrNull { it.uci == uci }

    private fun publish(previous: Position?, last: Move?, animate: Boolean) {
        version++
        mutableState.value = stateOf(previous, last, animate).copy(version = version)
    }

    private fun stateOf(previous: Position?, last: Move?, animate: Boolean = false): BoardSessionState {
        val playerTurn = !isFinished && line.isPlayerPly(ply)
        return BoardSessionState(
            position = current,
            previous = previous,
            lastMove = last,
            animateLastMove = animate && last != null && previous != null,
            movable = if (playerTurn) current.sideToMove else null,
            legalMoves = if (playerTurn) LegalMoveGenerator.generateLegalMoves(current) else emptyList(),
            start = start,
            moves = played.toList(),
        )
    }
}
