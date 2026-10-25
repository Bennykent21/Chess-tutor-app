package com.chesstutor.app.domain.account

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.GameSource
import com.chesstutor.app.domain.opening.Opening
import com.chesstutor.app.domain.opening.OpeningTree

/** How a game ended for the player. */
enum class GameOutcome(val label: String) {
    WIN("Win"),
    DRAW("Draw"),
    LOSS("Loss"),
    /** No result recorded (an abandoned import or a game cut short). */
    UNKNOWN("No result"),
}

/**
 * The clock a game was played with. Chess.com, Lichess and the app's own games name these
 * differently ("ultraBullet", "correspondence", "daily"), so they are folded into one list.
 */
enum class TimeClass(val label: String) {
    BULLET("Bullet"),
    BLITZ("Blitz"),
    RAPID("Rapid"),
    CLASSICAL("Classical"),
    DAILY("Daily"),
    UNTIMED("No clock");

    companion object {
        fun of(value: String): TimeClass = when (value.trim().lowercase()) {
            "ultrabullet", "bullet" -> BULLET
            "blitz" -> BLITZ
            "classical", "standard" -> CLASSICAL
            "daily", "correspondence" -> DAILY
            "untimed", "none", "unlimited" -> UNTIMED
            else -> RAPID
        }
    }
}

/** Where a game came from, as the games list names it. */
val GameSource.label: String
    get() = when (this) {
        GameSource.ARENA -> "This app"
        GameSource.CHESS_COM -> "Chess.com"
        GameSource.LICHESS -> "Lichess"
    }

/** A saved game with what the games list needs worked out once (plan section 6.4). */
data class GameSummary(
    val record: GameRecord,
    val outcome: GameOutcome,
    val userColor: PieceColor,
    val timeClass: TimeClass,
    /** The deepest named line from the opening database, or null (no moves, or a set-up position). */
    val opening: Opening?,
    /** The opening's name: the database's, else the one the site wrote in the PGN. */
    val openingName: String?,
    /** The ply (0-based) of the first move that left the opening database, or null if it never did. */
    val leftBookAt: Int?,
    /** The position the game started from when it was not the normal start, else null. */
    val startFen: String?,
) {
    val id: String get() = record.id
    val opponent: String get() = record.botName
    val opponentRating: Int get() = record.botRating
    val source: GameSource get() = record.source
    val dateMillis: Long get() = record.dateMillis
    val moves: List<String> get() = record.uciMoves.split(' ').filter { it.isNotBlank() }

    /** Full moves, as players count them (1. e4 e5 is one move). */
    val moveNumber: Int get() = (record.moveCount + 1) / 2

    /** "Sicilian Defense", for grouping and the opening filter. */
    val openingFamily: String? get() = opening?.family ?: openingName?.substringBefore(':')?.trim()?.ifBlank { null }

    /** How many of the game's first moves were in the opening database. */
    val bookPlies: Int get() = leftBookAt ?: if (opening != null) moves.size else 0

    /** The full move in which the game left the opening database ("move 7"), or null. */
    val leftBookAtMove: Int? get() = leftBookAt?.let { it / 2 + 1 }
}

/** What the games list shows: every filter is optional and they all apply together. */
data class GameFilter(
    val source: GameSource? = null,
    val outcome: GameOutcome? = null,
    val color: PieceColor? = null,
    val timeClass: TimeClass? = null,
    val openingFamily: String? = null,
    val query: String = "",
) {
    val isEmpty: Boolean get() = this == GameFilter()
}

/** Totals for the Overview page. */
data class GameTotals(val games: Int, val wins: Int, val draws: Int, val losses: Int) {
    /** Share of points scored, 0 to 1 (a draw is half a point); 0 with no finished games. */
    val score: Float
        get() {
            val finished = wins + draws + losses
            return if (finished == 0) 0f else (wins + draws * 0.5f) / finished
        }
}

/**
 * Turns saved games into [GameSummary] rows and filters them. Pure, so it can run on a
 * background thread over a few thousand imported games.
 */
object GameLibrary {

