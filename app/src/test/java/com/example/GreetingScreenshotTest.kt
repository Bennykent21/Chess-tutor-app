package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.chess.ui.screens.CoachHomeScreen
import com.example.chess.coaching.CurriculumRepository
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun coach_home_screenshot() {
    composeTestRule.setContent {
      CoachHomeScreen(
        userEstimatedRating = 1200,
        userTacticsRating = 1150,
        puzzlesSolvedCount = 12,
        dueMistakes = emptyList(),
        activeLesson = CurriculumRepository.allLessons.first(),
        onStartPlacementAssessment = {},
        onOpenTacticsDojo = {},
        onStartSpacedReview = {},
        onResumeLesson = {},
        onLaunchSparring = {}
      )
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
