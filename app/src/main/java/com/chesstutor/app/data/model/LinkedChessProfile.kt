package com.chesstutor.app.data.model

enum class RatingPlatform(val displayName: String) {
    CHESS_COM("Chess.com"),
    LICHESS("Lichess")
}

enum class RatingTimeControl(val displayName: String) {
    RAPID("Rapid"),
    BLITZ("Blitz"),
    BULLET("Bullet")
}

data class LinkedChessProfile(
    val platform: RatingPlatform,
    val username: String,
    val rapidRating: Int?,
    val blitzRating: Int?,
    val bulletRating: Int?,
    val selectedTimeControl: RatingTimeControl = RatingTimeControl.RAPID,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /** The rating the app follows (D61): the average of the clocks the player has a rating in. */
    val activeRating: Int?
        get() = listOfNotNull(rapidRating, blitzRating, bulletRating).takeIf { it.isNotEmpty() }?.average()?.toInt()

    fun tuningDescription(): String {
        val rating = activeRating ?: 1200
        return "Tuned to approximate your ${platform.displayName} rating ($rating)"
    }
}
