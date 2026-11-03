package com.chesstutor.app.domain.account

/** The piece a rating earns (D56), from [from] upwards. The player's piece follows their rating. */
enum class RatingRank(val label: String, val from: Int) {
    PAWN("Pawn", 0),
    KNIGHT("Knight", 800),
    BISHOP("Bishop", 1200),
    ROOK("Rook", 1500),
    QUEEN("Queen", 1800),
    KING("King", 2200),
    ;

    companion object {
        fun of(rating: Int): RatingRank = entries.lastOrNull { rating >= it.from } ?: PAWN
    }
}
