package com.chesstutor.app.feature.home

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.bot.BotRoster
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Chess960 against a bot (Phase 13), and the Play page's friend tile, at phone size. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class BotChess960Test {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aBotGameCanStartFromAChess960Position() {
        var started: Int? = null
        compose.setContent {
            CtTheme(reduceMotion = true) {
                BotSetupScreen(BotRoster.byId("marlo")!!, onPlay = { _, _, chess960 -> started = chess960 }, onBack = {})
            }
        }
        compose.onNodeWithText("Chess960").performScrollTo().performClick()
        compose.onNode(hasText("Position ", substring = true)).performScrollTo().assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/bot_setup_960.png")
        compose.onNode(hasText("Play")).performClick()
        assertNotNull(started)
        assertTrue(started!! in 0 until 960)
    }

    @Test
    fun thePlayPageHasAPictureTileForPlayingAFriend() {
        var friend = false
        compose.setContent {
            CtTheme(reduceMotion = true) {
                BotsScreen(roster = BotRoster.bots, myBots = emptyList(), onOpenBot = {}, onNewBot = {}, onWatch = {}, onPlayFriend = { friend = true }, onBack = {})
            }
        }
        compose.onNodeWithText("This phone or nearby").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/play_friend_tile.png")
        compose.onNodeWithText("Play a friend").performClick()
        assertTrue(friend)
    }
}
