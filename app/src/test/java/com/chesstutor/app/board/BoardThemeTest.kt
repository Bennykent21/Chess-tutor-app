package com.chesstutor.app.board

import com.chesstutor.app.design.AppTheme
import com.chesstutor.app.data.settings.AppSettings
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
        assertEquals(listOf("classic", "meadow", "lagoon", "walnut", "ice", "neon", "marble", "parchment", "baize", "mono", "slate"), BoardColors.entries.map { it.id })
        assertEquals(listOf("cburnett", "merida", "chessnut", "fantasy"), PieceSet.entries.map { it.id })
    }

    @Test
    fun unknownIdsFallBackToDefaults() {
        for (id in listOf(null, "", "glass", "CLASSIC")) {
            assertEquals(BoardColors.DEFAULT, BoardColors.fromId(id))
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

    /** The theme cards show these colours, so the board has to be what they promise, in both modes. */
    @Test
    fun everyThemesBoardIsTheOneItsCardShows() {
        AppTheme.entries.forEach { theme ->
            listOf(false, true).forEach { dark ->
                val palette = Palette.of(theme, dark)
                assertEquals("$theme light squares", BoardColors.forTheme(theme).light, palette.boardLight)
                assertEquals("$theme dark squares", BoardColors.forTheme(theme).dark, palette.boardDark)
            }
        }
    }

    @Test
    fun choosingAThemeBringsItsBoardAndABoardCanThenBePicked() {
        val baize = AppSettings().withTheme(AppTheme.BAIZE)
        assertEquals(BoardColors.BAIZE, baize.boardTheme.colors)

        val picked = baize.copy(boardThemeId = BoardColors.NEON.id)
        assertEquals("a picked board stays", BoardColors.NEON, picked.boardTheme.colors)
        assertEquals(AppTheme.BAIZE, picked.appTheme)
        assertEquals("the next theme brings its own again", BoardColors.SLATE, picked.withTheme(AppTheme.SLATE).boardTheme.colors)
    }
}
