package com.chesstutor.app.feature.puzzles

import com.chesstutor.app.board.BoardArrow
import com.chesstutor.app.board.MarkKind
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Square
import com.chesstutor.app.domain.puzzle.AttemptRecord
import com.chesstutor.app.domain.puzzle.LevelRun
import com.chesstutor.app.domain.puzzle.LevelRunResult
import com.chesstutor.app.domain.puzzle.Puzzle
import com.chesstutor.app.domain.puzzle.PuzzleGoal
import com.chesstutor.app.domain.puzzle.PuzzleHint
import com.chesstutor.app.domain.puzzle.PuzzleMode
import com.chesstutor.app.domain.puzzle.PuzzleRepository
import com.chesstutor.app.domain.puzzle.PuzzleThemes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One run of puzzles: a level, a Rush, the daily puzzle and so on (plan section 6.3.2).
 * [nextPuzzle] gives puzzle number [index] (0-based), or null when the run is over.
 */
data class PuzzleRunSpec(
    val mode: PuzzleMode,
    val title: String,
    val level: Int? = null,
    val plannedCount: Int? = null,
    /** A countdown for the whole run (Rush). */
    val timeLimitMs: Long? = null,
    /** The run ends after this many failed puzzles (Survival). */
    val maxStrikes: Int? = null,
    /** A wrong move fails the puzzle and the run moves on (Rush). */
    val oneTry: Boolean = false,
    val nextPuzzle: (index: Int, solvedSoFar: Int) -> Puzzle?,
)

enum class PlayerPhase { LOADING, SETUP, SOLVING, REPLYING, SOLVED, REVEALED, FINISHED, UNAVAILABLE }

/** A dot in the run's progress row. */
data class ResultMark(val solved: Boolean, val clean: Boolean)

data class PuzzlePlayerState(
    val title: String = "",
    val mode: PuzzleMode = PuzzleMode.LEVEL,
    /** The level being played, for level runs. */
    val level: Int? = null,
    val phase: PlayerPhase = PlayerPhase.LOADING,
    val board: PuzzleBoardSession? = null,
    /** 0-based number of the current puzzle in the run. */
    val index: Int = 0,
    val plannedCount: Int? = null,
    val prompt: String = "",
    val solverColor: PieceColor = PieceColor.WHITE,
    val elapsedMs: Long = 0,
    val remainingMs: Long? = null,
    val strikes: Int = 0,
    val maxStrikes: Int? = null,
    val hintSquare: Square? = null,
    val hintArrow: BoardArrow? = null,
    /** The square of a wrong move, flashed briefly. */
    val mistakeSquare: Square? = null,
    val solvedWithAlternative: Boolean = false,
    val showingLine: Boolean = false,
    /** Plain-language pattern names, shown only after the puzzle (plan section 6.3.6). */
    val revealedThemes: List<String> = emptyList(),
    val rating: Int? = null,
    val ratingDelta: Int? = null,
    val xpEarned: Int? = null,
    val results: List<ResultMark> = emptyList(),
    val lastWrongMove: Move? = null,
    val reported: Boolean = false,
    val levelResult: LevelRunResult? = null,
    val levelPassed: Boolean? = null,
) {
    val solvedCount: Int get() = results.count { it.solved }
    val solvedWithoutReveal: Int get() = results.count { it.solved }
    val isLastOfRun: Boolean get() = plannedCount != null && index + 1 >= plannedCount
}

/** Pauses that make puzzles feel natural (shortened in tests). */
data class PlayerTiming(
    val setupMs: Long = 600,
    val replyMs: Long = 550,
    val wrongFlashMs: Long = 900,
    val lineStepMs: Long = 800,
    val rushAdvanceMs: Long = 450,
    val tickMs: Long = 100,
)

/**
 * Runs a [PuzzleRunSpec] on the new board: the set-up move, replies, hints, the solution,
 * the timer, recording every attempt (rating, XP) and, for a level, whether it was passed.
 */
