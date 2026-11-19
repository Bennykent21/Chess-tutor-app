package com.chesstutor.app.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.core.Move
import com.chesstutor.app.data.repository.InMemoryReviewRepository
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.AppTheme
import com.chesstutor.app.design.Appearance
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.ReviewItem
import com.chesstutor.app.domain.puzzle.InMemoryPuzzleRepository
import com.chesstutor.app.domain.puzzle.LevelProgress
import com.chesstutor.app.domain.puzzle.PlayerLevel
import com.chesstutor.app.domain.puzzle.Puzzle
import com.chesstutor.app.domain.puzzle.PuzzleGoal
import com.chesstutor.app.domain.puzzle.PuzzleMode
import com.chesstutor.app.domain.puzzle.PuzzleStats
import com.chesstutor.app.feature.puzzles.CustomRunScreen
import com.chesstutor.app.feature.puzzles.MistakeReviewActions
import com.chesstutor.app.feature.puzzles.MistakeReviewScreen
import com.chesstutor.app.feature.puzzles.MistakeReviewViewModel
import com.chesstutor.app.feature.puzzles.PuzzleModeActions
import com.chesstutor.app.feature.puzzles.PuzzleModesScreen
import com.chesstutor.app.feature.puzzles.PuzzlePlayer
import com.chesstutor.app.feature.puzzles.PuzzlePlayerScreen
import com.chesstutor.app.feature.puzzles.PuzzleRunSpec
import com.chesstutor.app.feature.puzzles.PuzzlesHomeScreen
import com.chesstutor.app.feature.puzzles.PuzzlesHomeState
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every screen of the Puzzles tab at phone size, light and dark, for the visual pass (Phase 14B).
 * Pictures land in `build/outputs/roborazzi/gallery/puzzles/` with `recordRoborazziDebug`.
 */
@OptIn(ExperimentalRoborazziApi::class, ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class PuzzlesTabGalleryTest {

    @get:Rule
    val compose = createComposeRule()

    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(StandardTestDispatcher(scheduler) + SupervisorJob())

    @After
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    private var dark by mutableStateOf(false)

    private fun step(ms: Long = 0) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
        compose.waitForIdle()
    }

    /** Shows [content] in light, then dark, and saves both; [between] runs before each picture. */
    private fun shoot(name: String, between: () -> Unit = {}, content: @Composable () -> Unit) {
        compose.setContent {
            CtTheme(theme = AppTheme.PARCHMENT, appearance = if (dark) Appearance.DARK else Appearance.LIGHT, reduceMotion = true) { content() }
        }
        step()
        between()
        step()
        captureScreenRoboImage("$DIR/${name}_light.png")
        dark = true
        step()
        captureScreenRoboImage("$DIR/${name}_dark.png")
    }

    private val state = PuzzlesHomeState(
        loading = false,
        stats = PuzzleStats(rating = 1248, totalXp = 3420, playerLevel = PlayerLevel.fromXp(3420), solved = 80, solvedToday = true),
        levels = (1..11).associateWith { LevelProgress(it, if (it % 3 == 0) 4 else 5, true, 1) } + (12 to LevelProgress(12, 3, false, 2)),
    )

    private val patterns = mapOf(
        PuzzleGoal.CHECKMATE to listOf("mateIn2", "backRankMate"),
        PuzzleGoal.WIN_MATERIAL to listOf("fork", "pin"),
        PuzzleGoal.DEFEND to listOf("defensiveMove"),
        PuzzleGoal.ENDGAME to listOf("promotion", "rookEndgame"),
    )

    private fun fools(id: String) = Puzzle(
        id = id,
        fen = "rnbqkbnr/pppp1ppp/8/4p3/8/5P2/PPPPP1PP/RNBQKBNR w KQkq - 0 2",
        moves = listOf("g2g4", "d8h4"),
        rating = 500, primaryTheme = "mateIn1", themes = setOf("mateIn1", "short"),
    )

    @Test
    fun home() = shoot("01_puzzles") {
        PuzzlesHomeScreen(state = state, dueReviewCount = 3, onPlayLevel = {}, onOpenModes = {}, onReviewMistakes = {})
    }

    @Test
    fun modes() = shoot("03_modes") {
        PuzzleModesScreen(patternsFor = { patterns.getValue(it) }, actions = PuzzleModeActions({}, { _, _, _, _, _ -> }, {}, {}), onBack = {})
    }

    @Test
    fun goalPage() = shoot("04_goal", between = { if (!dark) compose.onNodeWithContentDescription("Checkmate").performClick() }) {
        PuzzleModesScreen(patternsFor = { patterns.getValue(it) }, actions = PuzzleModeActions({}, { _, _, _, _, _ -> }, {}, {}), onBack = {})
    }

    @Test
    fun customRun() = shoot("05_custom_run") {
        CustomRunScreen(patternsFor = { patterns.getValue(it) }, puzzleRating = 1310, onStart = { _, _, _, _ -> }, onBack = {})
    }

    @Test
    fun solving() {
        val player = PuzzlePlayer(
            PuzzleRunSpec(PuzzleMode.LEVEL, "Level 3", level = 3, plannedCount = 5, nextPuzzle = { i, _ -> fools("p$i") }),
            InMemoryPuzzleRepository(), scope, clock = { scheduler.currentTime },
        )
        player.start()
        shoot("06_solving", between = { step(700) }) {
            PuzzlePlayerScreen(player, AppSettings(), onFeedback = {}, onAnalyse = { _, _ -> }, onPlayLevel = {}, onBack = {})
        }
    }

    @Test
    fun solved() {
        val player = PuzzlePlayer(
            PuzzleRunSpec(PuzzleMode.LEVEL, "Level 3", level = 3, plannedCount = 5, nextPuzzle = { i, _ -> fools("p$i") }),
            InMemoryPuzzleRepository(), scope, clock = { scheduler.currentTime },
        )
        player.start()
        var played = false
        shoot("07_solved", between = {
            step(700)
            if (!played) {
                played = true
                player.state.value.board!!.onMove(Move.fromUci("d8h4"))
                step(400)
            }
        }) {
            PuzzlePlayerScreen(player, AppSettings(), onFeedback = {}, onAnalyse = { _, _ -> }, onPlayLevel = {}, onBack = {})
        }
    }

    @Test
    fun mistakeReview() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val now = Instant.parse("2026-10-25T12:00:00Z")
        val repo = InMemoryReviewRepository()
        runBlocking {
            repo.upsert(
                ReviewItem(
                    id = "game_x_31", fen = "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", dueAt = now.minusSeconds(5),
                    bestMoveUci = "a1a8", explanation = "Ra8 is mate.", sourceDescription = "Your game against Pip.",
                ),
            )
        }
        val vm = MistakeReviewViewModel(repo) { now }
        shoot("08_mistakes") {
            val ui by vm.ui.collectAsState()
            MistakeReviewScreen(ui = ui, session = vm, settings = AppSettings(), actions = MistakeReviewActions(onHint = vm::hint, onShowAnswer = vm::showAnswer, onNext = vm::next))
        }
    }

    private companion object {
        const val DIR = "build/outputs/roborazzi/gallery/puzzles"
    }
}
