package com.chesstutor.app.domain.account

/**
 * What the player's reviewed games of one clock say (D104): where their losses turned, why they
 * were lost, and how their accuracy has gone.
 */
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
)

object GameInsights {
    /** The player's winning chances (0 to 100) that count as a winning position let slip. */
    const val WINNING = 80.0

    /** Each clock's insight, from the games that have a review. */
    fun of(games: List<GameSummary>, reviews: Map<String, GameReview>): Map<TimeClass, ClockInsight> =
        games.filter { it.id in reviews }.groupBy { it.timeClass }.mapValues { (_, mine) -> clock(mine, reviews) }

    fun clock(games: List<GameSummary>, reviews: Map<String, GameReview>): ClockInsight {
        val byDate = games.filter { it.id in reviews }.sortedBy { it.dateMillis }
        val losses = byDate.filter { it.outcome == GameOutcome.LOSS }
        val costliest = losses.mapNotNull { game ->
            reviews.getValue(game.id).moves.filter { it.mover == game.userColor }.maxByOrNull { it.loss }?.takeIf { it.loss > 0 }
        }
        return ClockInsight(
            reviewed = byDate.size,
            losses = losses.size,
            turned = costliest.groupingBy { it.phase }.eachCount(),
            blunders = costliest.count { it.quality == MoveQuality.BLUNDER },
            slipped = losses.count { game -> reviews.getValue(game.id).moves.any { it.mover == game.userColor && it.winBefore >= WINNING } },
            accuracy = byDate.mapNotNull { game -> reviews.getValue(game.id).report(game.userColor).accuracy },
        )
    }
}
