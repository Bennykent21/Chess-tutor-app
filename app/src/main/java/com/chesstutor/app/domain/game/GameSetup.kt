package com.chesstutor.app.domain.game

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.domain.bot.BotProfile

/**
 * A game's clock (D92): minutes to start with and seconds added after each move; a null
 * [initialMs] is no clock. Presets cover bullet, blitz and rapid; any other value is a custom clock.
 */
data class TimeControl(val initialMs: Long?, val incrementMs: Long = 0) {
    init {
        require(initialMs == null || initialMs > 0) { "a clock needs time on it" }
        require(incrementMs >= 0) { "the increment cannot be negative" }
    }

    /** "No clock", "10 min", "3 | 2" or "0:30" for less than a minute. */
    val label: String
        get() {
            val start = initialMs ?: return "No clock"
            val seconds = start / 1000
            val minutes = if (seconds % 60 == 0L) "${seconds / 60}" else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
            return when {
                incrementMs > 0 -> "$minutes | ${incrementMs / 1000}"
                seconds % 60 == 0L -> "$minutes min"
                else -> minutes
            }
        }

    /**
     * "untimed", "bullet", "blitz" or "rapid", as saved games record it, from the game's expected
     * length (the start plus 40 moves' increments, as Lichess counts): under 3 minutes is bullet,
     * under 10 blitz.
     */
    val timeClass: String
        get() {
            val start = initialMs ?: return "untimed"
            val expected = start + 40 * incrementMs
            return when {
                expected < 3 * MINUTE -> "bullet"
                expected < 10 * MINUTE -> "blitz"
                else -> "rapid"
            }
        }

    /** How it is saved: "none", or the start and increment in milliseconds ("600000+5000"). */
    fun encode(): String = initialMs?.let { "$it+$incrementMs" } ?: "none"

    companion object {
        private const val MINUTE = 60_000L

        fun of(minutes: Int, addedSeconds: Int = 0) = TimeControl(minutes * MINUTE, addedSeconds * 1000L)

        val NONE = TimeControl(null)
        val BULLET_1 = of(1)
        val BULLET_1_1 = of(1, 1)
        val BULLET_2_1 = of(2, 1)
        val BLITZ_3 = of(3)
        val BLITZ_3_2 = of(3, 2)
        val BLITZ_5 = of(5)
        val RAPID_10 = of(10)
        val RAPID_15_10 = of(15, 10)
        val RAPID_30 = of(30)

        /** The presets the picker offers, by kind. */
        val GROUPS: List<Pair<String, List<TimeControl>>> = listOf(
            "Bullet" to listOf(BULLET_1, BULLET_1_1, BULLET_2_1),
            "Blitz" to listOf(BLITZ_3, BLITZ_3_2, BLITZ_5),
            "Rapid" to listOf(RAPID_10, RAPID_15_10, RAPID_30),
        )

        val PRESETS: List<TimeControl> = GROUPS.flatMap { it.second }

        /** Reads [encode]'s text, and the enum names saved before D92 (NONE, BLITZ_5, RAPID_10, RAPID_15_10). */
        fun decode(text: String?): TimeControl = when (text) {
            null, "none", "NONE" -> NONE
            "BLITZ_5" -> BLITZ_5
            "RAPID_10" -> RAPID_10
            "RAPID_15_10" -> RAPID_15_10
            else -> {
                val parts = text.split('+')
                val start = parts.getOrNull(0)?.toLongOrNull()
                val added = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                if (start != null && start > 0 && added >= 0) TimeControl(start, added) else NONE
            }
        }
    }
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
    /** Watch mode (review R6): a second bot plays the side named by [playerColor]. */
    val watch: BotProfile? = null,
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
    AGREEMENT("by agreement"),
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
