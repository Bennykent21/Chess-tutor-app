package com.chesstutor.app.feature.train

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.design.AppTheme
import com.chesstutor.app.design.Appearance
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.train.EndingKind
import com.chesstutor.app.domain.train.GameFinding
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** From your games (Phase 12, step 4, mock-up round 4) at phone size. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class FromYourGamesTest {

    @get:Rule
    val compose = createComposeRule()

    private val path = "e2e4 c7c6 d2d4 d7d5 e4e5".split(' ')
    private val report = FindingsReport(
        games = 120,
        reviewed = 14,
        findings = listOf(
            GameFinding.LineSlip(
                bookId = "caro-kann", bookTitle = "Caro-Kann", side = PieceColor.BLACK,
                fen = "rnbqkbnr/pp2pppp/2p5/3pP3/3P4/8/PPP2PPP/RNBQKBNR b KQkq - 0 3", path = path,
                after = "3.e5", played = "3...e6", right = "3...Bf5", misses = 6, reached = 9,
                note = "It locks your bishop inside the pawns.",
            ),
            GameFinding.EndingResult(EndingKind.ROOK, won = 2, reached = 7),
            GameFinding.RepeatedMistake(
                fen = "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3", opening = "Italian Game",
                after = "3.Bc4", played = "3...Nd4", better = "3...Nf6", times = 3, reached = 4,
            ),
        ),
    )

    @Test
    fun repeatedMistakesShowAsNumbersWithTrainIt() {
        var line: Pair<String, List<String>>? = null
        var ending: String? = null
        compose.setContent {
            CtTheme(reduceMotion = true) {
                FromYourGamesScreen(FindingsState(loading = false, report = report), BoardTheme(), onTrainLine = { id, p -> line = id to p }, onTrainEnding = { ending = it }, onBack = {})
            }
        }
        compose.onNodeWithText("6/9").assertExists()
        compose.onNodeWithText("120 games · 14 reviewed").assertExists()
        compose.onNodeWithText("3 of 4 games").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/train_from_games.png")
        compose.onNodeWithText("Train it").performClick()
        assertEquals("caro-kann" to path, line)
        compose.onNodeWithText("Rook endings").performClick()
        assertEquals("rook-endings", ending)
    }

    @Test
    fun withNoGamesItSaysWhereToGetThemInTheDark() {
        compose.setContent {
            CtTheme(theme = AppTheme.PARCHMENT, appearance = Appearance.DARK, reduceMotion = true) {
                FromYourGamesScreen(FindingsState(loading = false), BoardTheme(), onTrainLine = { _, _ -> }, onTrainEnding = {}, onBack = {})
            }
        }
        compose.onNodeWithText("Import your games in Account, or play a few here.").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/train_from_games_empty_dark.png")
    }
}
