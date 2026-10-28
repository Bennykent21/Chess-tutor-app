package com.chesstutor.app.domain.account

import com.chesstutor.app.data.model.GameSource
import com.chesstutor.app.domain.puzzle.PuzzleHistoryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What a badge is for, which sets its colour and icon. */
enum class BadgeKind { GAMES, PUZZLES, LEARNING, REVIEW }

/**
 * One badge (plan 5.3 and 6.4): earned by learning and playing, never by tapping around.
 * Every badge is worked out from what is already saved, so none can be lost again.
 */
data class Badge(val id: String, val title: String, val detail: String, val kind: BadgeKind, val progress: Int, val goal: Int) {
    val earned: Boolean get() = progress >= goal
}

/** What the badges and the activity list read: games, analyses, puzzles and lessons. */
data class AchievementInputs(
    val games: List<GameSummary> = emptyList(),
    val analysed: Map<String, AnalysisSummary> = emptyMap(),
    val reviews: Map<String, GameReview> = emptyMap(),
    val puzzles: List<PuzzleHistoryEntry> = emptyList(),
    /** The player level from XP (Puzzles), which only ever rises. */
    val playerLevel: Int = 1,
    /** Finished lessons with when they were finished, and every lesson's title. */
    val lessonsFinished: Map<String, Long?> = emptyMap(),
    val lessonTitles: Map<String, String> = emptyMap(),
)

object Achievements {

    /** Every badge, earned ones first, then by how close the player is. */
    fun badges(inputs: AchievementInputs, zone: ZoneId = ZoneId.systemDefault()): List<Badge> {
        val botGames = inputs.games.filter { it.source == GameSource.ARENA && it.id.startsWith("bot_") }
        val botWins = botGames.filter { it.outcome == GameOutcome.WIN }
        val solved = inputs.puzzles.count { it.solved }
        val bestCleanRun = longestRun(inputs.puzzles.map { it.clean })
        val dayRun = longestDayRun(inputs.puzzles.filter { it.solved }.map { it.finishedAt }, zone)
        val lessons = inputs.lessonsFinished.size
        val lessonTotal = inputs.lessonTitles.size.coerceAtLeast(1)
        val bestAccuracy = inputs.games.mapNotNull { g -> inputs.analysed[g.id]?.accuracyFor(g.userColor) }.maxOrNull() ?: 0.0
        val brilliant = inputs.games.count { g ->
            inputs.reviews[g.id]?.moves?.any { it.mover == g.userColor && it.quality == MoveQuality.BRILLIANT } == true
        }
        fun beat(rating: Int) = if (botWins.any { it.opponentRating >= rating }) 1 else 0
        val all = listOf(
            Badge("first-win", "First win", "Beat a bot", BadgeKind.GAMES, botWins.size.coerceAtMost(1), 1),
            Badge("giant-slayer", "Giant slayer", "Beat a bot rated 1600 or more", BadgeKind.GAMES, beat(1600), 1),
            Badge("bot-master", "Bot master", "Beat a bot rated 2000 or more", BadgeKind.GAMES, beat(2000), 1),
            Badge("regular", "Regular", "Play 25 games against the bots", BadgeKind.GAMES, botGames.size, 25),
            Badge("puzzles-10", "Puzzle starter", "Solve 10 puzzles", BadgeKind.PUZZLES, solved, 10),
            Badge("puzzles-100", "Puzzle fan", "Solve 100 puzzles", BadgeKind.PUZZLES, solved, 100),
            Badge("puzzles-500", "Puzzle master", "Solve 500 puzzles", BadgeKind.PUZZLES, solved, 500),
            Badge("hot-hand", "Hot hand", "Solve 10 puzzles in a row without a slip", BadgeKind.PUZZLES, bestCleanRun, 10),
            Badge("week", "Seven days", "Solve a puzzle 7 days in a row", BadgeKind.PUZZLES, dayRun, 7),
            Badge("level-10", "Level 10", "Reach level 10", BadgeKind.PUZZLES, inputs.playerLevel, 10),
            Badge("first-lesson", "First lesson", "Finish a lesson", BadgeKind.LEARNING, lessons.coerceAtMost(1), 1),
            Badge("ten-lessons", "Keen learner", "Finish 10 lessons", BadgeKind.LEARNING, lessons, 10),
            Badge("all-lessons", "Graduate", "Finish every lesson", BadgeKind.LEARNING, lessons, lessonTotal),
            Badge("first-review", "Under the microscope", "Review a game with Stockfish", BadgeKind.REVIEW, inputs.analysed.size.coerceAtMost(1), 1),
            Badge("sharp", "Sharp", "Play a game with 90% accuracy or more", BadgeKind.REVIEW, if (bestAccuracy >= 90.0) 1 else 0, 1),
            Badge("brilliant", "Brilliant!", "Play a brilliant move in a reviewed game", BadgeKind.REVIEW, brilliant.coerceAtMost(1), 1),
        )
        return all.sortedWith(compareByDescending<Badge> { it.earned }.thenByDescending { it.progress.toFloat() / it.goal })
    }

    /** The longest run of true values in a row. */
    internal fun longestRun(values: List<Boolean>): Int {
        var best = 0
        var run = 0
        values.forEach { if (it) { run++; best = maxOf(best, run) } else run = 0 }
        return best
    }

    /** The most calendar days in a row with at least one of [times]. */
    internal fun longestDayRun(times: List<Long>, zone: ZoneId): Int {
        val days = times.map { day(it, zone) }.distinct().sorted()
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        days.forEach { d ->
            run = if (previous?.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run)
            previous = d
        }
        return best
    }

    private fun day(at: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
}
