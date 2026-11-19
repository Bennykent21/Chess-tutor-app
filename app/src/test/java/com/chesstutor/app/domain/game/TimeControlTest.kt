package com.chesstutor.app.domain.game

import org.junit.Assert.assertEquals
import org.junit.Test

/** Any clock, with presets for bullet, blitz and rapid (D92). */
class TimeControlTest {

    @Test
    fun labelsReadLikeAChessSite() {
        assertEquals("No clock", TimeControl.NONE.label)
        assertEquals("10 min", TimeControl.RAPID_10.label)
        assertEquals("3 | 2", TimeControl.BLITZ_3_2.label)
        assertEquals("0:30", TimeControl(30_000L).label)
    }

    @Test
    fun theKindFollowsTheGamesExpectedLength() {
        assertEquals("untimed", TimeControl.NONE.timeClass)
        assertEquals("bullet", TimeControl.BULLET_2_1.timeClass)
        assertEquals("blitz", TimeControl.BLITZ_3_2.timeClass)
        assertEquals("blitz", TimeControl.BLITZ_5.timeClass)
        assertEquals("rapid", TimeControl.RAPID_10.timeClass)
        assertEquals("rapid", TimeControl.RAPID_15_10.timeClass)
    }

    @Test
    fun aSavedClockComesBackAndOldSavedNamesStillRead() {
        val custom = TimeControl.of(7, 4)
        assertEquals(custom, TimeControl.decode(custom.encode()))
        assertEquals(TimeControl.NONE, TimeControl.decode(TimeControl.NONE.encode()))
        // Bots and games saved before D92 kept the enum's names.
        assertEquals(TimeControl.NONE, TimeControl.decode("NONE"))
        assertEquals(TimeControl.BLITZ_5, TimeControl.decode("BLITZ_5"))
        assertEquals(TimeControl.RAPID_15_10, TimeControl.decode("RAPID_15_10"))
        assertEquals("anything unreadable plays without a clock", TimeControl.NONE, TimeControl.decode("garbage"))
        assertEquals(TimeControl.NONE, TimeControl.decode(null))
    }
}
