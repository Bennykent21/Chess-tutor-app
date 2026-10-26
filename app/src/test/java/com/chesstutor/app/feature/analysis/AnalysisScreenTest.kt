package com.chesstutor.app.feature.analysis

import android.net.Uri
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.SanFormatter
import com.chesstutor.app.domain.MoveChoice
import com.chesstutor.app.engine.EngineEvaluation
import com.chesstutor.app.engine.EngineHealthReport
import com.chesstutor.app.engine.EngineLine
import com.chesstutor.app.engine.EngineService
import com.chesstutor.app.engine.EngineStatus
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.robolectric.annotation.GraphicsMode

/** "Analyse" from a puzzle: the position and solution reach the analysis board intact. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h1600dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
        compose.onNodeWithText("Step through the line, or try your own moves").assertExists()
    }

    @Test
    fun anUnreadablePositionSaysSoInsteadOfShowingTheStart() {
        compose.setContent {
            CtTheme { AnalysisScreen(startFen = "not a position", settings = AppSettings(), engine = null, onFeedback = {}, onBack = {}) }
        }
        compose.onNodeWithText("This position could not be opened").assertExists()
    }

    /** Scores the start as level with 1.e4 best, and any position with White's f-pawn on f3 as lost. */
    private class JudgingEngine : EngineService {
        override val status = MutableStateFlow(EngineStatus())
        override suspend fun bestMove(fen: String, movetimeMs: Int): MoveChoice? {
            val position = Position.fromFen(fen)
            val move = LegalMoveGenerator.generateLegalMoves(position).first()
            return MoveChoice.fromCoreMove(move, SanFormatter.format(position, move), 'p')
        }
        override suspend fun evaluate(fen: String, movetimeMs: Int): EngineEvaluation? = null
        override suspend fun analyse(fen: String, lines: Int, movetimeMs: Int): List<EngineLine> {
            val position = Position.fromFen(fen)
            val first = LegalMoveGenerator.generateLegalMoves(position).firstOrNull()?.uci ?: return emptyList()
            return when {
                fen == Position.STARTING_FEN -> listOf(EngineLine(1, listOf("e2e4"), 30), EngineLine(2, listOf("d2d4"), 25))
                "5P2" in fen -> listOf(EngineLine(1, listOf(first), -450))
                else -> listOf(EngineLine(1, listOf(first), 30))
            }
        }
        override suspend fun botMove(fen: String, elo: Int): MoveChoice? = bestMove(fen, 0)
        override suspend fun healthCheck(): EngineHealthReport? = null
    }

    @Test
    fun everyMoveIsJudgedAndATakeBackIsOffered() {
        compose.setContent {
            CtTheme(reduceMotion = true) {
                AnalysisScreen(startFen = Position.STARTING_FEN, settings = AppSettings(), line = listOf("f2f3"), engine = JudgingEngine(), onFeedback = {}, onBack = {})
            }
        }
        compose.onNodeWithText("Play a move and I'll tell you how good it was.").assertExists()
        compose.onNode(hasContentDescription("Go to the end")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("f3 is a blunder").assertExists()
        compose.onNode(hasContentDescription("1. f3 Blunder")).assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/explore_blunder.png")
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        compose.onNodeWithText("Play a move and I'll tell you how good it was.").assertExists()
    }

    @Test
    fun theEngineAnswersForItsSide() {
        compose.setContent {
            CtTheme(reduceMotion = true) {
                AnalysisScreen(startFen = Position.STARTING_FEN, settings = AppSettings(), engine = JudgingEngine(), onFeedback = {}, onBack = {}, engineSide = PieceColor.WHITE, engineElo = 1200)
            }
        }
        compose.waitForIdle()
        // White (the engine) moved first; Black's pieces are at the bottom for the player.
        compose.onNode(hasContentDescription("Chess board. Black to move. Black at the bottom.")).assertExists()
        compose.onNodeWithText("Play the engine; every move is judged").assertExists()
    }

    @Test
    fun theRouteCarriesTheEnginesSideAndStrength() {
        val uri = Uri.parse(AppRoutes.analysis(Position.STARTING_FEN, engineSide = "black", engineElo = null))
        assertEquals("black", uri.getQueryParameter(AppRoutes.ANALYSIS_ENGINE_ARG))
        assertEquals("0", uri.getQueryParameter(AppRoutes.ANALYSIS_ELO_ARG))
        assertEquals(null, Uri.parse(AppRoutes.analysis(fen)).getQueryParameter(AppRoutes.ANALYSIS_ENGINE_ARG))
    }
}
