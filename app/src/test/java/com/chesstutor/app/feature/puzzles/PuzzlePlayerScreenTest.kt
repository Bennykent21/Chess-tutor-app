package com.chesstutor.app.feature.puzzles

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import com.chesstutor.app.design.CtBoardScreenTags
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
                PuzzlePlayerScreen(player, AppSettings(), onFeedback = {}, onAnalyse = { _, _ -> }, onPlayLevel = onPlayLevel, onBack = {})
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
        compose.onNodeWithText("Level 3").assertExists()
        compose.onNodeWithText("1 of 5").assertExists()
        compose.onNodeWithText("Find the checkmate in one move").assertExists()
        compose.onNodeWithText("Black to move").assertExists()
        val boardWhileSolving = compose.onNodeWithTag(CtBoardScreenTags.BOARD).getBoundsInRoot()
        compose.onNodeWithText("Hint").performClick()
        step(10)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/PuzzlePlayer_solving.png")

        player.state.value.board!!.onMove(Move.fromUci("d8h4"))
        step(100)
        compose.onNodeWithText("Correct! Well found.").assertExists()
        compose.onNodeWithText("Solved").assertExists()
        // What a puzzle earns (XP, rating) and its themes wait for the run's result screen.
        compose.onNodeWithText("Mate in 1").assertDoesNotExist()
        compose.onNodeWithText("+", substring = true).assertDoesNotExist()
        compose.onNode(hasText("Next puzzle")).assertExists()
        // D39: the hint, the result and the new buttons never move the board.
        assertEquals(boardWhileSolving, compose.onNodeWithTag(CtBoardScreenTags.BOARD).getBoundsInRoot())
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/PuzzlePlayer_solved.png")
    }

    /** Regression: after solving, Analyse must send the puzzle's start and its solution. */
    @Test
    fun analyseSendsThePuzzleStartAndSolutionNotTheFinalPosition() {
        val player = PuzzlePlayer(
            PuzzleRunSpec(PuzzleMode.LEVEL, "Level 1", level = 1, plannedCount = 5, nextPuzzle = { i, _ -> fools("a$i") }),
            InMemoryPuzzleRepository(), scope, clock = { scheduler.currentTime },
        )
        var analysed: Pair<String, List<String>>? = null
        player.start()
        compose.setContent {
            CtTheme(reduceMotion = true) {
                PuzzlePlayerScreen(player, AppSettings(), onFeedback = {}, onAnalyse = { fen, line -> analysed = fen to line }, onPlayLevel = {}, onBack = {})
            }
        }
        step(700)
        val start = player.state.value.board!!.state.value.position.toFen()
        player.state.value.board!!.onMove(Move.fromUci("d8h4"))
        step(100)
        compose.onNodeWithContentDescription("Analyse").performClick()
        assertEquals(start to listOf("d8h4"), analysed)
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
        compose.onNodeWithText("XP earned", substring = true).assertExists()
        compose.onNodeWithText("Rating, ", substring = true).assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/PuzzlePlayer_result.png")
        compose.onNodeWithText("Next level").performClick()
        assertEquals(4, nextLevel)
    }
}
