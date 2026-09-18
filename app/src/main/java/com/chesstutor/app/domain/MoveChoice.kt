package com.chesstutor.app.domain

import com.example.chess.core.Move
import com.example.chess.core.PieceType
import com.example.chess.core.Square

/**
 * UI/domain projection of the authoritative core [Move].
 *
 * The chess rules engine owns move legality and UCI semantics; this type only
 * carries the presentation fields needed by the app.
 */
data class MoveChoice(
    val from: String,
    val to: String,
    val san: String,
    val piece: Char,
    val promotion: Char? = null,
) {
    val uci: String
        get() = toCoreMove().uci

    fun toCoreMove(): Move = Move(
        from = Square.fromAlgebraic(from),
        to = Square.fromAlgebraic(to),
        promotion = promotion?.let(PieceType::fromNotation)
    )

    companion object {
        fun fromCoreMove(move: Move, san: String, piece: Char): MoveChoice =
            MoveChoice(
                from = move.from.algebraic,
                to = move.to.algebraic,
                san = san,
                piece = piece.lowercaseChar(),
                promotion = move.promotion?.notation?.lowercaseChar()
            )

        fun fromUci(uci: String, piece: Char = 'p', san: String = uci): MoveChoice {
            val move = Move.fromUci(uci)
            return MoveChoice(
                from = move.from.algebraic,
                to = move.to.algebraic,
                san = san,
                piece = piece.lowercaseChar(),
                promotion = move.promotion?.notation?.lowercaseChar()
            )
        }
    }
}
