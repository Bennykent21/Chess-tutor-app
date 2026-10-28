package com.chesstutor.app.feature.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.board.BoardColors
import com.chesstutor.app.board.PieceSet
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.data.settings.BoardOrientation
import com.chesstutor.app.design.CtTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Board and pieces settings (step 1D): every control changes the saved settings. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class BoardSettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val isRadio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
    private val isSwitch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

    private var settings by mutableStateOf(AppSettings())

    private fun show() {
        val actions = BoardSettingsActions(
            onPieceSet = { settings = settings.copy(pieceSetId = it.id) },
            onBoardColors = { settings = settings.copy(boardThemeId = it.id) },
            onShowCoordinates = { settings = settings.copy(showCoordinates = it) },
            onShowLegalDots = { settings = settings.copy(showLegalDots = it) },
            onAutoQueen = { settings = settings.copy(autoQueen = it) },
            onOrientation = { settings = settings.copy(boardOrientation = it) },
        )
        compose.setContent { CtTheme { BoardSettingsScreen(settings = settings, actions = actions, onBack = {}) } }
    }

    @Test
    fun choosingAPieceSetAndColoursSavesThem() {
        show()
        compose.onNode(hasText("Classic") and isRadio and isSelected()).assertExists()
        compose.onNode(hasText("Fantasy") and isRadio).performScrollTo().performClick()
        compose.onNode(hasContentDescription("Neon") and isRadio).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(PieceSet.FANTASY, settings.boardTheme.pieceSet)
        assertEquals(BoardColors.NEON, settings.boardTheme.colors)
        compose.onNode(hasText("Fantasy") and isRadio and isSelected()).assertExists()
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun togglesFlipTheirSettings() {
        show()
        compose.onNode(hasText("Coordinates") and isSwitch).performScrollTo().assertIsOn().performClick()
        compose.onNode(hasText("White always at the bottom") and isSwitch).performScrollTo().assertIsOff().performClick()
        compose.onNode(hasText("Move dots") and isSwitch).performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(settings.showCoordinates)
        assertFalse(settings.showLegalDots)
        assertEquals(BoardOrientation.WHITE_ALWAYS_BOTTOM, settings.boardOrientation)
        compose.onNode(hasText("Coordinates") and isSwitch).assertIsOff()
        // Sound, vibration and reduce motion are in Settings only.
        compose.onNode(hasText("Reduce motion")).assertDoesNotExist()
    }
}
