package com.chesstutor.app.feature.puzzles

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
@Config(qualifiers = "w400dp-h2400dp-xxhdpi")
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
                        onDaily = { started += "daily" },
                        onThemed = { goal, pattern, difficulty -> started += "theme $goal $pattern $difficulty" },
                        onCustom = { band, pattern, count, timed -> started += "custom $band $pattern $count $timed" },
                        onMistakes = { started += "mistakes" },
                    ),
                    onBack = {},
                )
            }
        }
        compose.onRoot().captureRoboImage()

        compose.onNodeWithText("3 min").performClick()
        compose.onNodeWithText("Survival: three strikes").performClick()
        compose.onNodeWithText("Solve today's puzzle").performClick()

        compose.onNodeWithText("Checkmate").performClick()
        // The custom card lists the same pattern; the first one is in the theme card.
        compose.onAllNodesWithText("Back-rank mate")[0].performClick()
        compose.onNodeWithText("Harder").performClick()
        compose.onNodeWithText("Start 10 puzzles").performScrollTo().performClick()

        compose.onNodeWithText("1600 to 2000").performScrollTo().performClick()
        compose.onNodeWithText("20").performScrollTo().performClick()
        compose.onNodeWithText("Timer").performScrollTo().performClick()
        compose.onNodeWithText("Start").performScrollTo().performClick()
        compose.onNodeWithText("Review my mistakes").performScrollTo().performClick()

        assertEquals(
            listOf(
                "rush 3",
                "rush null",
                "daily",
                "theme ${PuzzleGoal.CHECKMATE} backRankMate ${Difficulty.HARDER}",
                "custom ${RatingBand.STRONG} null 20 true",
                "mistakes",
            ),
            started,
        )
    }
}
