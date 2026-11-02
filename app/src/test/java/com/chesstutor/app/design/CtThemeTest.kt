package com.chesstutor.app.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.chesstutor.app.ui.theme.InterFontFamily
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

    @Test
    fun theTypeScaleFollowsTheDesignLanguage() {
        val type = CtTypeScale(CtColorScheme(Palette.PARCHMENT_LIGHT))
        val headings = listOf(type.display, type.headline, type.topBar, type.title, type.number, type.bigNumber)
        headings.forEach {
            assertEquals(GroteskFontFamily, it.fontFamily)
            assertEquals(FontWeight.SemiBold, it.fontWeight)
            assertEquals((-0.02).em, it.letterSpacing)
        }
        listOf(type.titleSmall, type.body, type.bodySmall, type.label, type.caption, type.navLabel, type.button).forEach {
            assertEquals(InterFontFamily, it.fontFamily)
        }
        (listOf(type.number, type.bigNumber, type.body, type.label, type.caption)).forEach { assertEquals("tnum", it.fontFeatureSettings) }
        // Words are not tabular: in Grotesk "tnum" widens the comma ("Hi , Kenny").
        listOf(type.display, type.headline, type.topBar, type.title).forEach { assertEquals(null, it.fontFeatureSettings) }
        assertEquals(
            listOf(30, 22, 20, 19, 16, 13, 12, 48).map { it.sp },
            listOf(type.display, type.headline, type.topBar, type.title, type.body, type.caption, type.navLabel, type.bigNumber).map { it.fontSize },
        )
    }
}
