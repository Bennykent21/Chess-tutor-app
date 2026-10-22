package com.chesstutor.app.domain.puzzle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PuzzleGoalTest {

    private fun puzzle(primary: String, vararg themes: String) =
        Puzzle("p", "8/8/8/8/8/8/8/K6k w - - 0 1", listOf("a1a2", "h1h2"), 1000, primary, themes.toSet() + primary)

    @Test
    fun matesAreCheckmateWhateverTheirPattern() {
        assertEquals(PuzzleGoal.CHECKMATE, PuzzleGoal.of(puzzle("mateIn2", "short")))
        assertEquals(PuzzleGoal.CHECKMATE, PuzzleGoal.of(puzzle("backRankMate", "mate", "mateIn1")))
        assertEquals(PuzzleGoal.CHECKMATE, PuzzleGoal.of(puzzle("fork", "mate")))
    }

    @Test
    fun defenceEndgameAndMaterialAreTellApart() {
        assertEquals(PuzzleGoal.DEFEND, PuzzleGoal.of(puzzle("defensiveMove", "equality")))
        assertEquals(PuzzleGoal.ENDGAME, PuzzleGoal.of(puzzle("rookEndgame", "endgame", "crushing")))
        assertEquals(PuzzleGoal.WIN_MATERIAL, PuzzleGoal.of(puzzle("fork", "middlegame", "advantage")))
    }

    @Test
    fun promptsNameTheGoalNeverThePattern() {
        assertEquals("Find the checkmate in one move", PuzzleGoal.prompt(puzzle("mateIn1")))
        assertEquals("Find mate in 3", PuzzleGoal.prompt(puzzle("smotheredMate", "mateIn3")))
        assertEquals("Can you win material?", PuzzleGoal.prompt(puzzle("skewer")))
        val forkPrompt = PuzzleGoal.prompt(puzzle("fork"))
        assertFalse("the theme would spoil the answer", forkPrompt.contains("fork", ignoreCase = true))
    }
}
