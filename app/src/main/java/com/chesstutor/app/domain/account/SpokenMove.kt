package com.chesstutor.app.domain.account

/**
 * A move the way a coach says it: "Nf6" is "Knight to f6", "exd5" is "Pawn takes d5", "O-O"
 * is "Castling short" (beta review: a tutor should name the pieces, the notation is already
 * under the board). Check and mate marks are left to the sentence around the move.
 */
object SpokenMove {

    /** [san] in words, starting with a capital ("Knight to f6"). */
    fun of(san: String): String {
        val clean = san.trimEnd('+', '#', '!', '?')
        if (clean == "O-O-O" || clean == "0-0-0") return "Castling long"
        if (clean == "O-O" || clean == "0-0") return "Castling short"
        val square = Regex("[a-h][1-8]").findAll(clean).lastOrNull()?.value ?: return san
        val piece = when (clean.firstOrNull()) {
            'K' -> "King"
            'Q' -> "Queen"
            'R' -> "Rook"
            'B' -> "Bishop"
            'N' -> "Knight"
            else -> "Pawn"
        }
        val verb = if ('x' in clean) "takes" else "to"
        val promotion = Regex("=([QRBN])").find(clean)?.groupValues?.get(1)?.let { ", becoming a ${promoted(it[0])}" }.orEmpty()
        return "$piece $verb $square$promotion"
    }

    /** [san] in words for the middle of a sentence ("best was knight to f6"). */
    fun inSentence(san: String): String = of(san).replaceFirstChar(Char::lowercaseChar)

    private fun promoted(letter: Char): String = when (letter) {
        'Q' -> "queen"
        'R' -> "rook"
        'B' -> "bishop"
        else -> "knight"
    }
}