class PuzzlePlayer(
    private val spec: PuzzleRunSpec,
    private val repository: PuzzleRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timing: PlayerTiming = PlayerTiming(),
) {
    private val mutableState = MutableStateFlow(
        PuzzlePlayerState(title = spec.title, mode = spec.mode, level = spec.level, plannedCount = spec.plannedCount, remainingMs = spec.timeLimitMs, maxStrikes = spec.maxStrikes)
    )
    val state: StateFlow<PuzzlePlayerState> = mutableState.asStateFlow()

    private var pacing: Job? = null
    private var ticker: Job? = null
    private var runClock: Job? = null
    private var puzzleStartedAt = 0L
    private var recorded = false

    fun start() {
        if (spec.timeLimitMs != null) startRunClock(spec.timeLimitMs)
        load(index = 0)
    }

    private fun load(index: Int) {
        pacing?.cancel()
        var skipped = 0
        var puzzle: Puzzle?
        var board: PuzzleBoardSession? = null
        while (true) {
            puzzle = spec.nextPuzzle(index + skipped, mutableState.value.solvedCount)
            if (puzzle == null) break
            board = PuzzleBoardSession.start(puzzle)
            if (board != null || ++skipped > 20) break
        }
        if (puzzle == null || board == null) {
            if (index == 0) mutableState.update { it.copy(phase = PlayerPhase.UNAVAILABLE) } else finishRun()
            return
        }
        board.onOutcome = ::onOutcome
        recorded = false
        mutableState.update {
            it.copy(
                phase = PlayerPhase.SETUP,
                board = board,
                index = index,
                prompt = PuzzleGoal.prompt(board.puzzle),
                solverColor = board.solverColor,
                elapsedMs = 0,
                hintSquare = null,
                hintArrow = null,
                mistakeSquare = null,
                solvedWithAlternative = false,
                showingLine = false,
                revealedThemes = emptyList(),
                ratingDelta = null,
                xpEarned = null,
                lastWrongMove = null,
                reported = false,
            )
        }
        pacing = scope.launch {
            delay(timing.setupMs)
            board.playOpening()
            puzzleStartedAt = clock()
            mutableState.update { it.copy(phase = PlayerPhase.SOLVING) }
            startTicker()
        }
    }

    private fun onOutcome(outcome: PuzzleMoveOutcome) {
        val board = mutableState.value.board ?: return
        when (outcome) {
            PuzzleMoveOutcome.Continue -> {
                mutableState.update { it.copy(phase = PlayerPhase.REPLYING, hintSquare = null, hintArrow = null) }
                pacing = scope.launch {
                    delay(timing.replyMs)
                    board.playReply()
                    mutableState.update { it.copy(phase = PlayerPhase.SOLVING) }
                }
            }
            is PuzzleMoveOutcome.Solved -> {
                mutableState.update { it.copy(phase = PlayerPhase.SOLVED, solvedWithAlternative = outcome.alternative, hintSquare = null, hintArrow = null) }
                finishPuzzle(board)
            }
            is PuzzleMoveOutcome.Wrong -> {
                mutableState.update { it.copy(mistakeSquare = outcome.move.to, lastWrongMove = outcome.move) }
                if (spec.oneTry) {
                    giveUp(autoAdvance = true)
                    return
                }
                pacing = scope.launch {
                    delay(timing.wrongFlashMs)
                    mutableState.update { it.copy(mistakeSquare = null) }
                }
            }
        }
    }

    fun hint() {
        val board = mutableState.value.board ?: return
        if (mutableState.value.phase != PlayerPhase.SOLVING) return
        when (val hint = board.hint()) {
            is PuzzleHint.Piece -> mutableState.update { it.copy(hintSquare = Square.fromAlgebraic(hint.square)) }
            is PuzzleHint.Move -> {
                val move = Move.fromUci(hint.uci)
                mutableState.update { it.copy(hintSquare = null, hintArrow = BoardArrow(move.from, move.to, MarkKind.HINT)) }
            }
            null -> Unit
        }
    }

    /** Shows the solution, move by move. */
    fun reveal() = giveUp(autoAdvance = false)

    private fun giveUp(autoAdvance: Boolean) {
        val board = mutableState.value.board ?: return
        if (mutableState.value.phase != PlayerPhase.SOLVING) return
        pacing?.cancel()
        board.reveal()
        mutableState.update { it.copy(phase = PlayerPhase.REVEALED, hintSquare = null, hintArrow = null, showingLine = !autoAdvance) }
        finishPuzzle(board, strike = true)
        if (autoAdvance) {
            pacing = scope.launch {
                delay(timing.rushAdvanceMs)
                advance()
            }
        } else {
            playLine(board)
        }
    }

    /** After a great alternative: shows the listed main line from the same position. */
    fun showMainLine() {
        val board = mutableState.value.board ?: return
        if (!mutableState.value.solvedWithAlternative) return
        board.prepareMainLine()
        mutableState.update { it.copy(showingLine = true) }
        playLine(board)
    }

    private fun playLine(board: PuzzleBoardSession) {
        pacing?.cancel()
        pacing = scope.launch {
            while (true) {
                delay(timing.lineStepMs)
                if (!board.stepLine()) break
            }
            mutableState.update { it.copy(showingLine = false) }
        }
    }

    private fun finishPuzzle(board: PuzzleBoardSession, strike: Boolean = false) {
        ticker?.cancel()
        if (recorded) return
        recorded = true
        val outcome = board.outcome() ?: return
        val elapsed = clock() - puzzleStartedAt
        val strikes = mutableState.value.strikes + if (strike) 1 else 0
        mutableState.update {
            it.copy(
                elapsedMs = elapsed,
                strikes = strikes,
                revealedThemes = board.puzzle.themes.filter(PuzzleThemes::isKnown).sortedBy(PuzzleThemes::order).map(PuzzleThemes::displayName),
                results = it.results + ResultMark(solved = outcome.solved, clean = outcome.clean),
            )
        }
        scope.launch {
            val result = repository.recordAttempt(AttemptRecord(board.puzzle, outcome, spec.mode, spec.level, elapsed, clock()))
            mutableState.update { it.copy(rating = result.progress.rating, ratingDelta = result.ratingDelta, xpEarned = result.xpEarned) }
        }
        val maxStrikes = spec.maxStrikes
        if (maxStrikes != null && strikes >= maxStrikes) {
            pacing = scope.launch {
                delay(timing.rushAdvanceMs)
                finishRun()
            }
            return
        }
        if (spec.oneTry && outcome.solved) {
            // Rush keeps moving: no post-puzzle screen.
            pacing = scope.launch {
                delay(timing.rushAdvanceMs)
                advance()
            }
        }
    }

    /** Next puzzle, or the end of the run. */
    fun next() {
        val phase = mutableState.value.phase
        if (phase != PlayerPhase.SOLVED && phase != PlayerPhase.REVEALED) return
        advance()
    }

    private fun advance() {
        if (mutableState.value.phase == PlayerPhase.FINISHED) return
        if (mutableState.value.isLastOfRun) finishRun() else load(mutableState.value.index + 1)
    }

    /** "I found another move that works" (plan section 6.3.5). */
    fun reportAnotherMove(note: String) {
        val s = mutableState.value
        val board = s.board ?: return
        if (s.reported) return
        mutableState.update { it.copy(reported = true) }
        scope.launch {
            repository.reportAnotherMove(board.puzzle.id, board.solvingFen, s.lastWrongMove?.uci.orEmpty(), note, clock())
        }
    }

    private fun finishRun() {
        pacing?.cancel()
        ticker?.cancel()
        runClock?.cancel()
        val s = mutableState.value
        mutableState.update { it.copy(phase = PlayerPhase.FINISHED) }
        val level = spec.level ?: return
        if (spec.mode != PuzzleMode.LEVEL) return
        val run = LevelRun(level, s.solvedWithoutReveal, s.results.size)
        scope.launch {
            val result = repository.recordLevelRun(run, clock())
            mutableState.update { it.copy(levelResult = result, levelPassed = run.passed) }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(timing.tickMs)
                mutableState.update { it.copy(elapsedMs = clock() - puzzleStartedAt) }
            }
        }
    }

    private fun startRunClock(limitMs: Long) {
        val endsAt = clock() + limitMs
        runClock = scope.launch {
            while (isActive) {
                delay(timing.tickMs)
                val left = (endsAt - clock()).coerceAtLeast(0)
                mutableState.update { it.copy(remainingMs = left) }
                if (left == 0L) {
                    finishRun()
                    break
                }
            }
        }
    }

    fun close() {
        pacing?.cancel()
        ticker?.cancel()
        runClock?.cancel()
    }
}
