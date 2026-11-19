package com.chesstutor.app.feature.puzzles

import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.design.CtTheme
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
        stats = PuzzleStats(rating = 1248, totalXp = 3420, playerLevel = PlayerLevel.fromXp(3420), solved = 80, solvedToday = true),
        levels = (1..11).associateWith { LevelProgress(it, if (it % 3 == 0) 4 else 5, true, 1) } + (12 to LevelProgress(12, 3, false, 2)),
    )

    @Test
    fun homeShowsTheRatingTheCurrentLevelAndItsTiles() {
        var played = -1
        var mistakes = false
        compose.setContent {
            CtTheme(reduceMotion = true) {
                PuzzlesHomeScreen(
                    state = state,
                    dueReviewCount = 3,
                    onPlayLevel = { played = it },
                    onOpenModes = {},
                    onReviewMistakes = { mistakes = true },
                )
            }
        }
        compose.onNodeWithText("1248").assertExists()
        compose.onNodeWithText("Best 3 of 5").assertExists()
        compose.onRoot().captureRoboImage()

        compose.onNodeWithText("Best 3 of 5").performClick()
        assertEquals(12, played)
        compose.onNodeWithText("Mistakes to fix").performClick()
        assertEquals(true, mistakes)
        // The levels sit in a strip under the card (D95): a passed one replays, the next one plays.
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Level 11"))).performClick()
        assertEquals(11, played)
        compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Level 12")) and
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "next level")
        ).performClick()
        assertEquals(12, played)
    }

    @Test
    fun aMissingPackSaysSoInsteadOfAnEmptyPath() {
        compose.setContent {
            CtTheme {
                PuzzlesHomeScreen(state.copy(packAvailable = false), 0, {}, {}, {})
            }
        }
        compose.onNodeWithText("Puzzles are missing").assertExists()
    }
}
