package com.chesstutor.app.core

/**
 * Finds the legal move a SAN token names ("Nbd2", "exd5", "e8=Q+", "O-O") by reading the token,
 * instead of writing out the SAN of every legal move and comparing. Imports replay thousands of
 * games, and naming every move at every ply was most of their cost.
 */
object SanReplay {

    /** The move [san] names among [legal] in [position], or null when it names none or several. */
    fun moveFor(position: Position, legal: List<Move>, san: String): Move? {
        val token = san.trimEnd('+', '#', '!', '?')
        if (token == "O-O" || token == "0-0") return castling(position, legal, kingside = true)
        if (token == "O-O-O" || token == "0-0-0") return castling(position, legal, kingside = false)

        val body = token.substringBefore('=')
        // "e8Q" as well as "e8=Q".
        val promotionLetter = token.substringAfter('=', "").firstOrNull() ?: body.lastOrNull()?.takeIf { it in "QRBN" }
        val squarePart = if (promotionLetter != null && '=' !in token) body.dropLast(1) else body
        if (squarePart.length < 2) return null
        val target = squarePart.takeLast(2)
        if (target[0] !in 'a'..'h' || target[1] !in '1'..'8') return null
        val to = Square.fromAlgebraic(target)

        val first = squarePart[0]
        val piece = when (first) {
            'K' -> PieceType.KING
            'Q' -> PieceType.QUEEN
            'R' -> PieceType.ROOK
            'B' -> PieceType.BISHOP
            'N' -> PieceType.KNIGHT
            else -> PieceType.PAWN
        }
        val hints = squarePart.dropLast(2).let { if (piece == PieceType.PAWN) it else it.drop(1) }.replace("x", "")
        val fileHint = hints.firstOrNull { it in 'a'..'h' }
        val rankHint = hints.firstOrNull { it in '1'..'8' }
        val promotion = promotionLetter?.let { letter ->
            when (letter) {
                'Q' -> PieceType.QUEEN
                'R' -> PieceType.ROOK
                'B' -> PieceType.BISHOP
                'N' -> PieceType.KNIGHT
                else -> return null
            }
        }
        var found: Move? = null
        for (move in legal) {
            if (move.to != to || move.promotion != promotion) continue
            if (position.pieceAt(move.from)?.type != piece) continue
            if (fileHint != null && move.from.fileChar != fileHint) continue
            if (rankHint != null && move.from.rankChar != rankHint) continue
            if (found != null) return null
            found = move
        }
        return found
    }

    private fun castling(position: Position, legal: List<Move>, kingside: Boolean): Move? =
        legal.firstOrNull { move ->
            position.pieceAt(move.from)?.type == PieceType.KING &&
                (move.isCastling || kotlin.math.abs(move.to.file - move.from.file) == 2) &&
                (move.to.file > move.from.file) == kingside
        }
}
