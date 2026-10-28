package com.chesstutor.app.domain.account

import com.chesstutor.app.core.PieceColor

/** Wins, draws and losses for a group of games. */
data class Wdl(val wins: Int = 0, val draws: Int = 0, val losses: Int = 0) {
    val games: Int get() = wins + draws + losses

    /** Points scored, 0 to 1 (a draw is half a point); 0 with no games. */
    val score: Float get() = if (games == 0) 0f else (wins + draws * 0.5f) / games

    operator fun plus(outcome: GameOutcome): Wdl = when (outcome) {
        GameOutcome.WIN -> copy(wins = wins + 1)
        GameOutcome.DRAW -> copy(draws = draws + 1)
        GameOutcome.LOSS -> copy(losses = losses + 1)
        GameOutcome.UNKNOWN -> this
    }
}

/** One bar of the dashboard; [filter] opens the games behind it. */
data class WdlRow(val label: String, val wdl: Wdl, val filter: GameFilter)

/** The kinds of costly moves the dashboard counts (plan 6.4, "common mistake types"). */
enum class MistakeKind(val label: String, val description: String) {
    THREW_WIN("Let a win slip", "You were clearly winning, then a move made it close or worse."),
    ALLOWED_MATE("Walked into mate", "A move let the opponent force checkmate."),
    MISSED_MATE("Missed a mate", "You had a forced mate and played something else."),
    OTHER_BLUNDER("Other blunders", "Big losses that fit none of the above, often loose pieces."),
    MISTAKE("Mistakes", "Smaller slips that still cost a clear part of your chances."),
}

/** The player's accuracy and costly moves in one phase of the game. */
data class PhaseStats(val phase: GamePhase, val accuracy: Double?, val moves: Int, val errors: Int)

/** One point of the rating trend. */
data class RatingPoint(val dateMillis: Long, val rating: Int)

/** Everything on the Performance page (plan 6.4), from the player's games. */
data class PerformanceReport(
    val overall: Wdl,
    val byColor: List<WdlRow>,
    val byTimeClass: List<WdlRow>,
    val byOpening: List<WdlRow>,
    /** How many of the games have been analysed (accuracy and mistakes come from these). */
    val analysedGames: Int,
    val phases: List<PhaseStats>,
    val mistakes: Map<MistakeKind, Int>,
    /** The time class with the most rated games, and the player's rating in it over time. */
    val ratingTimeClass: TimeClass?,
    val ratingTrend: List<RatingPoint>,
) {
    val isEmpty: Boolean get() = overall.games == 0
}

object PerformanceCalculator {

    /** Openings shown on the dashboard, most played first. */
    const val OPENING_ROWS = 8

    /**
     * Builds the report from [games] (already filtered by the page's chips, [base] being that
     * filter) and the [reviews] of those that were analysed.
     */
    fun report(games: List<GameSummary>, reviews: Map<String, GameReview>, base: GameFilter = GameFilter()): PerformanceReport {
        val finished = games.filter { it.outcome != GameOutcome.UNKNOWN }
        fun wdl(list: List<GameSummary>) = list.fold(Wdl()) { acc, g -> acc + g.outcome }

        val byColor = listOf(PieceColor.WHITE to "As White", PieceColor.BLACK to "As Black").map { (color, label) ->
            WdlRow(label, wdl(finished.filter { it.userColor == color }), base.copy(color = color))
        }.filter { it.wdl.games > 0 }
        val byTime = TimeClass.entries.mapNotNull { time ->
            val list = finished.filter { it.timeClass == time }
            if (list.isEmpty()) null else WdlRow(time.label, wdl(list), base.copy(timeClass = time))
        }
        val byOpening = finished.filter { it.openingFamily != null }
            .groupBy { it.openingFamily!! }
            .entries.sortedWith(compareByDescending<Map.Entry<String, List<GameSummary>>> { it.value.size }.thenBy { it.key })
            .take(OPENING_ROWS)
            .map { (family, list) -> WdlRow(family, wdl(list), base.copy(openingFamily = family)) }

        val analysed = games.mapNotNull { game -> reviews[game.id]?.let { game to it } }
        val mine = analysed.flatMap { (game, review) -> review.moves.filter { it.mover == game.userColor } }
        val phases = GamePhase.entries.map { phase ->
            val moves = mine.filter { it.phase == phase }
            PhaseStats(
                phase = phase,
                accuracy = GameReviewer.accuracyOf(moves.map { it.accuracy }),
                moves = moves.size,
                errors = moves.count { it.quality == MoveQuality.MISTAKE || it.quality == MoveQuality.BLUNDER },
            )
        }
        val mistakes = mine.mapNotNull(::kindOf).groupingBy { it }.eachCount()

        val rated = games.filter { it.playerRating != null }
        val ratingClass = rated.groupingBy { it.timeClass }.eachCount().maxByOrNull { it.value }?.key
        val trend = rated.filter { it.timeClass == ratingClass }
            .sortedBy { it.dateMillis }
            .map { RatingPoint(it.dateMillis, it.playerRating!!) }

        return PerformanceReport(
            overall = wdl(finished),
            byColor = byColor,
            byTimeClass = byTime,
            byOpening = byOpening,
            analysedGames = analysed.size,
            phases = phases,
            mistakes = mistakes,
            ratingTimeClass = ratingClass,
            ratingTrend = trend,
        )
    }

    /**
     * The openings the player does worst in, marked on Account > Stats: at most [WEAK_ROWS]
     * rows scoring under half the points, each from at least [MIN_WEAK_GAMES] games.
     */
    fun weakest(rows: List<WdlRow>): Set<String> =
        rows.filter { it.wdl.games >= MIN_WEAK_GAMES && it.wdl.score < 0.5f }
            .sortedWith(compareBy<WdlRow> { it.wdl.score }.thenByDescending { it.wdl.games })
            .take(WEAK_ROWS)
            .mapTo(HashSet()) { it.label }

    const val WEAK_ROWS = 2
    const val MIN_WEAK_GAMES = 3

    /** What kind of costly move [move] was, or null if it was not a mistake or blunder. */
    fun kindOf(move: ReviewedMove): MistakeKind? {
        if (move.quality != MoveQuality.MISTAKE && move.quality != MoveQuality.BLUNDER) return null
        return when {
            // Winning chances are exactly 0 or 100 only with a forced mate on the board.
            move.winAfter == 0.0 && move.winBefore > 0.0 -> MistakeKind.ALLOWED_MATE
            move.winBefore == 100.0 -> MistakeKind.MISSED_MATE
            move.winBefore >= WINNING && move.winAfter <= UNCLEAR -> MistakeKind.THREW_WIN
            move.quality == MoveQuality.BLUNDER -> MistakeKind.OTHER_BLUNDER
            else -> MistakeKind.MISTAKE
        }
    }

    /** Winning chances that count as clearly winning, and as no longer winning. */
    private const val WINNING = 75.0
    private const val UNCLEAR = 60.0
}
