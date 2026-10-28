package com.chesstutor.app.data.settings

import com.chesstutor.app.board.BoardColors
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.board.PieceSet
import com.chesstutor.app.design.AppTheme
import com.chesstutor.app.design.Appearance
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
            pieceSetId = "merida",
            mascotId = Mascot.SAGE.id,
            appTheme = AppTheme.SLATE,
            appearance = Appearance.DARK,
        )
        assertEquals(custom, AppSettingsCodec.decode(AppSettingsCodec.encode(custom)))
    }

    @Test
    fun appThemeDefaultsToParchmentFollowingThePhone() {
        val decoded = AppSettingsCodec.decode(emptyMap())
        assertEquals(AppTheme.PARCHMENT, decoded.appTheme)
        assertEquals(Appearance.SYSTEM, decoded.appearance)
    }

    @Test
    fun appThemeIsStoredByNameAndUnknownNamesFallBack() {
        val encoded = AppSettingsCodec.encode(AppSettings(appTheme = AppTheme.BAIZE, appearance = Appearance.LIGHT))
        assertEquals("BAIZE", encoded[AppSettingsCodec.APP_THEME])
        assertEquals("LIGHT", encoded[AppSettingsCodec.APPEARANCE])
        listOf("", "NAVY", "baize", 3).forEach { stored ->
            val decoded = AppSettingsCodec.decode(mapOf(AppSettingsCodec.APP_THEME to stored, AppSettingsCodec.APPEARANCE to stored))
            assertEquals("stored value $stored", AppTheme.PARCHMENT, decoded.appTheme)
            assertEquals("stored value $stored", Appearance.SYSTEM, decoded.appearance)
        }
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
    fun anUnfinishedImportTargetIsRemembered() {
        val encoded = AppSettingsCodec.encode(AppSettings(importTarget = 757))
        assertEquals("757", encoded[AppSettingsCodec.IMPORT_TARGET])
        assertEquals(757, AppSettingsCodec.decode(encoded).importTarget)
        assertEquals(null, AppSettingsCodec.decode(AppSettingsCodec.encode(AppSettings())).importTarget)
        assertEquals("a target saved before Phase 7 still reads", 1500, AppSettingsCodec.decode(mapOf(AppSettingsCodec.IMPORT_TARGET to "ALL")).importTarget)
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
    fun boardThemeAndPieceSetDecodeToKnownIdsOnly() {
        for (stored in listOf<Any?>("glass", "", 7, null)) {
            val decoded = AppSettingsCodec.decode(
                mapOf(AppSettingsCodec.BOARD_THEME to stored, AppSettingsCodec.PIECE_SET to stored)
            )
            assertEquals("stored value $stored", BoardColors.DEFAULT.id, decoded.boardThemeId)
            assertEquals("stored value $stored", PieceSet.DEFAULT.id, decoded.pieceSetId)
        }
        // Phase 0 stored the placeholder piece set "classic"; it now means the Classic set.
        assertEquals(PieceSet.CBURNETT, AppSettingsCodec.decode(mapOf(AppSettingsCodec.PIECE_SET to "classic")).boardTheme.pieceSet)
    }

    @Test
    fun boardThemeCombinesTheStoredColoursAndPieces() {
        val theme = AppSettings(boardThemeId = "neon", pieceSetId = "chessnut").boardTheme
        assertEquals(BoardTheme(BoardColors.NEON, PieceSet.CHESSNUT), theme)
        assertEquals(BoardTheme(), AppSettings().boardTheme)
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
