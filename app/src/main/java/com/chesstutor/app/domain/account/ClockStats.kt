package com.chesstutor.app.domain.account

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.domain.puzzle.PuzzleStats

/** How a finished game came to an end. */
enum class GameEnding(val label: String) {
    CHECKMATE("Checkmate"),
    RESIGNATION("Resignation"),
    TIME("Time"),
    OTHER("Other"),
    ;

    companion object {
        /** The ending from a game's termination ("checkmate", "resigned", "timeout"...). */
        fun of(termination: String): GameEnding = when (termination.trim().lowercase()) {
            "checkmate" -> CHECKMATE
            "resigned" -> RESIGNATION
            "timeout" -> TIME
            else -> OTHER
        }
    }
}

/** A clock's records: where the rating is and has been, the best win and the best run. */
data class ClockRecords(
    val current: Int?,
    val peak: Int?,
    /** The strongest player beaten, or null before any win. */
    val bestWin: GameSummary?,
    val longestWinStreak: Int,
    val averageOpponent: Int?,
)

/** Everything Stats shows for one clock (D60). */
data class ClockReport(
    val clock: TimeClass,
    val games: Int,
    val results: Wdl,
    val records: ClockRecords,
    val trend: RatingTrend,
    /** Wins and losses by how they ended; draws are counted in [results]. */
    val wonBy: Map<GameEnding, Int>,
    val lostBy: Map<GameEnding, Int>,
    /** Full moves in an average game, or null with no games. */
    val averageMoves: Int?,
    val asWhite: List<WdlRow>,
    val asBlack: List<WdlRow>,
)

object ClockStats {

    /** The clocks the player has finished games in, the three main ones first. */
    fun clocksPlayed(games: List<GameSummary>): List<TimeClass> {
        val present = games.filter { it.outcome != GameOutcome.UNKNOWN }.mapTo(HashSet()) { it.timeClass }
        return ORDER.filter { it in present }
    }

    /** [games] narrowed to [clock], with its records, endings and openings by colour. [current] is the linked site's rating. */
    fun report(games: List<GameSummary>, clock: TimeClass, current: Int? = null): ClockReport {
        val mine = games.filter { it.timeClass == clock }
        val finished = mine.filter { it.outcome != GameOutcome.UNKNOWN }
        val byDate = finished.sortedBy { it.dateMillis }
        val rated = byDate.mapNotNull { it.playerRating }
        val wins = finished.filter { it.outcome == GameOutcome.WIN }
        val base = GameFilter(timeClass = clock)
        fun openings(color: PieceColor) =
            PerformanceCalculator.report(finished.filter { it.userColor == color }, emptyMap(), base.copy(color = color)).byOpening
        return ClockReport(
            clock = clock,
            games = finished.size,
            results = finished.fold(Wdl()) { acc, g -> acc + g.outcome },
            records = ClockRecords(
                current = current ?: rated.lastOrNull(),
                peak = (rated + listOfNotNull(current)).maxOrNull(),
                bestWin = wins.maxByOrNull { it.opponentRating },
                longestWinStreak = longestRun(byDate.map { it.outcome == GameOutcome.WIN }),
                averageOpponent = finished.map { it.opponentRating }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            ),
            trend = RatingTrend(clock, byDate.filter { it.playerRating != null }.map { RatingPoint(it.dateMillis, it.playerRating!!) }),
            wonBy = endings(wins),
            lostBy = endings(finished.filter { it.outcome == GameOutcome.LOSS }),
            averageMoves = finished.map { it.moveNumber }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            asWhite = openings(PieceColor.WHITE),
            asBlack = openings(PieceColor.BLACK),
        )
    }

    private fun endings(games: List<GameSummary>): Map<GameEnding, Int> =
        games.groupingBy { GameEnding.of(it.record.termination) }.eachCount()

    /** The longest run of true values in a row. */
    internal fun longestRun(values: List<Boolean>): Int {
        var best = 0
        var run = 0
        values.forEach { if (it) { run++; best = maxOf(best, run) } else run = 0 }
        return best
    }

    private val ORDER = TimeClass.entries
}

/** A puzzle player's records, for Training (D60). */
data class PuzzleRecords(
    val current: Int,
    val peak: Int,
    val solved: Int,
    /** Share of puzzles solved without a slip, 0 to 1, or null before any. */
    val cleanRate: Float?,
    val bestCleanRun: Int,
    val dayStreak: Int,
    /** Puzzles played in the last seven days. */
    val thisWeek: Int = 0,
) {
    companion object {
        private const val WEEK_MILLIS = 7 * 86_400_000L

        fun of(stats: PuzzleStats, training: TrainingReport, now: Long): PuzzleRecords = PuzzleRecords(
            current = stats.rating,
            peak = maxOf(stats.rating, training.ratingHistory.maxOfOrNull { it.rating } ?: stats.rating),
            solved = stats.solved,
            cleanRate = training.cleanSolves.toFloat().takeIf { training.puzzlesPlayed > 0 }?.div(training.puzzlesPlayed),
            bestCleanRun = stats.bestStreak,
            dayStreak = stats.dayStreak,
            thisWeek = training.ratingHistory.count { it.dateMillis > now - WEEK_MILLIS },
        )
    }
}
