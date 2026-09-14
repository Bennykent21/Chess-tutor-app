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
    val activeRating: Int?
        get() = when (selectedTimeControl) {
            RatingTimeControl.RAPID -> rapidRating ?: blitzRating ?: bulletRating
            RatingTimeControl.BLITZ -> blitzRating ?: rapidRating ?: bulletRating
            RatingTimeControl.BULLET -> bulletRating ?: blitzRating ?: rapidRating
        }

    fun tuningDescription(): String {
        val rating = activeRating ?: 1200
        return "Tuned to approximate your ${platform.displayName} ${selectedTimeControl.displayName} rating ($rating)"
    }
}
