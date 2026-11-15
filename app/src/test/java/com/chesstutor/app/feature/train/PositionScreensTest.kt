package com.chesstutor.app.feature.train

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.AppTheme
import com.chesstutor.app.design.Appearance
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.train.PositionKind
import com.chesstutor.app.domain.train.PositionOutcome
import com.chesstutor.app.domain.train.PositionParser
import com.chesstutor.app.feature.play.PlaySession
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Train's endgames and middlegames (Phase 12, step 3, mock-up round 4) at phone size. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class PositionScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val sets = PositionParser.parse(File("src/main/assets/positions/positions.json").readText())

    private fun runOf(setId: String, index: Int = 0): PositionRunUi {
        val set = sets.single { it.id == setId }
        val position = set.positions[index]
        val board = PlaySession(Position.fromFen(position.fen), setOf(position.side)) {}.apply { open = true }
        return PositionRunUi(set, set.positions, index, board, thinking = false)
    }

    @Test
    fun theEndgamesListShowsEachSetWithHowManyAreWon() {
        val ui = sets.map { PositionSetUi(it, if (it.id == "king-pawn") 2 else 0) }
        var opened: String? = null
        compose.setContent {
            CtTheme(reduceMotion = true) {
                PositionSetsScreen(PositionKind.ENDGAME, ui.filter { it.set.kind == PositionKind.ENDGAME }, BoardTheme(), onOpen = { opened = it }, onBack = {})
            }
        }
        compose.onNodeWithText("Endgames").assertExists()
        compose.onNodeWithText("An extra pawn").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/train_endgames.png")
        compose.onNodeWithText("King and pawn").performClick()
        assertEquals("king-pawn", opened)
    }

    @Test
    fun aPositionInPlayShowsTheGoalTheAimAndTheControls() {
        var hint = false
        compose.setContent {
            CtTheme(reduceMotion = true) {
                PositionRunScreen(
                    runOf("king-pawn").copy(hintFrom = "e6"), AppSettings(), onFeedback = {}, onHint = { hint = true },
                    onRestart = {}, onGiveUp = {}, onNext = {}, onBack = {},
                )
            }
        }
        compose.onNodeWithText("Win").assertExists()
        compose.onNodeWithText("White to move").assertExists()
        compose.onNodeWithText("Promote the pawn").assertExists()
        compose.onNodeWithText("1 of 5").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/train_position.png")
        compose.onNodeWithText("Hint").performClick()
        assertEquals(true, hint)
    }

    @Test
    fun aSlippedPositionNamesTheMoveAndOffersTheNextInTheDark() {
        var next = false
        val slipped = runOf("king-pawn").copy(outcome = PositionOutcome.SLIPPED, end = PositionEnd.DRAWN, learnerMoves = 3, slip = "3.Kd5", better = "Kd6")
        compose.setContent {
            CtTheme(theme = AppTheme.PARCHMENT, appearance = Appearance.DARK, reduceMotion = true) {
                PositionRunScreen(slipped, AppSettings(), onFeedback = {}, onHint = {}, onRestart = {}, onGiveUp = {}, onNext = { next = true }, onBack = {})
            }
        }
        compose.onNodeWithText("Only a draw").assertExists()
        compose.onNodeWithText("3.Kd5 let it slip").assertExists()
        compose.onNodeWithText("Back tomorrow. Kd6 was the move.").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/train_position_slipped_dark.png")
        compose.onNodeWithText("Next position").performClick()
        assertEquals(true, next)
    }

    @Test
    fun aConvertedMiddlegameIsWon() {
        val won = runOf("extra-pawn").copy(outcome = PositionOutcome.WON, end = PositionEnd.CONVERTED, learnerMoves = 12)
        compose.setContent {
            CtTheme(reduceMotion = true) {
                PositionRunScreen(won, AppSettings(), onFeedback = {}, onHint = {}, onRestart = {}, onGiveUp = {}, onNext = {}, onBack = {})
            }
        }
        compose.onNodeWithText("Converted in 12 moves").assertExists()
        compose.onNodeWithText("3 more to go").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/train_position_won.png")
    }
}
