package com.chesstutor.app.domain.game

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.domain.bot.BotProfile

/** The clock for a game against a bot. */
enum class TimeControl(val label: String, val initialMs: Long?, val incrementMs: Long = 0) {
    NONE("No clock", null),
    BLITZ_5("5 min", 5 * 60_000L),
    RAPID_10("10 min", 10 * 60_000L),
    RAPID_15_10("15 | 10", 15 * 60_000L, 10_000L);

    /** "rapid" or "blitz", as saved games record it. */
    val timeClass: String get() = if (this == BLITZ_5) "blitz" else "rapid"
}

/** Which colour the player takes. */
enum class SideChoice(val label: String) { WHITE("White"), RANDOM("Random"), BLACK("Black") }

/** House rules for one game (plan 6.1: the bot builder's "Rules" group). */
data class GameRules(
    val timeControl: TimeControl = TimeControl.NONE,
    val takebacks: Boolean = true,
    val hints: Boolean = true,
    val evalBar: Boolean = false,
)

/** Everything needed to start a game against a bot. */
data class GameSetup(
    val bot: BotProfile,
    val playerColor: PieceColor,
    val rules: GameRules = GameRules(),
    val startFen: String = Position.STARTING_FEN,
)

/** Why a game ended. */
enum class GameEnd(val label: String) {
    CHECKMATE("by checkmate"),
    STALEMATE("by stalemate"),
    INSUFFICIENT_MATERIAL("by insufficient material"),
    REPETITION("by repetition"),
    FIFTY_MOVES("by the 50-move rule"),
    TIMEOUT("on time"),
    RESIGNATION("by resignation"),
}

/** The end of a game: [winner] is null for a draw. */
data class GameResult(val winner: PieceColor?, val end: GameEnd) {
    /** "1-0", "0-1" or "1/2-1/2", as PGN writes it. */
    val pgn: String get() = when (winner) {
        PieceColor.WHITE -> "1-0"
        PieceColor.BLACK -> "0-1"
        null -> "1/2-1/2"
    }
}
