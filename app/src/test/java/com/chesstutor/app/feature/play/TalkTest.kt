package com.chesstutor.app.feature.play

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.design.CtTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The talking coach's bubbles (D97): each line stays long enough to read, the one before stays below the newest. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class TalkTest {

    @get:Rule
    val compose = createComposeRule()

    private var said by mutableStateOf(listOf<TalkLine>())

    private fun show() {
        compose.setContent {
            CtTheme(reduceMotion = true) {
                Column(Modifier.height(240.dp)) { Talk(avatar = {}, lines = rememberTalk(said, key = Unit)) }
            }
        }
    }

    @Test
    fun theLineBeforeStaysOnScreenAndTheOldestLeaves() {
        show()
        compose.waitForIdle()
        said = listOf(TalkLine(1, "First"))
        compose.mainClock.advanceTimeBy(MIN_READ_MS + 100)
        compose.waitForIdle()
        said = listOf(TalkLine(2, "Second"))
        compose.mainClock.advanceTimeBy(MIN_READ_MS + 100)
        compose.onNodeWithText("First").assertExists()
        compose.onNodeWithText("Second").assertExists()
        compose.waitForIdle()
        said = listOf(TalkLine(3, "Third"))
        compose.mainClock.advanceTimeBy(MIN_READ_MS + 100)
        compose.onNodeWithText("First").assertDoesNotExist()
        compose.onNodeWithText("Third").assertExists()
    }

    @Test
    fun aNewLineWaitsUntilTheLastOneCouldBeRead() {
        show()
        compose.waitForIdle()
        said = listOf(TalkLine(1, "First"))
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        said = listOf(TalkLine(2, "Second"))
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Second").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(MIN_READ_MS)
        compose.onNodeWithText("Second").assertExists()
    }

    @Test
    fun theSameWordsAreNotSaidTwiceInARow() {
        show()
        compose.waitForIdle()
        said = listOf(TalkLine(1, "Your move"))
        compose.mainClock.advanceTimeBy(MIN_READ_MS + 100)
        compose.waitForIdle()
        said = listOf(TalkLine(2, "Your move"))
        compose.mainClock.advanceTimeBy(MIN_READ_MS + 100)
        assertEquals(1, compose.onAllNodesWithText("Your move").fetchSemanticsNodes().size)
    }

    @Test
    fun aBlankLineIsNeverSaid() {
        var talk = listOf<TalkLine>()
        compose.setContent { CtTheme(reduceMotion = true) { talk = rememberTalk(said, key = Unit) } }
        compose.waitForIdle()
        said = listOf(TalkLine(1, "First"))
        compose.mainClock.advanceTimeBy(MIN_READ_MS + 100)
        compose.waitForIdle()
        said = listOf(TalkLine(2, ""), TalkLine(3, "  "))
        compose.mainClock.advanceTimeBy(MIN_READ_MS + 100)
        compose.waitForIdle()
        assertEquals(listOf("First"), talk.map { it.text })
    }
}
