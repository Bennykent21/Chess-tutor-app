package com.chesstutor.app.domain.puzzle

import com.chesstutor.app.domain.ChessPosition

enum class PuzzleStatus { SOLVING, SOLVED, REVEALED }

/** What happened when the player tried a move. */
sealed interface PuzzleMoveResult {
    /** Right move. [reply] is the opponent's answer that was auto-played (null if none). */
    data class Correct(val reply: String?, val solved: Boolean) : PuzzleMoveResult

    /** Legal but not the solution. The board is unchanged so the player can try again. */
    data class Wrong(val mistakes: Int) : PuzzleMoveResult

    /** Not a legal move in the current position (the board is unchanged). */
    data object Illegal : PuzzleMoveResult

    /** The puzzle is already finished. */
    data object NotSolving : PuzzleMoveResult
}

sealed interface PuzzleHint {
    /** First hint: which piece to move (its square, e.g. "g1"). */
    data class Piece(val square: String) : PuzzleHint

    /** Second hint: the whole move (UCI). */
    data class Move(val uci: String) : PuzzleHint
}

data class PuzzleOutcome(
    val solved: Boolean,
    /** Solved with no mistakes, no hints and no reveal; the only result that raises rating. */
    val clean: Boolean,
    val mistakes: Int,
    val hintsUsed: Int,
    val revealed: Boolean
)

/**
 * Plays one puzzle. Pure logic with no Android or UI dependency.
 *
 * Flow: [start] plays the opponent's opening move, then the UI calls [submit] with each move the
 * player makes. Correct moves trigger the opponent's scripted reply; a wrong move leaves the board
 * untouched. Like Lichess, any move that checkmates is accepted even if it is not the listed one.
 */
class PuzzleSession private constructor(
    val puzzle: Puzzle,
    private val position: ChessPosition,
    /** The opponent's set-up move (UCI), already played on the board; animate it when showing. */
    val openingMove: String
) {
    private var index = 1          // index in puzzle.moves of the next move the player must find
    private var hintLevel = 0

    var status: PuzzleStatus = PuzzleStatus.SOLVING
        private set
    var mistakes: Int = 0
        private set
    var hintsUsed: Int = 0
        private set

    /** UCI of the last move played on the board (opening move, player move or reply). */
    var lastMove: String? = openingMove
        private set

    /** Current position. */
    val fen: String get() = position.fen

    /** 'w' or 'b': the side the player is solving for (also the board orientation). */
    val solverColor: Char = position.sideToMove

    fun submit(uci: String): PuzzleMoveResult {
        if (status != PuzzleStatus.SOLVING) return PuzzleMoveResult.NotSolving
        val move = uci.trim().lowercase()
        if (position.legalMoves.none { it.uci == move }) return PuzzleMoveResult.Illegal

        val isExpected = move == puzzle.moves[index]
        val deliversMate = !isExpected && ChessPosition(position.fen).let { trial ->
            trial.play(move) && trial.isCheckmate
        }
        if (!isExpected && !deliversMate) {
            mistakes++
            return PuzzleMoveResult.Wrong(mistakes)
        }

        position.play(move)
        lastMove = move
        hintLevel = 0
        index++
        if (deliversMate || index >= puzzle.moves.size) return finishSolved(reply = null)

        val reply = puzzle.moves[index]
        if (!position.play(reply)) return finishSolved(reply = null)   // corrupt tail: don't trap the player
        lastMove = reply
        index++
        return if (index >= puzzle.moves.size) finishSolved(reply) else PuzzleMoveResult.Correct(reply, solved = false)
    }

    /** First call shows the piece to move, later calls show the full move. Null once finished. */
    fun hint(): PuzzleHint? {
        if (status != PuzzleStatus.SOLVING) return null
        val expected = puzzle.moves[index]
        hintsUsed++
        hintLevel++
        return if (hintLevel == 1) PuzzleHint.Piece(expected.substring(0, 2)) else PuzzleHint.Move(expected)
    }

    /** Gives up: returns the rest of the solution (UCI) so the UI can play it out. */
    fun reveal(): List<String> {
        if (status != PuzzleStatus.SOLVING) return emptyList()
        status = PuzzleStatus.REVEALED
        return puzzle.moves.subList(index, puzzle.moves.size)
    }

    /** Null while still solving. */
    fun outcome(): PuzzleOutcome? = when (status) {
        PuzzleStatus.SOLVING -> null
        PuzzleStatus.SOLVED -> PuzzleOutcome(
            solved = true,
            clean = mistakes == 0 && hintsUsed == 0,
            mistakes = mistakes,
            hintsUsed = hintsUsed,
            revealed = false
        )
        PuzzleStatus.REVEALED -> PuzzleOutcome(
            solved = false,
            clean = false,
            mistakes = mistakes,
            hintsUsed = hintsUsed,
            revealed = true
        )
    }

    private fun finishSolved(reply: String?): PuzzleMoveResult {
        status = PuzzleStatus.SOLVED
        return PuzzleMoveResult.Correct(reply, solved = true)
    }

    companion object {
        /**
         * Sets the puzzle up. Returns null when the puzzle is unusable (bad FEN, illegal opening
         * move, or an illegal first solution move) so a corrupt entry is skipped, never a crash.
         */
        fun start(puzzle: Puzzle): PuzzleSession? {
            if (puzzle.moves.size < 2) return null
            val position = runCatching { ChessPosition(puzzle.fen) }.getOrNull() ?: return null
            val opening = puzzle.moves[0]
            if (!position.play(opening)) return null
            // The first solution move must be playable, otherwise the puzzle could never be solved.
            if (!ChessPosition(position.fen).play(puzzle.moves[1])) return null
            return PuzzleSession(puzzle, position, opening)
        }
    }
}
