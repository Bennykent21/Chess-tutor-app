package com.chesstutor.app.data.model

enum class GameSource {
    ARENA,
    CHESS_COM,
    LICHESS
}

/** How many games of each clock to import (D59): [label] when linking, [moreLabel] for "Import more" (older games). */
enum class ImportDepth(val maxGames: Int, val label: String, val moreLabel: String) {
    LAST_100(100, "100 of each", "100 more of each"),
    LAST_500(500, "500 of each", "500 more of each"),
    ALL(1500, "1,500 of each", "1,500 more of each")
}

data class ImportProgress(
    val importedCount: Int = 0,
    val targetCount: Int = 100,
    val currentPeriod: String = "",
    val incompleteCount: Int = 0,
    val phase: String = "Connecting...",
    /** Months (Chess.com) or streams (Lichess) that could not be downloaded even after retrying. */
    val failedPeriods: Int = 0,
    /** How far along the import really is, 0 to 1, when the count of games alone would mislead. */
    val fraction: Float? = null,
) {
    /** What the progress bar shows. */
    val shownFraction: Float
        get() = fraction ?: if (targetCount > 0) importedCount.toFloat() / targetCount else 0f
}

data class GameRecord(
    val id: String,
    val dateMillis: Long,
    val botName: String,
    val botRating: Int,
    val result: String,
    val pgn: String,
    val moveCount: Int,
    val userColor: String,
    val finalFen: String,
    val uciMoves: String = "",
    val source: GameSource = when {
        id.startsWith("chesscom_") -> GameSource.CHESS_COM
        id.startsWith("lichess_") -> GameSource.LICHESS
        else -> GameSource.ARENA
    },
    val termination: String = "",
    val timeClass: String = "rapid",
    val incompleteParse: Boolean = false
) {
    val isAccountGame: Boolean
        get() = source != GameSource.ARENA || id.startsWith("chesscom_") || id.startsWith("lichess_")

    val userOutcomeLabel: String
        get() {
            val isWhite = userColor.equals("white", ignoreCase = true)
            return when {
                (isWhite && result == "1-0") || (!isWhite && result == "0-1") -> "Win"
                (isWhite && result == "0-1") || (!isWhite && result == "1-0") -> "Loss"
                result == "1/2-1/2" -> "Draw"
                else -> result
            }
        }
}
