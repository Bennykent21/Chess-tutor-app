package com.chesstutor.app.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CtComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theSegmentedControlSelectsOneOption() {
        compose.setContent {
            var mode by remember { mutableStateOf(Appearance.SYSTEM) }
            CtTheme(reduceMotion = true) {
                CtSegmented(options = Appearance.entries, selected = mode, label = { it.label }, onSelect = { mode = it })
            }
        }
        compose.onNodeWithText("System").assertIsSelected()
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithText("Dark").assertIsSelected()
        compose.onNodeWithText("System").assertIsNotSelected()
    }

    @Test
    fun aToggleRowFlipsWhenTheWholeRowIsTapped() {
        compose.setContent {
            var on by remember { mutableStateOf(false) }
            CtTheme(reduceMotion = true) { CtToggleRow(title = "Sound", checked = on, onCheckedChange = { on = it }) }
        }
        compose.onNodeWithText("Sound").assertIsOff()
        compose.onNodeWithText("Sound").performClick()
        compose.onNodeWithText("Sound").assertIsOn()
    }

    @Test
    fun aSheetClosesFromOutsideButNotFromItsOwnContent() {
        var closed = 0
        compose.setContent {
            CtTheme(reduceMotion = true) {
                Box(Modifier.fillMaxSize()) {
                    CtSheet(visible = true, onDismiss = { closed++ }) { Text("You won") }
                }
            }
        }
        compose.onNodeWithText("You won").performClick()
        assertEquals("tapping the sheet itself keeps it open", 0, closed)
        // The scrim behind the sheet: tapping outside closes it.
        compose.onNode(SemanticsMatcher("click label Close") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Close" }).performClick()
        assertEquals(1, closed)
    }

    @Test
    fun aSpeechBubbleTailPointsAtTheSpeaker() {
        val size = Size(300f, 100f)
        fun geometry(tail: CtTail, rtl: Boolean = false) = bubbleGeometry(tail, size, rtl, depth = 10f, base = 16f, offset = 16f)

        // Start (left in English): the body moves 10 in and the tip touches the left edge, 16 + 8 down.
        val start = geometry(CtTail.Start)
        assertEquals(10f, start.body.left)
        assertEquals(Offset(0f, 24f), start.tail[1])
        // In a right-to-left language the same tail points right.
        assertEquals(Offset(300f, 24f), geometry(CtTail.Start, rtl = true).tail[1])
        assertEquals(Offset(300f, 24f), geometry(CtTail.End).tail[1])
        // A top tail lifts the body and points up, 16 + 8 from the start.
        val top = geometry(CtTail.Top)
        assertEquals(10f, top.body.top)
        assertEquals(Offset(24f, 0f), top.tail[1])
        assertTrue(geometry(CtTail.None).tail.isEmpty())
        assertFalse(geometry(CtTail.None).body.left > 0f)
    }
}
