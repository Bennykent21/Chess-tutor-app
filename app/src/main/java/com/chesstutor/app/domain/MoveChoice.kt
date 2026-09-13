package com.chesstutor.app.domain

data class MoveChoice(
    val from: String,
    val to: String,
    val san: String,
    val piece: Char,       // lowercase: p, n, b, r, q, k
    val promotion: Char? = null,
) {
    val uci: String get() = "$from$to${promotion ?: ""}"

    companion object {
        fun fromUci(uci: String, piece: Char = 'p', san: String = uci): MoveChoice {
            val from = uci.substring(0, 2)
            val to = uci.substring(2, 4)
            val promo = if (uci.length > 4) uci[4].lowercaseChar() else null
            return MoveChoice(from, to, san, piece.lowercaseChar(), promo)
        }
    }
}
