package com.chesstutor.app.feature.home

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.board.PieceSet
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.Mascot
import com.chesstutor.app.navigation.StartingLevel
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The first run (D46): the coach asks the player's name, then where to start. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h900dp-xxhdpi")
class FirstRunScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun itAsksTheNameThenWhereToStart() {
        var name: String? = null
        var level: StartingLevel? = null
        compose.setContent {
            CtTheme(reduceMotion = true) {
                FirstRunScreen(mascot = Mascot.PIP, pieceSet = PieceSet.DEFAULT, onName = { name = it }, onStartingLevel = { level = it })
            }
        }
        compose.onNodeWithText("Hi, I'm Pip").assertExists()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Your name").performTextInput("  Kenny ")
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/first_run_name.png")
        compose.onNodeWithText("Continue").performClick()
        assertEquals("Kenny", name)
        compose.onNodeWithText("Nice to meet you, Kenny").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/first_run_level.png")
        compose.onNodeWithText("I know the rules").performClick()
        assertEquals(StartingLevel.RULES, level)
    }

    @Test
    fun theNameCanBeSkipped() {
        var name: String? = null
        compose.setContent {
            CtTheme(reduceMotion = true) {
                FirstRunScreen(mascot = Mascot.PIP, pieceSet = PieceSet.DEFAULT, onName = { name = it }, onStartingLevel = {})
            }
        }
        compose.onNodeWithText("Skip").performClick()
        assertEquals(null, name)
        compose.onNodeWithText("Nice to meet you").assertExists()
    }

    @Test
    fun theGreetingUsesTheNameElseTheLinkedUsername() {
        assertEquals("Kenny", greetingName(AppSettings(playerName = "Kenny"), "kenny_k"))
        assertEquals("kenny_k", greetingName(AppSettings(), "kenny_k"))
        assertEquals(null, greetingName(AppSettings(playerName = "  "), null))
    }
}
