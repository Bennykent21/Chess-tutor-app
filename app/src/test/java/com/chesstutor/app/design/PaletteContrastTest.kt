package com.chesstutor.app.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteContrastTest {

    @Test
    fun everyAllowedTextPairingMeetsWcagAA() {
        val failures = Palette.TEXT_PAIRS.flatMap { (text, backgrounds) ->
            backgrounds.mapNotNull { bg ->
                val ratio = Palette.contrastRatio(text, bg)
                if (ratio < 4.5) "%08X on %08X = %.2f".format(text, bg, ratio) else null
            }
        }
        assertTrue("Pairs below 4.5:1: $failures", failures.isEmpty())
    }

    @Test
    fun contrastFormulaMatchesKnownValues() {
        assertEquals(21.0, Palette.contrastRatio(0xFFFFFFFFL, 0xFF000000L), 0.01)
        assertEquals(1.0, Palette.contrastRatio(0xFF777777L, 0xFF777777L), 0.0001)
    }

    @Test
    fun surfacesStepUpInLightness() {
        val steps = listOf(Palette.BG_DEEP, Palette.BG, Palette.SURFACE, Palette.SURFACE_RAISED, Palette.SURFACE_HIGH)
        val lum = steps.map(Palette::relativeLuminance)
        assertEquals(lum.sorted(), lum)
    }
}
