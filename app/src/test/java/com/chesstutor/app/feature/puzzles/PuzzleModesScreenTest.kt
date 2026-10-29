package com.chesstutor.app.feature.puzzles

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.puzzle.Difficulty
import com.chesstutor.app.domain.puzzle.PuzzleGoal
import com.chesstutor.app.domain.puzzle.RatingBand
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h900dp-xxhdpi")
class PuzzleModesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everyModeStartsWithTheChosenOptions() {
        val started = mutableListOf<String>()
        val patterns = mapOf(
            PuzzleGoal.CHECKMATE to listOf("mateIn2", "backRankMate"),
            PuzzleGoal.WIN_MATERIAL to listOf("fork", "pin"),
            PuzzleGoal.DEFEND to listOf("defensiveMove"),
            PuzzleGoal.ENDGAME to listOf("rookEndgame"),
        )
        compose.setContent {
            CtTheme {
                PuzzleModesScreen(
                    patternsFor = { patterns.getValue(it) },
                    actions = PuzzleModeActions(
                        onRush = { started += "rush $it" },
                        onThemed = { goal, pattern, difficulty -> started += "theme $goal $pattern $difficulty" },
                        onCustom = { band, pattern, count, timed -> started += "custom $band $pattern $count $timed" },
                        onMistakes = { started += "mistakes" },
                    ),
                    onBack = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/puzzle_modes.png")

        compose.onNodeWithContentDescription("3-minute rush").performClick()
        compose.onNodeWithContentDescription("Survival, three strikes").performClick()

        compose.onNodeWithText("Checkmate").performClick()
        compose.onNodeWithText("Back-rank mate").performClick()
        compose.onNodeWithText("Harder").performClick()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/puzzle_modes_goal.png")
        compose.onNodeWithText("Start 10 puzzles").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Custom run").performScrollTo().performClick()
        compose.onNodeWithText("1600 to 2000").performClick()
        compose.onNodeWithText("20").performClick()
        compose.onNodeWithText("Timer").performClick()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/puzzle_modes_custom.png")
        compose.onNodeWithText("Start 20 puzzles").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Puzzles you missed").performScrollTo().performClick()

        assertEquals(
            listOf(
                "rush 3",
                "rush null",
                "theme ${PuzzleGoal.CHECKMATE} backRankMate ${Difficulty.HARDER}",
                "custom ${RatingBand.STRONG} null 20 true",
                "mistakes",
            ),
            started,
        )
    }
}
