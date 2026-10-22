package com.chesstutor.app.domain.puzzle

import com.chesstutor.app.domain.ChessPosition
import kotlin.random.Random

enum class PuzzlePhase {
    /** The pack is still loading. */
    LOADING,
    /** The pack is missing or empty. */
    UNAVAILABLE,
    /** Showing the position before the opponent's set-up move. */
    SETUP,
    /** The player is looking for the move. */
    SOLVING,
    /** The player was right; the opponent's reply is about to be shown. */
    REPLYING,
    /** The player was wrong; the board is unchanged and they can try again. */
    WRONG,
    SOLVED,
    /** The player gave up and the solution is being shown. */
    REVEALED
}

data class PuzzleThemeOption(val id: String?, val label: String)

/** Asks the UI to let the player choose a promotion piece (lowercase: q, r, b, n). */
data class PuzzlePromotion(val from: String, val to: String, val choices: List<Char>)

/** Everything the Puzzles screen needs to draw. */
data class PuzzleScreenState(
    val phase: PuzzlePhase = PuzzlePhase.LOADING,
    val puzzle: Puzzle? = null,
    val fen: String = ChessPosition.STARTING_FEN,
    /** Board is turned so the player's side is at the bottom. */
    val flipped: Boolean = false,
    val sideToMove: Char = 'w',
    val selectedSquare: String? = null,
    val legalTargets: Set<String> = emptySet(),
    val lastMove: Pair<String, String>? = null,
    val badSquare: String? = null,
    val hintArrow: Pair<String, String>? = null,
    val pendingPromotion: PuzzlePromotion? = null,
    val rating: Int = 1000,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val solvedCount: Int = 0,
    /** Rating change from the puzzle just finished (null while solving). */
    val ratingDelta: Int? = null,
    val theme: String? = null,
    val themeOptions: List<PuzzleThemeOption> = emptyList(),
    val mistakes: Int = 0,
    val hintsUsed: Int = 0,
    /** 1-based number of the move the player is looking for. */
    val step: Int = 1,
    val totalSteps: Int = 1,
    /** Solved with another winning move than the listed one ("Solved: a great alternative"). */
    val solvedWithAlternative: Boolean = false,
)

/**
 * The brain of the Puzzles tab: no Android, no timers. The view model calls these methods and adds
 * the short pauses (e.g. before the opponent's reply) between them.
 */
