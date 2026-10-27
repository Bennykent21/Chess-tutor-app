package com.chesstutor.app.feature.puzzles

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.Mascot
import com.chesstutor.app.domain.puzzle.LevelProgress
import com.chesstutor.app.domain.puzzle.PlayerLevel
import com.chesstutor.app.domain.puzzle.PuzzleStats
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
class PuzzlesHomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val state = PuzzlesHomeState(
        loading = false,
        stats = PuzzleStats(rating = 1248, totalXp = 3420, playerLevel = PlayerLevel.fromXp(3420), dayStreak = 5, solved = 80, solvedToday = true),
        levels = (1..11).associateWith { LevelProgress(it, if (it % 3 == 0) 4 else 5, true, 1) } + (12 to LevelProgress(12, 3, false, 2)),
    )

    @Test
    fun homeShowsStatsCoachPathAndPlaysTheCurrentLevel() {
        var played = -1
        // Reduce motion: the current level's endless pulse would keep the test from ever going idle.
        compose.setContent {
            CtTheme(reduceMotion = true) {
                PuzzlesHomeScreen(
                    state = state,
                    mascot = Mascot.PIP,
                    dueReviewCount = 0,
                    onPlayLevel = { played = it },
                    onOpenModes = {},
                    onReviewMistakes = {},
                )
            }
        }
        compose.onNodeWithText("3,420").assertExists()
        compose.onNodeWithText("Day 5 in a row. You're on fire!").assertExists()
        compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Level 12")) and
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "next level")
        ).assertExists()
        compose.onRoot().captureRoboImage()

        compose.onNodeWithText("Solve puzzles").performClick()
        assertEquals(12, played)
        compose.onNodeWithContentDescription("Level 12").performClick()
        assertEquals(12, played)
    }

    @Test
    fun aMissingPackSaysSoInsteadOfAnEmptyPath() {
        compose.setContent {
            CtTheme {
                PuzzlesHomeScreen(state.copy(packAvailable = false), Mascot.SAGE, 0, {}, {}, {})
            }
        }
        compose.onNodeWithText("Puzzles are missing").assertExists()
    }
}
