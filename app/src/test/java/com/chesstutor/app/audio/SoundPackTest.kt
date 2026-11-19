package com.chesstutor.app.audio

import com.chesstutor.app.R
import com.chesstutor.app.board.BoardSound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Every pack has a sound for every board event (D108). */
class SoundPackTest {

    @Test
    fun everyPackSoundsEveryEventAndStoredIdsAreStable() {
        SoundPack.entries.forEach { pack -> BoardSound.entries.forEach { pack.rawFor(it) } }
        // Settings store these ids; renaming one would silently reset players' choices.
        assertEquals(listOf("wood", "studio", "piano", "retro", "scifi"), SoundPack.entries.map { it.id })
    }

    @Test
    fun packsShareOnlyTheirRightWrongAndTapSounds() {
        assertEquals(R.raw.sfx_correct, SoundPack.PIANO.rawFor(BoardSound.CORRECT))
        assertEquals(R.raw.sfx_studio_move, SoundPack.STUDIO.rawFor(BoardSound.CASTLE))
        assertEquals(R.raw.sfx_castle, SoundPack.WOOD.rawFor(BoardSound.CASTLE))
        assertNotEquals(SoundPack.WOOD.rawFor(BoardSound.CAPTURE), SoundPack.SCIFI.rawFor(BoardSound.CAPTURE))
    }
}