class PuzzleTrainer(
    private val pack: PuzzlePack,
    initial: PuzzleProgress,
    private val store: PuzzleProgressStore? = null,
    random: Random = Random.Default
) {
    private val selector = PuzzleSelector(pack, random)
    private var progress = initial
    private var session: PuzzleSession? = null
    private var pendingReply: String? = null
    private var revealPosition: ChessPosition? = null
    private var finished = false
    private val skipped = mutableSetOf<String>()

    var state: PuzzleScreenState = PuzzleScreenState(
        phase = if (pack.isEmpty) PuzzlePhase.UNAVAILABLE else PuzzlePhase.LOADING,
        rating = initial.rating,
        streak = initial.streak,
        bestStreak = initial.bestStreak,
        solvedCount = initial.solved,
        themeOptions = buildList {
            add(PuzzleThemeOption(null, "Mixed"))
            pack.themes.sortedBy { PuzzleThemes.order(it) }
                .forEach { add(PuzzleThemeOption(it, PuzzleThemes.displayName(it))) }
        }
    )
        private set

    // ---------- choosing a puzzle ----------

    fun setTheme(theme: String?) {
        state = state.copy(theme = theme)
    }

    /** The current puzzle will not be offered again right away. */
    fun markSkipped() {
        state.puzzle?.let { skipped += it.id }
    }

    /** Picks the next puzzle. Returns false if the pack has nothing usable. */
    fun loadNext(): Boolean {
        val theme = state.theme
        val currentId = state.puzzle?.id
        val tried = mutableSetOf<String>()
        repeat(MAX_TRIES) {
            val avoid = progress.seenIds + skipped + tried
            val puzzle = selector.next(progress.rating, theme, avoid)
                // Everything in this theme has been seen: allow repeats, but not the same one twice.
                ?: selector.next(progress.rating, theme, setOfNotNull(currentId) + tried)
                ?: return fail()
            val started = PuzzleSession.start(puzzle)
            if (started == null) {
                tried += puzzle.id
                return@repeat
            }
            session = started
            pendingReply = null
            revealPosition = null
            finished = false
            state = state.copy(
                phase = PuzzlePhase.SETUP,
                puzzle = puzzle,
                fen = puzzle.fen,                       // position BEFORE the opponent's move
                flipped = started.solverColor == 'b',
                sideToMove = started.solverColor,
                selectedSquare = null,
                legalTargets = emptySet(),
                lastMove = null,
                badSquare = null,
                hintArrow = null,
                pendingPromotion = null,
                ratingDelta = null,
                mistakes = 0,
                hintsUsed = 0,
                step = 1,
                totalSteps = puzzle.solverMoveCount.coerceAtLeast(1),
                solvedWithAlternative = false,
            )
            return true
        }
        return fail()
    }

    private fun fail(): Boolean {
        state = state.copy(phase = PuzzlePhase.UNAVAILABLE)
        return false
    }

    /** Shows the position after the opponent's set-up move; the player may now move. */
    fun beginSolving() {
        val s = session ?: return
        if (state.phase != PuzzlePhase.SETUP) return
        state = state.copy(
            phase = PuzzlePhase.SOLVING,
            fen = s.fen,
            lastMove = squares(s.openingMove)
        )
    }

    // ---------- playing ----------

    fun onSquareTapped(square: String) {
        if (state.phase != PuzzlePhase.SOLVING || state.pendingPromotion != null) return
        val legal = ChessPosition(state.fen).legalMoves
        val selected = state.selectedSquare

        if (selected != null && selected != square) {
            val candidates = legal.filter { it.from == selected && it.to == square }
            if (candidates.isNotEmpty()) {
                if (candidates.any { it.promotion != null }) {
                    val choices = candidates.mapNotNull { it.promotion }.map { it.lowercaseChar() }.distinct()
                    state = state.copy(pendingPromotion = PuzzlePromotion(selected, square, choices))
                } else {
                    attempt(selected + square)
                }
                return
            }
        }
        // Select one of the player's own pieces (or deselect).
        val targets = legal.filter { it.from == square }.map { it.to }.toSet()
        state = if (targets.isEmpty() || square == selected) {
            state.copy(selectedSquare = null, legalTargets = emptySet(), hintArrow = null)
        } else {
            state.copy(selectedSquare = square, legalTargets = targets, hintArrow = null)
        }
    }

    fun choosePromotion(piece: Char) {
        val promo = state.pendingPromotion ?: return
        state = state.copy(pendingPromotion = null)
        attempt(promo.from + promo.to + piece.lowercaseChar())
    }

    fun cancelPromotion() {
        state = state.copy(pendingPromotion = null, selectedSquare = null, legalTargets = emptySet())
    }

    private fun attempt(uci: String) {
        val s = session ?: return
        val fenBefore = state.fen
        when (val result = s.submit(uci)) {
            is PuzzleMoveResult.Illegal, is PuzzleMoveResult.NotSolving ->
                state = state.copy(selectedSquare = null, legalTargets = emptySet())

            is PuzzleMoveResult.Wrong -> state = state.copy(
                phase = PuzzlePhase.WRONG,
                badSquare = uci.substring(2, 4),
                selectedSquare = null,
                legalTargets = emptySet(),
                hintArrow = null,
                mistakes = result.mistakes
            )

            is PuzzleMoveResult.Correct -> {
                val afterPlayerMove = ChessPosition(fenBefore).also { it.play(uci) }.fen
                if (result.solved) {
                    val last = squares(result.reply ?: uci)
                    state = state.copy(
                        fen = s.fen,
                        lastMove = last,
                        selectedSquare = null,
                        legalTargets = emptySet(),
                        hintArrow = null
                    )
                    finish()
                } else {
                    pendingReply = result.reply
                    state = state.copy(
                        phase = PuzzlePhase.REPLYING,
                        fen = afterPlayerMove,
                        lastMove = squares(uci),
                        selectedSquare = null,
                        legalTargets = emptySet(),
                        hintArrow = null
                    )
                }
            }
        }
    }

    /** Called after a short pause: shows the opponent's reply and hands the move back. */
    fun showReply() {
        val s = session ?: return
        val reply = pendingReply ?: return
        if (state.phase != PuzzlePhase.REPLYING) return
        pendingReply = null
        state = state.copy(
            phase = PuzzlePhase.SOLVING,
            fen = s.fen,
            lastMove = squares(reply),
            step = (state.step + 1).coerceAtMost(state.totalSteps)
        )
    }

    /** Called after a short pause on a wrong move: clears the red mark so they can retry. */
    fun clearWrong() {
        if (state.phase != PuzzlePhase.WRONG) return
        state = state.copy(phase = PuzzlePhase.SOLVING, badSquare = null)
    }

    // ---------- help ----------

    fun hint() {
        val s = session ?: return
        if (state.phase != PuzzlePhase.SOLVING) return
        when (val hint = s.hint()) {
            is PuzzleHint.Piece -> {
                val targets = ChessPosition(state.fen).legalMoves
                    .filter { it.from == hint.square }.map { it.to }.toSet()
                state = state.copy(
                    selectedSquare = hint.square,
                    legalTargets = targets,
                    hintArrow = null,
                    hintsUsed = s.hintsUsed
                )
            }
            is PuzzleHint.Move -> {
                val (from, to) = squares(hint.uci)
                state = state.copy(
                    selectedSquare = from,
                    hintArrow = from to to,
                    hintsUsed = s.hintsUsed
                )
            }
            null -> Unit
        }
    }

    /** Gives up. Returns the rest of the solution (UCI) for the view model to play out slowly. */
    fun reveal(): List<String> {
        val s = session ?: return emptyList()
        if (state.phase != PuzzlePhase.SOLVING && state.phase != PuzzlePhase.WRONG) return emptyList()
        val moves = s.reveal()
        revealPosition = ChessPosition(s.fen)
        state = state.copy(
            phase = PuzzlePhase.REVEALED,
            badSquare = null,
            selectedSquare = null,
            legalTargets = emptySet(),
            hintArrow = null,
            pendingPromotion = null
        )
        finish()
        return moves
    }

    fun applyRevealMove(uci: String) {
        val position = revealPosition ?: return
        if (state.phase != PuzzlePhase.REVEALED) return
        if (position.play(uci)) state = state.copy(fen = position.fen, lastMove = squares(uci))
    }

    // ---------- bookkeeping ----------

    private fun finish() {
        val s = session ?: return
        val puzzle = state.puzzle ?: return
        val outcome = s.outcome() ?: return
        if (!finished) {
            finished = true
            val before = progress.rating
            progress = progress.afterFinish(puzzle, outcome)
            store?.save(progress)
            state = state.copy(
                ratingDelta = progress.rating - before,
                rating = progress.rating,
                streak = progress.streak,
                bestStreak = progress.bestStreak,
                solvedCount = progress.solved
            )
        }
        if (state.phase != PuzzlePhase.REVEALED) {
            state = state.copy(
                phase = PuzzlePhase.SOLVED,
                mistakes = s.mistakes,
                hintsUsed = s.hintsUsed,
                solvedWithAlternative = s.solvedWithAlternative,
            )
        }
    }

    private fun squares(uci: String): Pair<String, String> = uci.substring(0, 2) to uci.substring(2, 4)

    private companion object {
        const val MAX_TRIES = 12
    }
}
