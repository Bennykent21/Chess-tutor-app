package com.chesstutor.app.data.settings

import com.chesstutor.app.domain.Mascot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun emptyStorageGivesDefaults() {
        assertEquals(AppSettings(), AppSettingsCodec.decode(emptyMap()))
    }

    @Test
    fun encodeThenDecodeRoundTrips() {
        val custom = AppSettings(
            soundEnabled = false,
            hapticsEnabled = false,
            showCoordinates = false,
            showLegalDots = false,
            reduceMotion = true,
            autoQueen = true,
            boardOrientation = BoardOrientation.WHITE_ALWAYS_BOTTOM,
            boardThemeId = "walnut",
            pieceSetId = "glass",
            mascotId = Mascot.SAGE.id,
        )
        assertEquals(custom, AppSettingsCodec.decode(AppSettingsCodec.encode(custom)))
    }

    @Test
    fun encodedValuesAreOnlyBooleansAndStrings() {
        assertTrue(AppSettingsCodec.encode(AppSettings()).values.all { it is Boolean || it is String })
    }

    @Test
    fun corruptedValuesFallBackToDefaultsInsteadOfCrashing() {
        val decoded = AppSettingsCodec.decode(
            mapOf(
                AppSettingsCodec.SOUND to "yes",
                AppSettingsCodec.ORIENTATION to "UPSIDE_DOWN",
                AppSettingsCodec.BOARD_THEME to "",
                AppSettingsCodec.REDUCE_MOTION to true,
                "some_old_key" to 42,
            )
        )
        assertTrue(decoded.soundEnabled)
        assertEquals(BoardOrientation.FOLLOW_SIDE, decoded.boardOrientation)
        assertEquals(AppSettings.DEFAULT_THEME_ID, decoded.boardThemeId)
        assertTrue(decoded.reduceMotion)
    }

    @Test
    fun mascotDefaultsToPip() {
        assertEquals(Mascot.PIP, AppSettings().mascot)
        assertEquals(Mascot.PIP, AppSettingsCodec.decode(emptyMap()).mascot)
    }

    @Test
    fun mascotIsStoredByItsIdUnderMascotKey() {
        val encoded = AppSettingsCodec.encode(AppSettings(mascotId = Mascot.ROOKIE.id))
        assertEquals("rookie", encoded[AppSettingsCodec.MASCOT])
        assertEquals("mascot_id", AppSettingsCodec.MASCOT)
        assertEquals(Mascot.ROOKIE, AppSettingsCodec.decode(encoded).mascot)
    }

    @Test
    fun unknownOrMistypedMascotFallsBackToPip() {
        for (stored in listOf<Any?>("dragon", "", "PIP", 3, true, null)) {
            val decoded = AppSettingsCodec.decode(mapOf(AppSettingsCodec.MASCOT to stored))
            assertEquals("stored value $stored", Mascot.PIP.id, decoded.mascotId)
        }
    }

    @Test
    fun legacySoundSettingIsImportedOnlyOnce() = runBlocking {
        val store = InMemorySettingsStore()
        store.importLegacyOnce(soundEnabled = false)
        assertFalse(store.settings.first().soundEnabled)

        store.update { it.copy(soundEnabled = true) }
        store.importLegacyOnce(soundEnabled = false)
        assertTrue("A later import must not overwrite the player's choice", store.settings.first().soundEnabled)
    }
}
