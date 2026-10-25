package com.chesstutor.app.domain.account

import com.chesstutor.app.domain.puzzle.PuzzleHistoryEntry
import com.chesstutor.app.domain.puzzle.PuzzleThemes

/** How cleanly the player solves one puzzle theme. */
data class ThemeScore(val theme: String, val attempts: Int, val clean: Int) {
    val name: String get() = PuzzleThemes.displayName(theme)
    val rate: Float get() = if (attempts == 0) 0f else clean.toFloat() / attempts
}

/** Everything on the Training page (plan 6.4) apart from the live puzzle stats. */
data class TrainingReport(
    /** The puzzle rating after each puzzle, oldest first. */
    val ratingHistory: List<RatingPoint>,
    val puzzlesPlayed: Int,
    val cleanSolves: Int,
    /** Themes solved most cleanly, best first. */
    val strengths: List<ThemeScore>,
    /** Themes that trip the player up most, worst first. */
    val weaknesses: List<ThemeScore>,
    val lessonsDone: Int,
    val lessonsTotal: Int,
) {
    val cleanRate: Float get() = if (puzzlesPlayed == 0) 0f else cleanSolves.toFloat() / puzzlesPlayed
}

object TrainingCalculator {
    /** A theme needs this many puzzles before it counts as a strength or weakness. */
    const val MIN_ATTEMPTS = 3
    const val LIST_SIZE = 3

    /**
     * Builds the report. [themesOf] gives a puzzle's theme tags (from the bundled pack), or
     * null for a puzzle no longer in it; only named themes count (not "short" or "crushing").
     */
    fun report(
        history: List<PuzzleHistoryEntry>,
        themesOf: (String) -> Set<String>?,
        lessonsDone: Int,
        lessonsTotal: Int,
    ): TrainingReport {
        val attempts = HashMap<String, Int>()
        val clean = HashMap<String, Int>()
        for (entry in history) {
            themesOf(entry.puzzleId).orEmpty().filter(PuzzleThemes::isKnown).forEach { theme ->
                attempts[theme] = (attempts[theme] ?: 0) + 1
                if (entry.clean) clean[theme] = (clean[theme] ?: 0) + 1
            }
        }
        val scores = attempts.filterValues { it >= MIN_ATTEMPTS }.map { (theme, n) -> ThemeScore(theme, n, clean[theme] ?: 0) }
        val strengths = scores.filter { it.rate >= 0.5f }
            .sortedWith(compareByDescending<ThemeScore> { it.rate }.thenByDescending { it.attempts }.thenBy { it.theme })
            .take(LIST_SIZE)
        val weaknesses = scores.filter { it.rate < 0.75f && it !in strengths }
            .sortedWith(compareBy<ThemeScore> { it.rate }.thenByDescending { it.attempts }.thenBy { it.theme })
            .take(LIST_SIZE)
        return TrainingReport(
            ratingHistory = history.map { RatingPoint(it.finishedAt, it.ratingAfter) },
            puzzlesPlayed = history.size,
            cleanSolves = history.count { it.clean },
            strengths = strengths,
            weaknesses = weaknesses,
            lessonsDone = lessonsDone,
            lessonsTotal = lessonsTotal,
        )
    }
}
