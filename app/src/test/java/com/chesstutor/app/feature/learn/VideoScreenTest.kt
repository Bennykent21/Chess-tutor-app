package com.chesstutor.app.feature.learn

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.learn.LessonLevel
import com.chesstutor.app.domain.learn.LessonVideo
import com.chesstutor.app.gallery.GALLERY_PHONE
import com.chesstutor.app.gallery.shootBoth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The in-app video player page (D98): the video, what it teaches and what to watch next. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = GALLERY_PHONE)
class VideoScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = LessonVideo("endgames-rook", LessonLevel.BEGINNER, "aaaaaaaaaaa", "The Lucena position, step by step", "A channel", 12, "How to build a bridge with the rook and promote the pawn.")
    private val next = listOf(
        LessonVideo("endgames-rook", LessonLevel.BEGINNER, "bbbbbbbbbbb", "The Philidor defence", "Another channel", 9, ""),
        LessonVideo("topic:endgames", LessonLevel.INTERMEDIATE, "ccccccccccc", "Rook endgames you must know", "A third channel", 21, ""),
    )

    @Test
    fun theVideoSaysWhatItTeachesAndUpNextOpensTheNextOne() {
        var opened: LessonVideo? = null
        compose.setContent {
            CtTheme(reduceMotion = true) { VideoScreen(now, next, onOpen = { opened = it }, onBack = {}) }
        }
        compose.onNodeWithText("The Lucena position, step by step").assertExists()
        compose.onNodeWithText("A channel · 12 min").assertExists()
        compose.onNodeWithText("How to build a bridge", substring = true).assertExists()
        compose.onNodeWithText("Up next").assertExists()
        compose.onNodeWithContentDescription("The Philidor defence", substring = true).performClick()
        assertEquals(next[0], opened)
    }

    @Test
    fun gallery() {
        compose.shootBoth("learn", "11_video") { VideoScreen(now, next, onOpen = {}, onBack = {}) }
    }
}
