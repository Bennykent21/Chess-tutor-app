package com.chesstutor.app.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** [CtTheme] hands the chosen theme and mode to screens and to Material components alike. */
@RunWith(AndroidJUnit4::class)
class CtThemeTest {

    @get:Rule
    val compose = createComposeRule()

    private data class Seen(val bg: Color, val text: Color, val gold: Color, val materialBg: Color, val headline: Color)

    private fun seenIn(theme: AppTheme, appearance: Appearance): Seen {
        var seen: Seen? = null
        compose.setContent {
            CtTheme(theme = theme, appearance = appearance) {
                seen = Seen(CtColors.Bg, CtColors.Text, CtColors.Gold, MaterialTheme.colorScheme.background, CtType.Headline.color)
            }
        }
        compose.waitForIdle()
        return seen!!
    }

    @Test
    fun theChosenThemeAndModeReachEveryReader() {
        val p = Palette.MONO_DARK
        assertEquals(
            Seen(Color(p.bg), Color(p.text), Color(p.accent), Color(p.bg), Color(p.text)),
            seenIn(AppTheme.MONO, Appearance.DARK),
        )
    }

    @Test
    fun lightModeUsesTheLightPalette() {
        assertEquals(Color(Palette.PARCHMENT_LIGHT.bg), seenIn(AppTheme.PARCHMENT, Appearance.LIGHT).bg)
    }

    @Test
    fun eachThemeHasItsOwnColours() {
        assertEquals(Color(Palette.SLATE_LIGHT.text), seenIn(AppTheme.SLATE, Appearance.LIGHT).text)
    }
}
