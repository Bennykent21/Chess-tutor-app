package com.chesstutor.app.domain.account

import java.time.Instant
import java.time.ZoneId

/**
 * What one reviewed game says about the player (D104, D106), kept small so Stats can narrow the
 * games to a clock and a period and sum them again.
 */
data class ReviewedGame(
    val dateMillis: Long,
    val outcome: GameOutcome,
    val accuracy: Double?,
    /** The player's average move accuracy in each phase the game reached. */
    val phaseAccuracy: Map<GamePhase, Double>,
    /** For a loss: the phase of the player's costliest move, and whether it was a blunder. */
    val turnedIn: GamePhase?,
    val decidedByBlunder: Boolean,
    /** The player was clearly winning at some point, or clearly lost at some point. */
    val wasWinning: Boolean,
    val wasLost: Boolean,
)

/** What the reviewed games of one clock and period say (D104, D106). */
data class ClockInsight(
    val reviewed: Int,
    val losses: Int,
    /** Reviewed losses by the phase of the player's costliest move. */
    val turned: Map<GamePhase, Int>,
    /** Reviewed losses where that costliest move was a blunder. */
    val blunders: Int,
    /** Reviewed losses where the player was winning at some point. */
    val slipped: Int,
    /** The player's accuracy in each reviewed game, oldest first. */
    val accuracy: List<Double>,
    /** The player's average accuracy in each phase. */
    val phaseAccuracy: Map<GamePhase, Double> = emptyMap(),
    /** Games that reached a winning position, and how many of those were won. */
    val winning: Int = 0,
    val converted: Int = 0,
    /** Games that reached a lost position, and how many of those were not lost. */
    val lost: Int = 0,
    val saved: Int = 0,
)

/** Results against players rated lower, about the same, and higher (within [RatingBand.MARGIN]). */
enum class RatingBand(val label: String) {
    LOWER("Lower rated"),
    SIMILAR("Similar"),
    HIGHER("Higher rated"),
    ;

    companion object {
        const val MARGIN = 100
    }
}

/** The parts of the day, for when the player plays best. */
enum class DayPart(val label: String) {
    MORNING("Morning"),
    AFTERNOON("Afternoon"),
    EVENING("Evening"),
    NIGHT("Night"),
    ;

    companion object {
        fun of(hour: Int): DayPart = when (hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..21 -> EVENING
            else -> NIGHT
        }
    }
}

object GameInsights {
    /** The player's winning chances (0 to 100) that count as clearly winning; 100 minus it is clearly lost. */
    const val WINNING = 80.0

    /** Each clock's reviewed games, oldest first. */
    fun of(games: List<GameSummary>, reviews: Map<String, GameReview>): Map<TimeClass, List<ReviewedGame>> =
        games.filter { it.id in reviews }.groupBy { it.timeClass }
            .mapValues { (_, mine) -> mine.sortedBy { it.dateMillis }.map { game -> reviewed(game, reviews.getValue(game.id)) } }

    fun reviewed(game: GameSummary, review: GameReview): ReviewedGame {
        val mine = review.moves.filter { it.mover == game.userColor }
        val costliest = mine.maxByOrNull { it.loss }?.takeIf { it.loss > 0 && game.outcome == GameOutcome.LOSS }
        return ReviewedGame(
            dateMillis = game.dateMillis,
            outcome = game.outcome,
            accuracy = review.report(game.userColor).accuracy,
            phaseAccuracy = mine.groupBy { it.phase }.mapValues { (_, moves) -> moves.map { it.accuracy }.average() },
            turnedIn = costliest?.phase,
            decidedByBlunder = costliest?.quality == MoveQuality.BLUNDER,
            wasWinning = mine.any { it.winBefore >= WINNING },
            wasLost = mine.any { it.winBefore <= 100 - WINNING },
        )
    }

    /** Sums [games] (one clock, any period) into what Stats shows. */
    fun summarise(games: List<ReviewedGame>): ClockInsight {
        val losses = games.filter { it.outcome == GameOutcome.LOSS }
        val winning = games.filter { it.wasWinning }
        val lost = games.filter { it.wasLost }
        return ClockInsight(
            reviewed = games.size,
            losses = losses.size,
            turned = losses.mapNotNull { it.turnedIn }.groupingBy { it }.eachCount(),
            blunders = losses.count { it.decidedByBlunder },
            slipped = losses.count { it.wasWinning },
            accuracy = games.mapNotNull { it.accuracy },
            phaseAccuracy = GamePhase.entries.mapNotNull { phase ->
                games.mapNotNull { it.phaseAccuracy[phase] }.takeIf { it.isNotEmpty() }?.let { phase to it.average() }
            }.toMap(),
            winning = winning.size,
            converted = winning.count { it.outcome == GameOutcome.WIN },
            lost = lost.size,
            saved = lost.count { it.outcome != GameOutcome.LOSS },
        )
    }

    /** One clock's insight from its games and their reviews (tests and callers that have both). */
    fun clock(games: List<GameSummary>, reviews: Map<String, GameReview>): ClockInsight =
        summarise(games.filter { it.id in reviews }.sortedBy { it.dateMillis }.map { reviewed(it, reviews.getValue(it.id)) })

    /** Results by the opponent's rating against the player's own at the time; games without both ratings are left out. */
    fun byRating(games: List<GameSummary>): Map<RatingBand, Wdl> = games
        .filter { it.outcome != GameOutcome.UNKNOWN && it.opponentRating > 0 && it.playerRating != null }
        .groupBy { game ->
            val gap = game.opponentRating - game.playerRating!!
            when {
                gap < -RatingBand.MARGIN -> RatingBand.LOWER
                gap > RatingBand.MARGIN -> RatingBand.HIGHER
                else -> RatingBand.SIMILAR
            }
        }
        .mapValues { (_, mine) -> mine.fold(Wdl()) { acc, g -> acc + g.outcome } }

    /** Results by the part of the day the game was played, in [zone]. */
    fun byDayPart(games: List<GameSummary>, zone: ZoneId = ZoneId.systemDefault()): Map<DayPart, Wdl> = games
        .filter { it.outcome != GameOutcome.UNKNOWN }
        .groupBy { DayPart.of(Instant.ofEpochMilli(it.dateMillis).atZone(zone).hour) }
        .mapValues { (_, mine) -> mine.fold(Wdl()) { acc, g -> acc + g.outcome } }
}
