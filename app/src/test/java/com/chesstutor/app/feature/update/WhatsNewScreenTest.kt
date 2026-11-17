package com.chesstutor.app.feature.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.data.update.UpdateState
import com.chesstutor.app.design.AppTheme
import com.chesstutor.app.design.Appearance
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.update.AppRelease
import com.chesstutor.app.domain.update.AppUpdates
import com.chesstutor.app.feature.home.HomeActions
import com.chesstutor.app.feature.home.HomeScreen
import com.chesstutor.app.feature.home.HomeUiState
import com.chesstutor.app.viewmodel.AppUiState
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The update icon on Home and What's new (D90), with screenshots in light and dark. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h900dp-xxhdpi")
class WhatsNewScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val release = AppRelease(
        version = "1.2.0",
        apkUrl = "https://example.org/a.apk",
        apkBytes = 95_371_000L,
        sha256Url = null,
        notes = listOf(
            AppUpdates.parseNotes("1.2.0", "### Game review\n\n- Accuracy closer to chess.com: tuned on six games reviewed on both, it now lands within about four points of chess.com's number.\n- Book moves no longer count.\n\n### Updates\n\n- An icon beside your streak while a new version waits, and this page with what changed."),
            AppUpdates.parseNotes("1.1.1", "### Play\n\n- Chess960 against a friend or a bot."),
        ),
    )

    private val noActions = HomeActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    @Test
    fun theIconSitsBesideTheStreakOnlyWhileAnUpdateWaits() {
        var opened = 0
        var appearance by mutableStateOf(Appearance.LIGHT)
        var update by mutableStateOf<UpdateState>(UpdateState.Available(release))
        compose.setContent {
            CtTheme(theme = AppTheme.PARCHMENT, appearance = appearance, reduceMotion = true) {
                HomeScreen(
                    state = AppUiState(assessmentState = "COMPLETE"),
                    home = HomeUiState(dayStreak = 4),
                    actions = noActions.copy(onWhatsNew = { opened++ }),
                    update = update,
                )
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/Home_update_light.png")
        appearance = Appearance.DARK
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/Home_update_dark.png")
        compose.onNodeWithContentDescription("Chess Tutor 1.2.0 is ready").performClick()
        assertEquals(1, opened)

        // While downloading, the arrow fills from the bottom.
        update = UpdateState.Downloading(release, 40_000_000, release.apkBytes)
        compose.onNodeWithContentDescription("Chess Tutor 1.2.0 is ready").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/Home_update_downloading.png")

        update = UpdateState.UpToDate
        compose.onNodeWithContentDescription("Chess Tutor 1.2.0 is ready").assertDoesNotExist()
    }

    @Test
    fun whatsNewShowsEveryVersionsNotesAndTheUpdate() {
        val taps = mutableListOf<String>()
        var appearance by mutableStateOf(Appearance.LIGHT)
        var update by mutableStateOf<UpdateState>(UpdateState.Available(release))
        compose.setContent {
            CtTheme(theme = AppTheme.PARCHMENT, appearance = appearance, reduceMotion = true) {
                WhatsNewScreen(update, onUpdate = { taps += "update" }, onAllow = { taps += "allow" }, onBack = { taps += "back" })
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/WhatsNew_light.png")
        appearance = Appearance.DARK
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/WhatsNew_dark.png")
        compose.onNodeWithText("Chess Tutor 1.2.0").assertExists()
        compose.onNodeWithText("Game review").assertExists()
        compose.onNodeWithText("1.1.1").assertExists()
        compose.onNodeWithText("Update").performClick()
        compose.onNodeWithContentDescription("Back").performClick()

        update = UpdateState.Downloading(release, 40_000_000, release.apkBytes)
        compose.onNodeWithText("40 MB of 96 MB").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/WhatsNew_downloading.png")

        update = UpdateState.NeedsPermission(release, File("x.apk"))
        compose.onNodeWithText("Allow").performClick()
        assertEquals(listOf("update", "back", "allow"), taps)
    }
}
