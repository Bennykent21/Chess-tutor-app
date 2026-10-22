package com.chesstutor.app.domain.puzzle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

class PuzzleModesTest {

    private val themeCycle = listOf(
        setOf("mateIn2", "mate"), setOf("fork"), setOf("defensiveMove", "equality"),
        setOf("rookEndgame", "endgame"), setOf("pin"), setOf("backRankMate", "mate", "mateIn1"),
    )

    private val pack = PuzzlePack((400..2600 step 10).mapIndexed { i, rating ->
        val themes = themeCycle[i % themeCycle.size]
        Puzzle("p$rating", "8/8/8/8/8/8/8/K6k w - - 0 1", listOf("a1a2", "h1h2"), rating, themes.first(), themes)
    })

    @Test
    fun theDailyPuzzleIsTheSameAllDayAndChangesDayToDay() {
        val day = LocalDate.of(2026, 10, 22)
        val today = PuzzleModes.daily(pack, day)!!
        assertEquals(today, PuzzleModes.daily(pack, day))
        assertTrue(today.rating in 1000..1800)
        val week = (0L..6L).map { PuzzleModes.daily(pack, day.plusDays(it))!!.id }
        assertTrue("a week brings different puzzles", week.toSet().size >= 5)
        assertNull(PuzzleModes.daily(PuzzlePack.EMPTY, day))
    }

    @Test
    fun patternsBelongToTheirGoal() {
        // Tags without a plain-language name, such as "mate" and "equality", are left out.
        assertEquals(setOf("backRankMate", "mateIn1", "mateIn2"), PuzzleModes.patternsFor(PuzzleGoal.CHECKMATE, pack).toSet())
        assertEquals(setOf("fork", "pin"), PuzzleModes.patternsFor(PuzzleGoal.WIN_MATERIAL, pack).toSet())
        assertEquals(listOf("defensiveMove"), PuzzleModes.patternsFor(PuzzleGoal.DEFEND, pack))
    }

    @Test
    fun themedRunsMatchTheGoalPatternAndRating() {
        val run = PuzzleModes.themed(pack, PuzzleGoal.WIN_MATERIAL, pattern = "fork", rating = 1500, count = 10, seed = 1)
        assertEquals(10, run.size)
        assertTrue(run.all { "fork" in it.themes })
        // Forks are one puzzle in six here, so the search widens until it has a choice of 20.
        assertTrue(run.all { abs(it.rating - 1500) <= 800 })
        assertEquals(10, run.map { it.id }.toSet().size)
        val endgames = PuzzleModes.themed(pack, PuzzleGoal.ENDGAME, null, 900, 5, seed = 2)
        assertTrue(endgames.all { PuzzleGoal.of(it) == PuzzleGoal.ENDGAME })
    }

    @Test
    fun customRunsStayInTheirBand() {
        val run = PuzzleModes.custom(pack, RatingBand.CLUB, pattern = null, count = 20, seed = 3)
        assertEquals(20, run.size)
        assertTrue(run.all { it.rating in RatingBand.CLUB.range })
        assertNotEquals(run, PuzzleModes.custom(pack, RatingBand.CLUB, null, 20, seed = 4))
    }

    @Test
    fun mistakesKeepTheirOrderAndSkipMissingPuzzles() {
        val run = PuzzleModes.mistakes(pack, listOf("p900", "gone", "p1200"))
        assertEquals(listOf("p900", "p1200"), run.map { it.id })
    }

    @Test
    fun rushGetsHarderAndNeverRepeats() {
        val rush = RushSelector(pack, startRating = RushSelector.startFor(1000), seed = 5)
        val served = (0 until 20).map { solved -> rush.next(solved)!! }
        assertEquals(20, served.map { it.id }.toSet().size)
        assertTrue("climbs with each solve", served.last().rating > served.first().rating + 600)
        assertEquals(700, RushSelector.startFor(1000))
        assertEquals(PuzzleRating.MIN, RushSelector.startFor(500))
    }
}
