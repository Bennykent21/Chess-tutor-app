package com.chesstutor.app.feature.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.data.update.UpdateState
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.update.AppRelease
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The update prompt at each stage (D88). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class UpdateDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val release = AppRelease("1.2.0", "https://example.org/a.apk", 94_947_862L, null)

    @Test
    fun eachStageSaysWhatIsHappeningAndOffersTheNextStep() {
        var state by mutableStateOf<UpdateState>(UpdateState.Available(release))
        val taps = mutableListOf<String>()
        compose.setContent {
            CtTheme(reduceMotion = true) {
                UpdateDialog(state, onUpdate = { taps += "update" }, onAllow = { taps += "allow" }, onLater = { taps += "later" }, onHide = { taps += "hide" }, onWhatsNew = { taps += "notes" })
            }
        }
        compose.onNodeWithText("Chess Tutor 1.2.0").assertExists()
        compose.onNodeWithText("A new version is ready. 95 MB.").assertExists()
        compose.onNodeWithText("What's new").performClick()
        compose.onNodeWithText("Update").performClick()

        state = UpdateState.Downloading(release, 40_000_000, 94_947_862L)
        compose.onNodeWithText("40 MB of 95 MB").assertExists()
        compose.onNodeWithText("Hide").performClick()

        state = UpdateState.NeedsPermission(release, File("x.apk"))
        compose.onNodeWithText("Allow updates").assertExists()
        compose.onNodeWithText("Open settings").performClick()
        compose.onNodeWithText("Install").performClick()

        state = UpdateState.Failed(release, "The download didn't finish. Check your internet and try again.")
        compose.onNodeWithText("Later").performClick()

        assertEquals(listOf("notes", "update", "hide", "allow", "update", "later"), taps)
    }
}
