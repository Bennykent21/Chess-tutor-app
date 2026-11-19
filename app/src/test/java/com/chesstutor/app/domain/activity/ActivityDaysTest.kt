package com.chesstutor.app.domain.activity

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.domain.learn.LessonProgress
import com.chesstutor.app.domain.puzzle.PuzzleHistoryEntry
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/** One definition of a day trained in the app, for the streak and Home's week (D110). */
class ActivityDaysTest {

    private fun at(day: LocalDate) = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun game(id: String, at: Long) = GameRecord(id, at, "Pip", 800, "1-0", "", 20, "white", "")

    @Test
    fun theStreakCountsConsecutiveDaysAndSurvivesUntilMidnight() {
        val today = LocalDate.of(2026, 10, 22)
        val days = setOf(today.minusDays(1), today.minusDays(2), today.minusDays(3), today.minusDays(5))
        assertEquals("not played yet today: yesterday's streak still stands", 3, ActivityDays.streak(days, today))
        assertEquals(4, ActivityDays.streak(days + today, today))
        assertEquals("a missed day breaks it", 0, ActivityDays.streak(setOf(today.minusDays(2)), today))
        assertEquals(0, ActivityDays.streak(emptySet(), today))
    }

    @Test
    fun theWeekRunsMondayToSunday() {
        val wednesday = LocalDate.of(2026, 11, 18)
        // Monday 16 and Wednesday 18; Sunday 15 belongs to last week.
        val days = setOf(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 16), wednesday)
        assertEquals(listOf(true, false, true, false, false, false, false), ActivityDays.week(days, wednesday))
    }

    @Test
    fun everyKindOfTrainingCountsButNotGamesPlayedElsewhere() {
        val monday = LocalDate.of(2026, 11, 16)
        val days = ActivityDays.of(
            puzzles = listOf(
                PuzzleHistoryEntry("a", solved = true, clean = true, ratingAfter = 1000, finishedAt = at(monday)),
                PuzzleHistoryEntry("b", solved = false, clean = false, ratingAfter = 990, finishedAt = at(monday.plusDays(4))),
            ),
            games = listOf(
                game("bot_pip_1", at(monday.plusDays(1))),
                game("chesscom_42", at(monday.plusDays(5))),
            ),
            lessons = listOf(LessonProgress("l1", completed = true, completedAt = at(monday.plusDays(2))), LessonProgress("l2", stepIndex = 3)),
            drillTimes = listOf(at(monday.plusDays(3))),
            zone = ZoneOffset.UTC,
        )
        assertEquals("a failed puzzle and an imported game are not days trained here", (0L..3L).map { monday.plusDays(it) }.toSet(), days)
        val thursday = monday.plusDays(3)
        assertEquals("the streak and the week agree", 4, ActivityDays.streak(days, thursday))
        assertEquals(listOf(true, true, true, true, false, false, false), ActivityDays.week(days, thursday))
    }
}
