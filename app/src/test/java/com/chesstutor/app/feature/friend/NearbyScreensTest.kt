package com.chesstutor.app.feature.friend

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.design.AppTheme
import com.chesstutor.app.design.Appearance
import com.chesstutor.app.design.CtTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Nearby and Chess960 on the friend pages (Phase 11C and 11D), at phone size. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class NearbyScreensTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun nearbyOffersJoinAndHostAndChess960ShowsItsPosition() {
        var draft by mutableStateOf(FriendDraft(mode = FriendMode.NEARBY, start = FriendStart.CHESS960, chess960 = 3))
        var hosted = false
        compose.setContent {
            CtTheme(reduceMotion = true) {
                FriendSetupScreen(
                    draft = draft, ownerName = "Sam", theme = BoardTheme(), onChange = { draft = it(draft) },
                    onPickOpening = {}, onSetPosition = {}, onStart = {}, onBack = {}, onHost = { hosted = true },
                )
            }
        }
        compose.onNodeWithText("Chess960 · 3").assertExists()
        compose.onNodeWithText("Face to face").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/friend_setup_nearby.png")
        compose.onNodeWithText("Host").performClick()
        assertTrue(hosted)
    }

    @Test
    fun theHostSeesTheCodeToShare() {
        compose.setContent {
            CtTheme(reduceMotion = true) {
                NearbyLobbyScreen(NearbyStatus.Hosting("KMQX", "192.168.1.5:41234"), joining = false, onJoin = {}, onCancel = {})
            }
        }
        compose.onNodeWithContentDescription("Code K M Q X").assertExists()
        compose.onNodeWithText("Code not found? They can type 192.168.1.5:41234 instead.").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/friend_nearby_host.png")
    }

    @Test
    fun theJoinerTypesTheCodeInTheDark() {
        var joined: String? = null
        compose.setContent {
            CtTheme(theme = AppTheme.PARCHMENT, appearance = Appearance.DARK, reduceMotion = true) {
                NearbyLobbyScreen(NearbyStatus.Failed("No game with the code ABCE nearby. Are you both on the same Wi-Fi or hotspot?"), joining = true, onJoin = { joined = it }, onCancel = {})
            }
        }
        compose.onNodeWithContentDescription("Your friend's code").performTextInput("kq7vx")
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/friend_nearby_join_dark.png")
        compose.onNodeWithText("Join").performClick()
        assertEquals("typed in lower case, with a slip", "KQVX", joined)
    }
}
