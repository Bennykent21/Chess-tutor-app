package com.chesstutor.app.board

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.Square
import kotlin.math.abs

/** One piece gliding from [from] to [to]. */
data class PieceSlide(val piece: Piece, val from: Square, val to: Square)

/** What a move looks like on the board: which pieces slide and which piece is taken. */
data class MoveAnimationPlan(
    val slides: List<PieceSlide>,
    /** The captured piece and the square it disappears from (not the target for en passant). */
    val captured: Pair<Square, Piece>?,
)

/** The sounds of the pack (plan section 4.3). Files are in res/raw, recorded in LICENSES.md. */
enum class BoardSound { MOVE, CAPTURE, CASTLE, CHECK, PROMOTE, WIN, LOSE, CORRECT, WRONG, TAP }

/**
 * Vibration patterns (plan section 4.3): a light tick on a move, firmer on a capture or
 * check, a double pulse when wrong, a long buzz on success. Selecting a piece never vibrates.
 */
enum class HapticPattern(val timingsMs: LongArray, val amplitudes: IntArray) {
    TICK(longArrayOf(0, 18), intArrayOf(0, 90)),
    FIRM(longArrayOf(0, 30), intArrayOf(0, 200)),
    DOUBLE(longArrayOf(0, 45, 70, 45), intArrayOf(0, 200, 0, 200)),
    LONG(longArrayOf(0, 160), intArrayOf(0, 180)),
}

/** One sound (or none) and one vibration for a board event. */
data class BoardFeedback(val sound: BoardSound?, val haptic: HapticPattern)

/** Pure rules for animating and sounding a move, so they are unit tested. */
object MoveEffects {

    fun isCastling(previous: Position, move: Move): Boolean {
        val piece = previous.pieceAt(move.from) ?: return false
        return piece.type == PieceType.KING && abs(move.to.file - move.from.file) == 2
    }

    fun isEnPassant(previous: Position, move: Move): Boolean {
        val piece = previous.pieceAt(move.from) ?: return false
        return piece.type == PieceType.PAWN && move.from.file != move.to.file && previous.pieceAt(move.to) == null
    }

    fun animationPlan(previous: Position, move: Move): MoveAnimationPlan {
        val mover = previous.pieceAt(move.from) ?: return MoveAnimationPlan(emptyList(), null)
        // A promoting pawn slides as itself; the new piece appears when it lands.
        val slides = mutableListOf(PieceSlide(mover, move.from, move.to))
        if (isCastling(previous, move)) {
            val kingside = move.to.file > move.from.file
            val rank = move.from.rank
            val rookFrom = Square.of(if (kingside) 7 else 0, rank)
            val rookTo = Square.of(if (kingside) 5 else 3, rank)
            previous.pieceAt(rookFrom)?.let { slides += PieceSlide(it, rookFrom, rookTo) }
        }
        val captured = when {
            isEnPassant(previous, move) -> {
                val square = Square.of(move.to.file, move.from.rank)
                previous.pieceAt(square)?.let { square to it }
            }
            else -> previous.pieceAt(move.to)?.let { move.to to it }
        }
        return MoveAnimationPlan(slides, captured)
    }

    /** Check beats promotion beats castling beats capture beats a plain move. */
    fun feedbackFor(previous: Position, move: Move, next: Position): BoardFeedback {
        val givesCheck = LegalMoveGenerator.isKingInCheck(next, next.sideToMove)
        val isCapture = isEnPassant(previous, move) || previous.pieceAt(move.to) != null
        val sound = when {
            givesCheck -> BoardSound.CHECK
            move.promotion != null -> BoardSound.PROMOTE
            isCastling(previous, move) -> BoardSound.CASTLE
            isCapture -> BoardSound.CAPTURE
            else -> BoardSound.MOVE
        }
        val haptic = if (givesCheck || isCapture) HapticPattern.FIRM else HapticPattern.TICK
        return BoardFeedback(sound, haptic)
    }

    val WRONG = BoardFeedback(BoardSound.WRONG, HapticPattern.DOUBLE)
    val CORRECT = BoardFeedback(BoardSound.CORRECT, HapticPattern.LONG)

    /** A dropped piece that cannot go there: a buzz only, no sound. */
    val ILLEGAL = BoardFeedback(null, HapticPattern.DOUBLE)
}
