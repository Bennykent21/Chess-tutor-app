package com.chesstutor.app.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The board never moves (D39): nothing above it can change height, and overlays do not push. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class CtBoardScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var long by mutableStateOf(false)
    private var sheet by mutableStateOf(false)

    private fun showScreen() {
        compose.setContent {
            CtTheme(reduceMotion = true) {
                CtBoardScreen(
                    title = "Puzzle",
                    onBack = {},
                    status = {
                        Text(if (long) "A status that keeps going and going, far longer than one line could ever hold, and then some more words besides." else "White to move")
                    },
                    board = { Box(Modifier.fillMaxSize()) },
                    controls = { Text("Undo") },
                    overlay = { CtSheet(visible = sheet, onDismiss = { sheet = false }) { Text("Solved") } },
                ) {
                    repeat(if (long) 40 else 1) { Text("Coach line $it") }
                }
            }
        }
    }

    private fun board(): DpRect = compose.onNodeWithTag(CtBoardScreenTags.BOARD).getBoundsInRoot()

    @Test
    fun theBoardSitsUnderTheFixedRowsAtFullWidth() {
        showScreen()
        val bounds = board()
        assertEquals(CtBoardScreenSpec.TopBarHeight + CtBoardScreenSpec.StatusRowHeight, bounds.top)
        assertEquals(400.dp, bounds.right - bounds.left)
        assertEquals(400.dp, bounds.bottom - bounds.top)
    }

    @Test
    fun longerMessagesAboveAndBelowNeverMoveTheBoard() {
        showScreen()
        val before = board()
        long = true
        compose.waitForIdle()
        assertEquals(before, board())
        assertEquals(CtBoardScreenSpec.StatusRowHeight, compose.onNodeWithTag(CtBoardScreenTags.STATUS).getBoundsInRoot().let { it.bottom - it.top })
    }

    @Test
    fun theControlsStayOnScreenHoweverMuchTextIsBelowTheBoard() {
        showScreen()
        long = true
        compose.waitForIdle()
        val controls = compose.onNodeWithTag(CtBoardScreenTags.CONTROLS).getBoundsInRoot()
        assertEquals(CtBoardScreenSpec.ControlsHeight, controls.bottom - controls.top)
        assertEquals("the controls end at the bottom of the screen", 860.dp, controls.bottom)
        compose.onNodeWithText("Undo").assertIsDisplayed()
    }

    @Test
    fun aSheetCoversTheScreenInsteadOfPushingTheBoard() {
        showScreen()
        val before = board()
        sheet = true
        compose.waitForIdle()
        compose.onNodeWithText("Solved").assertIsDisplayed()
        assertEquals(before, board())
    }

    @Test
    fun shortOrWideScreensShrinkTheBoardToKeepRoomBelow() {
        // A tall phone: the full width.
        assertEquals(400.dp, CtBoardScreenSpec.boardSize(400.dp, 860.dp))
        // A short phone: 640 - 56 - 64 - 160 = 360.
        assertEquals(360.dp, CtBoardScreenSpec.boardSize(400.dp, 640.dp))
        // Landscape: never smaller than the minimum.
        assertEquals(CtBoardScreenSpec.MinBoard, CtBoardScreenSpec.boardSize(800.dp, 400.dp))
    }
}
