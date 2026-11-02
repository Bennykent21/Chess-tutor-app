package com.chesstutor.app.feature.account

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.data.settings.PlayerPicture
import com.chesstutor.app.design.CtTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class PlayerPictureSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private var chosen by mutableStateOf<PlayerPicture>(PlayerPicture.None)
    private var chessComTaps = 0

    private fun label(text: String) = SemanticsMatcher("click label $text") { it.config.getOrNull(SemanticsActions.OnClick)?.label == text }

    private fun show(chessComUser: String?, busy: Boolean = false) {
        compose.setContent {
            CtTheme(reduceMotion = true) {
                Column {
                    PlayerPictureChoices(
                        current = chosen,
                        name = "Kenny",
                        chessComUser = chessComUser,
                        busy = busy,
                        message = null,
                        onPhone = {},
                        onChessCom = { chessComTaps++ },
                        onChoose = { chosen = it },
                    )
                }
            }
        }
    }

    @Test
    fun aFaceCanBeChosenAndRemoved() {
        show(chessComUser = null)
        compose.onNode(label("Use Juno as your picture")).performClick()
        assertEquals(PlayerPicture.Face("juno"), chosen)
        compose.onNode(label("Use Juno as your picture")).assertIsSelected()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/player_picture.png")

        compose.onNodeWithText("Remove picture").performClick()
        assertEquals(PlayerPicture.None, chosen)
    }

    @Test
    fun aCoachCanBeChosenToo() {
        show(chessComUser = null)
        compose.onNode(label("Use Pip as your picture")).performClick()
        assertEquals(PlayerPicture.Coach("pip"), chosen)
    }

    /** Lichess has no pictures, so the button only makes sense for a linked Chess.com account. */
    @Test
    fun theChessComPictureIsOfferedOnlyWhenThatAccountIsLinked() {
        show(chessComUser = null)
        compose.onNodeWithText("Use my Chess.com picture").assertDoesNotExist()
    }

    @Test
    fun aLinkedChessComAccountOffersItsPicture() {
        show(chessComUser = "kenny")
        compose.onNodeWithText("Use my Chess.com picture").performClick()
        assertEquals(1, chessComTaps)
    }

    @Test
    fun theButtonsWaitWhileAPictureIsLoading() {
        show(chessComUser = "kenny", busy = true)
        compose.onNodeWithText("Choose a photo").assertIsNotEnabled()
    }
}
