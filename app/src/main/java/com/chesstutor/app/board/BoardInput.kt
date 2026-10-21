package com.chesstutor.app.board

import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.Square

/** What a tap or a drop means. */
sealed interface BoardIntent {
    /** Select [square] (its legal targets are shown). */
    data class Select(val square: Square) : BoardIntent
    /** Clear the selection. */
    data object Deselect : BoardIntent
    /** Play [move]. */
    data class Play(val move: Move) : BoardIntent
    /** The move promotes: ask which piece (unless auto-queen is on). */
    data class ChoosePromotion(val from: Square, val to: Square) : BoardIntent
    /** A move was tried that is not legal: snap back, buzz. */
    data object Rejected : BoardIntent
}

/**
 * Tap-tap and drag-and-drop rules (plan section 4.3), pure so they are unit tested.
 * [movable] is the colour the player may move now, or null when the board is read-only.
 */
object BoardInput {

    fun canPick(position: Position, square: Square, movable: PieceColor?): Boolean {
        val piece = position.pieceAt(square) ?: return false
        return movable != null && piece.color == movable
    }

    fun targetsFrom(from: Square, legalMoves: List<Move>): Set<Square> =
        legalMoves.filter { it.from == from }.map { it.to }.toSet()

    fun tap(
        selected: Square?,
        square: Square,
        position: Position,
        legalMoves: List<Move>,
        movable: PieceColor?,
        autoQueen: Boolean,
    ): BoardIntent {
        if (movable == null) return BoardIntent.Deselect
        if (selected == null) {
            return if (canPick(position, square, movable)) BoardIntent.Select(square) else BoardIntent.Deselect
        }
        if (square == selected) return BoardIntent.Deselect
        // Tapping another of your own pieces switches the selection.
        if (canPick(position, square, movable)) return BoardIntent.Select(square)
        return moveIntent(selected, square, legalMoves, autoQueen, illegal = BoardIntent.Deselect)
    }

    fun drop(from: Square, to: Square?, legalMoves: List<Move>, autoQueen: Boolean): BoardIntent {
        if (to == null || to == from) return BoardIntent.Select(from)
        return moveIntent(from, to, legalMoves, autoQueen, illegal = BoardIntent.Rejected)
    }

    private fun moveIntent(from: Square, to: Square, legalMoves: List<Move>, autoQueen: Boolean, illegal: BoardIntent): BoardIntent {
        val candidates = legalMoves.filter { it.from == from && it.to == to }
        return when {
            candidates.isEmpty() -> illegal
            candidates.any { it.promotion != null } ->
                if (autoQueen) BoardIntent.Play(candidates.first { it.promotion == PieceType.QUEEN })
                else BoardIntent.ChoosePromotion(from, to)
            else -> BoardIntent.Play(candidates.first())
        }
    }

    /** The legal move for a promotion choice, or null if [type] is not offered. */
    fun promotionMove(from: Square, to: Square, type: PieceType, legalMoves: List<Move>): Move? =
        legalMoves.firstOrNull { it.from == from && it.to == to && it.promotion == type }
}
