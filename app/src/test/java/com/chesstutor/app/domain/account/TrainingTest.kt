package com.chesstutor.app.domain.account

import com.chesstutor.app.domain.puzzle.PuzzleHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingTest {

    private fun entry(id: String, clean: Boolean, rating: Int, at: Long) = PuzzleHistoryEntry(id, solved = true, clean = clean, ratingAfter = rating, finishedAt = at)

    private val themes = mapOf(
        "f1" to setOf("fork", "short"), "f2" to setOf("fork"), "f3" to setOf("fork"),
        "p1" to setOf("pin"), "p2" to setOf("pin"), "p3" to setOf("pin", "crushing"),
        "s1" to setOf("skewer"), "s2" to setOf("skewer"),
    )

    @Test
    fun strengthsWeaknessesAndRatingHistory() {
        val history = listOf(
            entry("f1", true, 1010, 1), entry("f2", true, 1020, 2), entry("f3", true, 1030, 3),
            entry("p1", false, 1020, 4), entry("p2", false, 1010, 5), entry("p3", true, 1020, 6),
            entry("s1", false, 1010, 7), entry("s2", false, 1000, 8),
            entry("gone", true, 1010, 9),
        )
        val report = TrainingCalculator.report(history, themes::get, lessonsDone = 5, lessonsTotal = 48)
        assertEquals(9, report.puzzlesPlayed)
        assertEquals(5, report.cleanSolves)
        assertEquals(listOf(1010, 1020, 1030, 1020, 1010, 1020, 1010, 1000, 1010), report.ratingHistory.map { it.rating })
        assertEquals(listOf("fork"), report.strengths.map { it.theme })
        assertEquals("Fork", report.strengths.single().name)
        assertEquals(1f, report.strengths.single().rate, 0.001f)
        assertEquals("a pin record of 1 in 3 is a weakness; skewer has too few puzzles", listOf("pin"), report.weaknesses.map { it.theme })
        assertTrue("noise tags like short and crushing never count", (report.strengths + report.weaknesses).none { it.theme == "short" || it.theme == "crushing" })
        assertEquals(5, report.lessonsDone)
        assertEquals(48, report.lessonsTotal)
    }

    @Test
    fun anEmptyHistoryIsCalm() {
        val report = TrainingCalculator.report(emptyList(), { null }, 0, 0)
        assertEquals(0f, report.cleanRate)
        assertTrue(report.strengths.isEmpty() && report.weaknesses.isEmpty())
    }
}
