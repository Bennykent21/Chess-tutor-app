package com.chesstutor.app.feature.learn

import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardSession
import com.chesstutor.app.board.BoardSessionState
import com.chesstutor.app.board.MoveEffects
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.Position
import com.chesstutor.app.domain.learn.LessonChess
import com.chesstutor.app.domain.learn.LessonMoveResult
import com.chesstutor.app.domain.learn.LessonSession
import com.chesstutor.app.domain.learn.LessonStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One lesson on the board (plan section 4.1): the tested [LessonSession] judges every move;
 * this class shows its position, lets the player move only when a move is expected, and
 * slides demonstration moves and the opponent's replies.
 */
class LessonBoardSession(val judge: LessonSession) : BoardSession {
    private var version = 0
    private var shown: Position = judge.position
    private val mutableState = MutableStateFlow(stateOf(shown, previous = null, last = null))
    override val state: StateFlow<BoardSessionState> = mutableState.asStateFlow()

    /** Told about every judged move (the player reacts: reply, feedback, next). */
    var onResult: ((result: LessonMoveResult, uci: String) -> Unit)? = null

    override fun onMove(move: Move): BoardFeedback {
        val before = shown
        val result = judge.submit(move.uci)
        when (result) {
            is LessonMoveResult.Correct -> {
                val played = LessonChess.legal(before, move.uci) ?: move
                shown = judge.position
                publish(previous = before, last = played)
            }
            is LessonMoveResult.Wrong, LessonMoveResult.NotExpected -> Unit
        }
        onResult?.invoke(result, move.uci)
        return when (result) {
            is LessonMoveResult.Correct -> if (result.stepSolved && result.reply == null) MoveEffects.CORRECT else MoveEffects.feedbackFor(before, move, shown)
            is LessonMoveResult.Wrong -> MoveEffects.WRONG
            LessonMoveResult.NotExpected -> MoveEffects.ILLEGAL
        }
    }

    /** Shows the opponent's pending reply with a slide. */
    fun playReply(): Boolean {
        val before = shown
        val reply = judge.playReply() ?: return false
        shown = judge.position
        publish(previous = before, last = LessonChess.legal(before, reply))
        return true
    }

    /** Jumps to the start of the current step (after [LessonSession.advance] or a restart). */
    fun showStep() {
        shown = Position.fromFen(judge.step.fen)
        publish(previous = null, last = null)
    }

    /** Slides an explain step's demonstration move, if it has one. */
    fun playDemonstration(): Boolean {
        val explain = judge.step as? LessonStep.Explain ?: return false
        val uci = explain.move ?: return false
        val start = Position.fromFen(explain.fen)
        val move = LessonChess.legal(start, uci) ?: return false
        shown = LegalMoveGenerator.makeMove(start, move)
        publish(previous = start, last = move)
        return true
    }

    private fun publish(previous: Position?, last: Move?) {
        version++
        mutableState.value = stateOf(shown, previous, last).copy(version = version)
    }

    private fun stateOf(position: Position, previous: Position?, last: Move?): BoardSessionState {
        val movable = if (judge.expectsMove) position.sideToMove else null
        return BoardSessionState(
            position = position,
            previous = previous,
            lastMove = last,
            animateLastMove = last != null && previous != null,
            movable = movable,
            legalMoves = if (movable == null) emptyList() else LegalMoveGenerator.generateLegalMoves(position),
        )
    }
}
