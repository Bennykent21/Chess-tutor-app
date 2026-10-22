package com.chesstutor.app.feature.analysis

import android.net.Uri
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.navigation.AppRoutes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** "Analyse" from a puzzle: the position and solution reach the analysis board intact. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h1600dp-xxhdpi")
class AnalysisScreenTest {

    @get:Rule
    val compose = createComposeRule()

    /** Fool's mate after White's 2.g4: Black to move, the solution is ...Qh4#. */
    private val fen = "rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq g3 0 2"

    @Test
    fun theRouteCarriesTheFenAndLineIntact() {
        val uri = Uri.parse(AppRoutes.analysis(fen, listOf("d8h4")))
        assertEquals(fen, uri.getQueryParameter(AppRoutes.ANALYSIS_FEN_ARG))
        assertEquals("d8h4", uri.getQueryParameter(AppRoutes.ANALYSIS_LINE_ARG))
        assertEquals("", Uri.parse(AppRoutes.analysis(fen)).getQueryParameter(AppRoutes.ANALYSIS_LINE_ARG))
    }

    @Test
    fun aPuzzleOpensAtItsPositionWithTheSolutionAsTheMainLine() {
        compose.setContent {
            CtTheme(reduceMotion = true) {
                AnalysisScreen(startFen = fen, settings = AppSettings(), line = listOf("d8h4"), engine = null, onFeedback = {}, onBack = {})
            }
        }
        compose.onNode(hasContentDescription("Chess board. Black to move. Black at the bottom.")).assertExists()
        compose.onNodeWithText("Qh4#").assertExists()
        compose.onNodeWithText("Step through the solution, or try your own moves").assertExists()
    }

    @Test
    fun anUnreadablePositionSaysSoInsteadOfShowingTheStart() {
        compose.setContent {
            CtTheme { AnalysisScreen(startFen = "not a position", settings = AppSettings(), engine = null, onFeedback = {}, onBack = {}) }
        }
        compose.onNodeWithText("This position could not be opened").assertExists()
    }
}
