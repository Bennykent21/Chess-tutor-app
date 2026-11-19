package com.chesstutor.app.domain.puzzle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the player does at each puzzle goal (D95). */
class GoalStatsTest {

    private fun puzzle(id: String, theme: String) = Puzzle(id = id, fen = "8/8/8/8/8/8/8/K6k w - - 0 1", moves = listOf("a1a2", "h1h2"), rating = 1200, primaryTheme = theme, themes = setOf(theme))

    private fun played(id: String, clean: Boolean) = PuzzleHistoryEntry(puzzleId = id, solved = true, clean = clean, ratingAfter = 1200, finishedAt = 0)

    @Test
    fun eachGoalCountsItsPuzzlesAndFirstTries() {
        val pack = PuzzlePack(listOf(puzzle("a", "mateIn2"), puzzle("b", "mateIn1"), puzzle("c", "fork")))
        val history = listOf(played("a", clean = true), played("b", clean = false), played("c", clean = true), played("gone", clean = true))
        val stats = GoalStats.of(history, pack)
        assertEquals(GoalStats(played = 2, firstTry = 1), stats[PuzzleGoal.of(pack.get("a")!!)])
        assertEquals(50, stats.getValue(PuzzleGoal.of(pack.get("a")!!)).firstTryPercent)
        assertNull(GoalStats(0, 0).firstTryPercent)
        assertTrue("no pack yet, no numbers", GoalStats.of(history, null).isEmpty())
    }
}
