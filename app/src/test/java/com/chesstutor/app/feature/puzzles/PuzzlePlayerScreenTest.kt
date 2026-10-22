package com.chesstutor.app.feature.puzzles

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.core.Move
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.puzzle.InMemoryPuzzleRepository
import com.chesstutor.app.domain.puzzle.Puzzle
import com.chesstutor.app.domain.puzzle.PuzzleMode
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The puzzle player screen (step 2D), driven by a test clock. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1300dp-xxhdpi")
class PuzzlePlayerScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(StandardTestDispatcher(scheduler) + SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    private fun fools(id: String) = Puzzle(
        id = id,
        fen = "rnbqkbnr/pppp1ppp/8/4p3/8/5P2/PPPPP1PP/RNBQKBNR w KQkq - 0 2",
        moves = listOf("g2g4", "d8h4"),
        rating = 500, primaryTheme = "mateIn1", themes = setOf("mateIn1", "short"),
    )

    private fun step(ms: Long) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
        compose.waitForIdle()
    }

    private fun show(player: PuzzlePlayer, onPlayLevel: (Int) -> Unit = {}) {
        compose.setContent {
            CtTheme(reduceMotion = true) {
                PuzzlePlayerScreen(player, AppSettings(), onFeedback = {}, onAnalyse = {}, onPlayLevel = onPlayLevel, onBack = {})
            }
        }
    }

    @Test
    fun solvingAPuzzleShowsTheGoalThenTheResult() {
        val player = PuzzlePlayer(
            PuzzleRunSpec(PuzzleMode.LEVEL, "Level 3", level = 3, plannedCount = 5, nextPuzzle = { i, _ -> fools("p$i") }),
            InMemoryPuzzleRepository(), scope, clock = { scheduler.currentTime },
        )
        player.start()
        show(player)
        step(700)
        compose.onNodeWithText("Level 3 · 1/5").assertExists()
        compose.onNodeWithText("Find the checkmate in one move").assertExists()
        compose.onNodeWithText("You play Black").assertExists()
        compose.onNodeWithText("Hint").performClick()
        step(10)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/PuzzlePlayer_solving.png")

        player.state.value.board!!.onMove(Move.fromUci("d8h4"))
        step(100)
        compose.onNodeWithText("Correct! Well found.").assertExists()
        compose.onNodeWithText("Mate in 1").assertExists()
        compose.onNode(hasText("Next puzzle")).performScrollTo().assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/PuzzlePlayer_solved.png")
    }

    @Test
    fun aPassedLevelOffersTheNextOne() {
        val player = PuzzlePlayer(
            PuzzleRunSpec(PuzzleMode.LEVEL, "Level 3", level = 3, plannedCount = 5, nextPuzzle = { i, _ -> fools("q$i") }),
            InMemoryPuzzleRepository(), scope, clock = { scheduler.currentTime },
        )
        var nextLevel = -1
        player.start()
        show(player) { nextLevel = it }
        repeat(5) {
            step(700)
            player.state.value.board!!.onMove(Move.fromUci("d8h4"))
            step(100)
            player.next()
        }
        step(100)
        compose.onNodeWithText("Level 3 passed!").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/PuzzlePlayer_result.png")
        compose.onNodeWithText("Next level").performClick()
        assertEquals(4, nextLevel)
    }
}
