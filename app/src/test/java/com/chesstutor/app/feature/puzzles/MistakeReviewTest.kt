package com.chesstutor.app.feature.puzzles

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.core.Move
import com.chesstutor.app.data.repository.InMemoryReviewRepository
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.ReviewItem
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** Puzzles > Mistakes review on the new board (step 5D). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1100dp-xxhdpi")
class MistakeReviewTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-10-25T12:00:00Z")
    private val repo = InMemoryReviewRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking {
            repo.upsert(
                ReviewItem(
                    id = "game_x_31", fen = "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", dueAt = now.minusSeconds(5),
                    bestMoveUci = "a1a8", explanation = "Ra8 is mate.", sourceDescription = "Your game against Pip.",
                ),
            )
            repo.upsert(ReviewItem(id = "later", fen = "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", dueAt = now.plusSeconds(3_600), bestMoveUci = "a1a8"))
        }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun solvesADuePositionAndSavesTheSchedule() {
        var changes = 0
        val vm = MistakeReviewViewModel(repo, onChanged = { changes++ }) { now }
        assertEquals(1, vm.ui.value.total)
        assertEquals("Your game against Pip. Find the best move.", vm.ui.value.message)

        vm.onMove(Move.fromUci("a1a7"))
        assertTrue(vm.ui.value.wrong)
        vm.hint()
        assertEquals("Move the piece on a1.", vm.ui.value.message)
        vm.onMove(Move.fromUci("a1a8"))
        assertTrue(vm.ui.value.done)
        assertEquals("That's it. Ra8 is mate.", vm.ui.value.message)
        assertNull("the board is read-only once solved", vm.state.value.movable)
        assertEquals("the miss was saved once", 1, changes)
        val saved = runBlocking { repo.getById("game_x_31")!! }
        assertEquals(-1, saved.stage)

        vm.next()
        assertTrue(vm.ui.value.finished)
        assertEquals(0, vm.ui.value.cleanCount)
    }

    @Test
    fun screenShowsThePositionThenAllCaughtUp() {
        val vm = MistakeReviewViewModel(repo) { now }
        compose.setContent {
            CtTheme(reduceMotion = true) {
                val ui by vm.ui.collectAsState()
                MistakeReviewScreen(ui = ui, session = vm, settings = AppSettings(), actions = MistakeReviewActions(onHint = vm::hint, onShowAnswer = vm::showAnswer, onNext = vm::next))
            }
        }
        compose.onNodeWithText("Position 1 of 1").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/puzzles_mistake_review.png")
        compose.onNodeWithText("Solution").performClick()
        compose.onNodeWithText("The answer is shown. Ra8 is mate.").assertExists()
        compose.onNodeWithText("Finish").performClick()
        compose.onNodeWithText("All caught up").assertExists()
    }
}
