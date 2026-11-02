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

    /** The piece after this one, or null at the top. */
    val next: RatingRank? get() = entries.getOrNull(ordinal + 1)

    /** How far [rating] has climbed from this piece to the next, 0 to 1; full at the top. */
    fun progress(rating: Int): Float {
        val goal = next ?: return 1f
        return ((rating - from).toFloat() / (goal.from - from)).coerceIn(0f, 1f)
    }

    companion object {
        fun of(rating: Int): RatingRank = entries.lastOrNull { rating >= it.from } ?: PAWN
    }
}
