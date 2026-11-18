package com.chesstutor.app.feature.learn

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.learn.LessonLevel
import com.chesstutor.app.domain.learn.LessonVideo
import com.chesstutor.app.gallery.GALLERY_PHONE
import com.chesstutor.app.gallery.shootBoth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A video from a Watch row on its own page (D100): the player with its controls, and what it teaches. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = GALLERY_PHONE)
class VideoScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val video = LessonVideo("endgames-rook", LessonLevel.BEGINNER, "aaaaaaaaaaa", "The Lucena position, step by step", "A channel", 12, "How to build a bridge with the rook and promote the pawn.")

    @Test
    fun theVideoHasItsControlsAndSaysWhatItTeaches() {
        compose.setContent { CtTheme(reduceMotion = true) { VideoScreen(video, onBack = {}) } }
        compose.onNodeWithText("The Lucena position, step by step").assertExists()
        compose.onNodeWithText("A channel · 12 min").assertExists()
        compose.onNodeWithText("How to build a bridge", substring = true).assertExists()
        listOf("Back 10 seconds", "Play", "Forward 10 seconds", "Full screen").forEach { compose.onNodeWithContentDescription(it).assertExists() }
        compose.onNodeWithText("Up next").assertDoesNotExist()
    }

    @Test
    fun gallery() {
        compose.shootBoth("learn", "11_video") { VideoScreen(video, onBack = {}) }
    }
}
