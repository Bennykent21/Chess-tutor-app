package com.chesstutor.app.feature.puzzles

import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardSession
import com.chesstutor.app.board.BoardSessionState
import com.chesstutor.app.board.MoveEffects
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.domain.puzzle.Puzzle
import com.chesstutor.app.domain.puzzle.PuzzleHint
import com.chesstutor.app.domain.puzzle.PuzzleMoveResult
import com.chesstutor.app.domain.puzzle.PuzzleSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the player's last move did, for the player screen to react to. */
sealed interface PuzzleMoveOutcome {
    /** Right; the opponent replies next (call [PuzzleBoardSession.playReply] after a pause). */
    data object Continue : PuzzleMoveOutcome
    data class Solved(val alternative: Boolean) : PuzzleMoveOutcome
    /** Wrong: the board is unchanged and the player may try again (unless the mode allows one try). */
    data class Wrong(val move: Move) : PuzzleMoveOutcome
}

/**
 * One puzzle on the new board (plan section 6.3.3). The tested [PuzzleSession] judges every
 * move; this class keeps a [Position] for the board, plays the opponent's set-up move and
 * replies with a slide, and steps through the solution or the main line when asked.
 */
class PuzzleBoardSession private constructor(
    val puzzle: Puzzle,
    private val judge: PuzzleSession,
) : BoardSession {
    private val start: Position = Position.fromFen(puzzle.fen)
    private var current: Position = start
    private var version = 0
    private var pendingReply: Move? = null
    private var lineToShow: List<Move> = emptyList()
    /** Where the board was before the player's last accepted move (for showing the main line). */
    private var beforePlayerMove: Position = start

    /** The side the player solves for: the board faces it (decision D2). */
    val solverColor: PieceColor = if (judge.solverColor == 'w') PieceColor.WHITE else PieceColor.BLACK

    private val mutableState = MutableStateFlow(stateOf(start, previous = null, last = null, animate = false, movable = null))
    override val state: StateFlow<BoardSessionState> = mutableState.asStateFlow()

    var lastOutcome: PuzzleMoveOutcome? = null
        private set

    /** Told about every judged move (the player screen reacts: reply, flash, finish). */
    var onOutcome: ((PuzzleMoveOutcome) -> Unit)? = null

    /**
     * The position the puzzle starts from (after the opponent's set-up move), captured once.
     * Not the judge's current position: that one moves on as the puzzle is played.
     */
    val solvingFen: String = judge.fen

    val mistakes: Int get() = judge.mistakes
    val hintsUsed: Int get() = judge.hintsUsed
    val isFinished: Boolean get() = judge.outcome() != null
    fun outcome() = judge.outcome()

    /** Plays the opponent's set-up move with a slide; the player may then move. */
    fun playOpening() {
        if (version > 0) return
        val move = legal(start, judge.openingMove) ?: return
        current = LegalMoveGenerator.makeMove(start, move)
        publish(previous = start, last = move, animate = true, movable = solverColor)
    }

    override fun onMove(move: Move): BoardFeedback {
        val before = current
        return when (val result = judge.submit(move.uci)) {
            is PuzzleMoveResult.Correct -> {
                beforePlayerMove = before
                // Use the generated move: one parsed from text lacks the castling and en passant flags.
                current = LegalMoveGenerator.makeMove(before, legal(before, move.uci) ?: move)
                pendingReply = result.reply?.takeIf { !result.solved }?.let { legal(current, it) }
                lastOutcome = if (result.solved) PuzzleMoveOutcome.Solved(result.alternative) else PuzzleMoveOutcome.Continue
                publish(previous = before, last = move, animate = true, movable = null)
                lastOutcome?.let { onOutcome?.invoke(it) }
                if (result.solved) MoveEffects.CORRECT else MoveEffects.feedbackFor(before, move, current)
            }
            is PuzzleMoveResult.Wrong -> {
                lastOutcome = PuzzleMoveOutcome.Wrong(move)
                lastOutcome?.let { onOutcome?.invoke(it) }
                MoveEffects.WRONG
            }
            PuzzleMoveResult.Illegal, PuzzleMoveResult.NotSolving -> MoveEffects.ILLEGAL
        }
    }

    /** Shows the opponent's reply after a correct move, and hands the move back. */
    fun playReply(): Boolean {
        val reply = pendingReply ?: return false
        pendingReply = null
        val before = current
        current = LegalMoveGenerator.makeMove(before, reply)
        publish(previous = before, last = reply, animate = true, movable = solverColor)
        return true
    }

    /** First call: the piece to move. Later calls: the whole move. Null once finished. */
    fun hint(): PuzzleHint? = judge.hint()

    /** Gives up. Call [stepLine] to play the solution out move by move. */
    fun reveal() {
        lineToShow = judge.reveal().mapNotNull { uci -> runCatching { Move.fromUci(uci) }.getOrNull() }
        pendingReply = null
        publish(previous = null, last = null, animate = false, movable = null, keepVersionMove = true)
    }

    /**
     * After an alternative solve: goes back to the position before the player's move, so
     * [stepLine] can show the listed main line.
     */
    fun prepareMainLine() {
        lineToShow = judge.mainLineFromHere().mapNotNull { uci -> runCatching { Move.fromUci(uci) }.getOrNull() }
        current = beforePlayerMove
        publish(previous = null, last = null, animate = false, movable = null)
    }

    /** Plays the next move of the solution or main line. False when the line is done. */
    fun stepLine(): Boolean {
        val next = lineToShow.firstOrNull() ?: return false
        lineToShow = lineToShow.drop(1)
        val move = legal(current, next.uci) ?: return false
        val before = current
        current = LegalMoveGenerator.makeMove(before, move)
        publish(previous = before, last = move, animate = true, movable = null)
        return true
    }

    /** FEN of what the board shows now, for "Analyse" and reports. */
    val currentFen: String get() = current.toFen()

    private fun legal(position: Position, uci: String): Move? =
        LegalMoveGenerator.generateLegalMoves(position).firstOrNull { it.uci == uci }

    private fun publish(previous: Position?, last: Move?, animate: Boolean, movable: PieceColor?, keepVersionMove: Boolean = false) {
        version++
        val shownLast = if (keepVersionMove) mutableState.value.lastMove else last
        mutableState.value = stateOf(current, previous, shownLast, animate, movable).copy(version = version)
    }

    private fun stateOf(position: Position, previous: Position?, last: Move?, animate: Boolean, movable: PieceColor?) =
        BoardSessionState(
            position = position,
            previous = previous,
            lastMove = last,
            animateLastMove = animate && last != null && previous != null,
            movable = movable,
            legalMoves = if (movable == null) emptyList() else LegalMoveGenerator.generateLegalMoves(position),
            start = start,
        )

    companion object {
        /** Null when the puzzle cannot be played (bad FEN or moves), so a corrupt entry is skipped. */
        fun start(puzzle: Puzzle): PuzzleBoardSession? {
            val judge = PuzzleSession.start(puzzle) ?: return null
            if (Position.tryFromFen(puzzle.fen).isFailure) return null
            return PuzzleBoardSession(puzzle, judge)
        }
    }
}
