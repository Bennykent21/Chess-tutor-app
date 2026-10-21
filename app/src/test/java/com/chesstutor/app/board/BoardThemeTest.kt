package com.chesstutor.app.board

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.settings.BoardOrientation
import com.chesstutor.app.design.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardThemeTest {

    @Test
    fun coordinatesMeetWcagAAOnBothSquareColoursOfEveryTheme() {
        val failures = BoardColors.entries.flatMap { theme ->
            listOf(
                Triple("on light", theme.coordinateOnLight, theme.light),
                Triple("on dark", theme.coordinateOnDark, theme.dark),
            ).mapNotNull { (where, ink, square) ->
                val ratio = Palette.contrastRatio(ink, square)
                if (ratio < 4.5) "${theme.id} $where = %.2f".format(ratio) else null
            }
        }
        assertTrue("Coordinates below 4.5:1: $failures", failures.isEmpty())
    }

    @Test
    fun lightAndDarkSquaresAreEasyToTellApart() {
        BoardColors.entries.forEach { theme ->
            val ratio = Palette.contrastRatio(theme.light, theme.dark)
            assertTrue("${theme.id} squares only %.2f:1 apart".format(ratio), ratio >= 1.5)
            assertTrue("${theme.id} light square must be the lighter one", Palette.relativeLuminance(theme.light) > Palette.relativeLuminance(theme.dark))
        }
    }

    @Test
    fun storedIdsAreUniqueAndStable() {
        assertEquals(BoardColors.entries.size, BoardColors.entries.map { it.id }.toSet().size)
        assertEquals(PieceSet.entries.size, PieceSet.entries.map { it.id }.toSet().size)
        // Settings store these ids; renaming one would silently reset players' choices.
        assertEquals(listOf("classic", "meadow", "lagoon", "walnut", "ice", "neon", "marble"), BoardColors.entries.map { it.id })
        assertEquals(listOf("cburnett", "merida", "chessnut", "fantasy"), PieceSet.entries.map { it.id })
    }

    @Test
    fun unknownIdsFallBackToDefaults() {
        for (id in listOf(null, "", "glass", "CLASSIC")) {
            assertEquals(BoardColors.CLASSIC, BoardColors.fromId(id))
        }
        // "classic" was the Phase 0 placeholder piece set id.
        for (id in listOf(null, "", "classic", "glass")) {
            assertEquals(PieceSet.CBURNETT, PieceSet.fromId(id))
        }
        assertEquals(BoardColors.NEON, BoardColors.fromId("neon"))
        assertEquals(PieceSet.FANTASY, PieceSet.fromId("fantasy"))
    }

    @Test
    fun boardFacesThePlayersSideUnlessWhiteIsPinnedToTheBottom() {
        assertTrue(whiteAtBottom(BoardOrientation.FOLLOW_SIDE, PieceColor.WHITE))
        assertFalse(whiteAtBottom(BoardOrientation.FOLLOW_SIDE, PieceColor.BLACK))
        assertTrue(whiteAtBottom(BoardOrientation.WHITE_ALWAYS_BOTTOM, PieceColor.WHITE))
        assertTrue(whiteAtBottom(BoardOrientation.WHITE_ALWAYS_BOTTOM, PieceColor.BLACK))
    }
}
