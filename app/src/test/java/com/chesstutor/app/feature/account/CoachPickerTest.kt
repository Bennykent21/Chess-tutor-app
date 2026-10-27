package com.chesstutor.app.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtCoachBubble
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.Mascot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The coach picker (decision D20) and coach bubble, rendered with Robolectric's native
 * graphics so the mascot VectorDrawables really inflate and draw. Record a screenshot to
 * look at with `./gradlew recordRoborazziDebug --tests '*CoachPickerTest*'`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1100dp-xxhdpi")
class CoachPickerTest {

    @get:Rule
    val compose = createComposeRule()

    private val isRadioButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    @Test
    fun pickerShowsOneSelectedRadioCardPerMascotAndSelectsOnTap() {
        var chosen by mutableStateOf(Mascot.DEFAULT)
        compose.setContent {
            CtTheme {
                CtScreenBackground {
                    Column(
                        modifier = Modifier.padding(CtSpacing.gutter),
                        verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
                    ) {
                        CtCard(modifier = Modifier.fillMaxWidth()) {
                            CoachPicker(selected = chosen, onSelect = { chosen = it })
                        }
                        Mascot.entries.forEach {
                            CtCoachBubble(mascot = it, text = "Ready to sharpen your tactics?", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }

        Mascot.entries.forEach { mascot ->
            compose.onNode(hasText(mascot.displayName) and isRadioButton).assertExists()
        }
        compose.onNode(hasText("Pip") and isRadioButton and isSelected()).assertExists()
        compose.onNode(hasText("Sage") and isRadioButton and isNotSelected()).assertExists()

        compose.onNode(hasText("Sage") and isRadioButton).performClick()
        compose.waitForIdle()
        assertEquals(Mascot.SAGE, chosen)
        compose.onNode(hasText("Sage") and isRadioButton and isSelected()).assertExists()
        compose.onNode(hasText("Pip") and isRadioButton and isNotSelected()).assertExists()

        compose.onRoot().captureRoboImage()
    }

    @Test
    fun coachBubbleReadsAsOneItemWithTheMascotName() {
        compose.setContent {
            CtTheme { CtCoachBubble(mascot = Mascot.ROOKIE, text = "Your move!") }
        }
        compose.onNode(hasText("Rookie") and hasText("Your move!")).assertExists()
    }
}
