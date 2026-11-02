package com.chesstutor.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MascotTest {

    @Test
    fun idsAreStableBecauseSettingsStoreThem() {
        // Changing an id would silently reset every player's chosen coach to Pip.
        assertEquals(listOf("pip", "rookie", "sage"), Mascot.entries.map { it.id })
    }

    @Test
    fun everyMascotRoundTripsThroughItsId() {
        Mascot.entries.forEach { assertEquals(it, Mascot.fromId(it.id)) }
    }

    @Test
    fun unknownIdFallsBackToPip() {
        assertEquals(Mascot.PIP, Mascot.DEFAULT)
        assertEquals(Mascot.PIP, Mascot.fromId("dragon"))
        assertEquals(Mascot.PIP, Mascot.fromId(null))
    }

    @Test
    fun everyMascotHasNameTaglineAndDistinctArt() {
        Mascot.entries.forEach {
            assertTrue(it.displayName.isNotBlank())
            assertTrue(it.tagline.isNotBlank())
        }
        assertEquals(Mascot.entries.size, Mascot.entries.map { it.art() }.toSet().size)
    }

    @Test
    fun everyCoachHasItsOwnDrawingForEveryMood() {
        val drawings = Mascot.entries.flatMap { coach -> MascotMood.entries.map { coach.art(it) } }
        assertEquals("3 coaches x 4 moods, no drawing shared", Mascot.entries.size * MascotMood.entries.size, drawings.toSet().size)
        assertEquals(MascotMood.READY.let { mood -> Mascot.SAGE.art(mood) }, Mascot.SAGE.art())
    }

    @Test
    fun everyMoodSaysHowTheCoachLooks() {
        MascotMood.entries.forEach { assertTrue(it.description.isNotBlank()) }
    }
}
