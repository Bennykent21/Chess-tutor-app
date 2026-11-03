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

    private val patterns = mapOf(
        PuzzleGoal.CHECKMATE to listOf("mateIn2", "fork", "backRankMate"),
        PuzzleGoal.WIN_MATERIAL to listOf("fork", "pin"),
        PuzzleGoal.DEFEND to listOf("defensiveMove"),
        PuzzleGoal.ENDGAME to listOf("rookEndgame", "promotion"),
    )

    @Test
    fun eachPatternSitsOnceUnderTheGoalItBelongsToMost() {
        assertEquals(
            listOf(
                PuzzleGoal.CHECKMATE to listOf("mateIn2", "backRankMate"),
                PuzzleGoal.WIN_MATERIAL to listOf("fork", "pin"),
                PuzzleGoal.DEFEND to listOf("defensiveMove"),
                PuzzleGoal.ENDGAME to listOf("promotion", "rookEndgame"),
            ),
            patternGroups { patterns.getValue(it) },
        )
    }

    /** Phone test 3 Nov: picking a pattern in a sideways strip inside a sheet was a hassle. */
    @Test
    fun customRunShowsEveryPatternAndStartsWithTheChoices() {
        var started: String? = null
        compose.setContent {
            CtTheme(reduceMotion = true) {
                CustomRunScreen(
                    patternsFor = { patterns.getValue(it) },
                    puzzleRating = 1310,
                    onStart = { band, pattern, count, timed -> started = "$band $pattern $count $timed" },
                    onBack = {},
                )
            }
        }
        compose.onNodeWithText("1200 to 1600").assertExists()
        compose.onNodeWithText("You").assertExists()
        compose.onNodeWithText("Pin").performScrollTo().performClick()
        compose.onNodeWithText("1600").performClick()
        compose.onNodeWithText("20").performClick()
        compose.onNodeWithText("Timer").performClick()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/puzzle_custom_run.png")
        compose.onNodeWithText("Start 20 puzzles").performClick()
        assertEquals("${RatingBand.STRONG} pin 20 true", started)
    }

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
                        onOpenCustom = { started += "custom" },
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
        compose.onNodeWithText("Puzzles you missed").performScrollTo().performClick()

        assertEquals(
            listOf(
                "rush 3",
                "rush null",
                "theme ${PuzzleGoal.CHECKMATE} backRankMate ${Difficulty.HARDER}",
                "custom",
                "mistakes",
            ),
            started,
        )
    }
}