    fun summarise(record: GameRecord, tree: OpeningTree): GameSummary {
        val startFen = startFenOf(record.pgn)
        val moves = record.uciMoves.split(' ').filter { it.isNotBlank() }
        // Opening names only mean something from the normal start.
        val match = if (startFen == null && moves.isNotEmpty() && !tree.isEmpty) tree.match(moves) else null
        val opening = match?.opening
        return GameSummary(
            record = record,
            outcome = outcomeOf(record),
            userColor = if (record.userColor.equals("black", ignoreCase = true)) PieceColor.BLACK else PieceColor.WHITE,
            timeClass = TimeClass.of(record.timeClass),
            opening = opening,
            openingName = opening?.name ?: if (startFen == null) pgnTag(record.pgn, "Opening") else null,
            leftBookAt = match?.leftBookAt,
            startFen = startFen,
        )
    }

    fun summarise(records: List<GameRecord>, tree: OpeningTree): List<GameSummary> =
        records.map { summarise(it, tree) }.sortedByDescending { it.dateMillis }

    fun outcomeOf(record: GameRecord): GameOutcome {
        val white = !record.userColor.equals("black", ignoreCase = true)
        return when (record.result.trim()) {
            "1-0" -> if (white) GameOutcome.WIN else GameOutcome.LOSS
            "0-1" -> if (white) GameOutcome.LOSS else GameOutcome.WIN
            "1/2-1/2", "½-½" -> GameOutcome.DRAW
            else -> GameOutcome.UNKNOWN
        }
    }

    fun filter(games: List<GameSummary>, filter: GameFilter): List<GameSummary> {
        if (filter.isEmpty) return games
        val words = filter.query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return games.filter { game ->
            (filter.source == null || game.source == filter.source) &&
                (filter.outcome == null || game.outcome == filter.outcome) &&
                (filter.color == null || game.userColor == filter.color) &&
                (filter.timeClass == null || game.timeClass == filter.timeClass) &&
                (filter.openingFamily == null || game.openingFamily == filter.openingFamily) &&
                (words.isEmpty() || searchText(game).let { text -> words.all(text::contains) })
        }
    }

    /** Time classes that appear in [games], in the enum's order, so chips never show an empty filter. */
    fun timeClasses(games: List<GameSummary>): List<TimeClass> {
        val present = games.mapTo(HashSet()) { it.timeClass }
        return TimeClass.entries.filter { it in present }
    }

    /** Sources that appear in [games]. */
    fun sources(games: List<GameSummary>): List<GameSource> {
        val present = games.mapTo(HashSet()) { it.source }
        return GameSource.entries.filter { it in present }
    }

    /** The opening families played most, most games first, for the opening chips. */
    fun topOpenings(games: List<GameSummary>, limit: Int = 8): List<String> =
        games.mapNotNull { it.openingFamily }
            .groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key }

    fun totals(games: List<GameSummary>): GameTotals = GameTotals(
        games = games.size,
        wins = games.count { it.outcome == GameOutcome.WIN },
        draws = games.count { it.outcome == GameOutcome.DRAW },
        losses = games.count { it.outcome == GameOutcome.LOSS },
    )

    /** The FEN a game was set up from (PGN SetUp and FEN tags), or null for the normal start. */
    fun startFenOf(pgn: String): String? {
        val fen = pgnTag(pgn, "FEN") ?: return null
        return fen.takeUnless { it.startsWith(NORMAL_START) }
    }

    /** The value of one PGN header tag, for example `[Opening "Italian Game"]`. */
    fun pgnTag(pgn: String, name: String): String? =
        Regex("""\[$name\s+"([^"]*)"\]""").find(pgn)?.groupValues?.get(1)?.trim()?.ifBlank { null }

    private fun searchText(game: GameSummary): String = buildString {
        append(game.opponent).append(' ')
        append(game.openingName.orEmpty()).append(' ')
        append(game.opening?.eco.orEmpty()).append(' ')
        append(game.source.label).append(' ')
        append(game.timeClass.label)
    }.lowercase()

    private const val NORMAL_START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq"
}
