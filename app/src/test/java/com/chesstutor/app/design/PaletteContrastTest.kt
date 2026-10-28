package com.chesstutor.app.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteContrastTest {

    private fun name(p: CtPalette) = "${p.theme.label} ${if (p.dark) "dark" else "light"}"

    @Test
    fun everyAllowedTextPairingMeetsWcagAAInEveryThemeAndMode() {
        val failures = Palette.ALL.flatMap { palette ->
            palette.textPairs.flatMap { (text, backgrounds) ->
                backgrounds.mapNotNull { bg ->
                    val ratio = Palette.contrastRatio(text, bg)
                    if (ratio < 4.5) "${name(palette)}: %08X on %08X = %.2f".format(text, bg, ratio) else null
                }
            }
        }
        assertTrue("Pairs below 4.5:1: $failures", failures.isEmpty())
    }

    @Test
    fun accentShapesStandOutFromEveryBackground() {
        // Icons, progress fills and selection marks are UI parts: WCAG asks 3:1 against what is around them.
        val failures = Palette.ALL.flatMap { p ->
            listOf(p.bg, p.surface, p.raised, p.track).mapNotNull { bg ->
                val ratio = Palette.contrastRatio(p.accent, bg)
                if (ratio < 3.0) "${name(p)}: accent on %08X = %.2f".format(bg, ratio) else null
            }
        }
        assertTrue("Accent below 3:1: $failures", failures.isEmpty())
    }

    @Test
    fun everyThemeHasALightAndADarkPalette() {
        assertEquals(AppTheme.entries.size * 2, Palette.ALL.size)
        AppTheme.entries.forEach { theme ->
            listOf(false, true).forEach { dark ->
                val p = Palette.of(theme, dark)
                assertEquals(theme, p.theme)
                assertEquals(dark, p.dark)
                val bgLum = Palette.relativeLuminance(p.bg)
                if (dark) assertTrue("${name(p)} background must be dark", bgLum < 0.05) else assertTrue("${name(p)} background must be light", bgLum > 0.75)
            }
        }
    }

    @Test
    fun parchmentIsTheDefaultAndTheAppFollowsThePhone() {
        assertEquals(AppTheme.PARCHMENT, AppTheme.DEFAULT)
        assertEquals(Appearance.SYSTEM, Appearance.DEFAULT)
        assertTrue(Appearance.SYSTEM.isDark(systemDark = true))
        assertFalse(Appearance.SYSTEM.isDark(systemDark = false))
        assertFalse(Appearance.LIGHT.isDark(systemDark = true))
        assertTrue(Appearance.DARK.isDark(systemDark = false))
        assertEquals(AppTheme.PARCHMENT, AppTheme.fromName("nonsense"))
        assertEquals(AppTheme.MONO, AppTheme.fromName("MONO"))
        assertEquals(Appearance.SYSTEM, Appearance.fromName(null))
    }

    @Test
    fun contrastFormulaMatchesKnownValues() {
        assertEquals(21.0, Palette.contrastRatio(0xFFFFFFFFL, 0xFF000000L), 0.01)
        assertEquals(1.0, Palette.contrastRatio(0xFF777777L, 0xFF777777L), 0.0001)
    }
}
