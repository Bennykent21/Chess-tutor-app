package com.chesstutor.app.data.model

enum class GameSource {
    ARENA,
    CHESS_COM,
    LICHESS
}

enum class ImportDepth(val maxGames: Int, val label: String) {
    LAST_100(100, "Last 100"),
    LAST_500(500, "Last 500"),
    ALL(1500, "All available")
}

data class ImportProgress(
    val importedCount: Int = 0,
    val targetCount: Int = 100,
    val currentPeriod: String = "",
    val incompleteCount: Int = 0,
    val phase: String = "Connecting..."
)

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
