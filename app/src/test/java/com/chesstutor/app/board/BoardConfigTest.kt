package com.chesstutor.app.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The table in plan section 4.2, one test per row. */
class BoardConfigTest {

    @Test
    fun playShowsTheMoveListAndOnlyWhatThePlayerSwitchesOn() {
        val plain = BoardConfig.PLAY.resolve()
        assertTrue(plain.moveList)
        assertFalse(plain.evalBar || plain.arrows || plain.clocks || plain.engineLines || plain.takebacks || plain.coachStrip)
        val everything = BoardConfig.PLAY.resolve(BoardOptions(evalBar = true, arrows = true, clocks = true, engineLines = true, takebacks = true))
        assertTrue(everything.evalBar && everything.arrows && everything.clocks && everything.engineLines && everything.takebacks)
        assertFalse(everything.coachStrip)
    }

    @Test
    fun lessonsTeachWithArrowsAndTheCoachOnly() {
        val all = BoardOptions(evalBar = true, arrows = true, clocks = true, engineLines = true, takebacks = true)
        assertEquals(
            BoardExtras(evalBar = false, arrows = true, moveList = false, clocks = false, coachStrip = true, engineLines = false, takebacks = false),
            BoardConfig.LESSON.resolve(all),
        )
    }

    @Test
    fun puzzlesNeverShowTheEngineWhileSolving() {
        val asked = BoardConfig.PUZZLE.resolve(BoardOptions(evalBar = true, engineLines = true, arrows = true, clocks = true))
        assertFalse("no eval bar while solving", asked.evalBar)
        assertFalse("no engine lines while solving", asked.engineLines)
        assertTrue("hints when asked", asked.arrows)
        assertTrue("timer in timed modes", asked.clocks)
        assertTrue(asked.coachStrip && asked.takebacks)

        val solved = BoardConfig.PUZZLE_SOLVED.resolve(BoardOptions(evalBar = true, engineLines = true))
        assertTrue("the engine may explain once solved", solved.evalBar && solved.engineLines)
    }

    @Test
    fun reviewAndAnalysisShowTheEngineAlways() {
        for (config in listOf(BoardConfig.REVIEW, BoardConfig.ANALYSIS)) {
            val extras = config.resolve()
            assertTrue(config.name, extras.evalBar && extras.arrows && extras.moveList && extras.engineLines)
            assertFalse(config.name, extras.coachStrip || extras.takebacks)
        }
        assertTrue("review shows recorded times", BoardConfig.REVIEW.resolve().clocks)
        assertFalse(BoardConfig.ANALYSIS.resolve().clocks)
    }

    @Test
    fun everyConfigurationHasItsOwnName() {
        assertEquals(BoardConfig.ALL.size, BoardConfig.ALL.map { it.name }.toSet().size)
    }
}
