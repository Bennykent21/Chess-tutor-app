package com.chesstutor.app.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.chesstutor.app.design.AppTheme
import com.chesstutor.app.design.Appearance
import com.chesstutor.app.design.CtTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage

/**
 * A screen in light, then dark, saved for the visual pass (Phase 14B) under
 * `build/outputs/roborazzi/gallery/[tab]/`. A test using it runs at phone size
 * (`@Config(qualifiers = "w360dp-h780dp-xxhdpi")` on the test) and is recorded with
 * `recordRoborazziDebug`. [settle] runs before each picture, for clocks driven by a test scheduler.
 */
@OptIn(ExperimentalRoborazziApi::class)
fun ComposeContentTestRule.shootBoth(tab: String, name: String, settle: () -> Unit = {}, content: @Composable () -> Unit) {
    var dark by mutableStateOf(false)
    setContent {
        CtTheme(theme = AppTheme.PARCHMENT, appearance = if (dark) Appearance.DARK else Appearance.LIGHT, reduceMotion = true) { content() }
    }
    waitForIdle()
    settle()
    captureScreenRoboImage("build/outputs/roborazzi/gallery/$tab/${name}_light.png")
    dark = true
    waitForIdle()
    settle()
    captureScreenRoboImage("build/outputs/roborazzi/gallery/$tab/${name}_dark.png")
}

/** The phone size every gallery picture is taken at. */
const val GALLERY_PHONE = "w360dp-h780dp-xxhdpi"
