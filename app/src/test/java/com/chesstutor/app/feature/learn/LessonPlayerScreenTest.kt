package com.chesstutor.app.feature.learn

import org.junit.Assert.assertEquals
import com.chesstutor.app.design.CtBoardScreenTags
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.core.Move
import com.chesstutor.app.data.learn.InMemoryLessonProgressRepository
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.learn.LessonParser
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The lesson player screen (step 4A) on the bundled "Check and checkmate" course. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1300dp-xxhdpi")
class LessonPlayerScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(StandardTestDispatcher(scheduler) + SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    private val course = LessonParser.parseCourse(File("src/main/assets/lessons/basics-check-and-mate.json").readText())

    private fun step(ms: Long = 1_000) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
        compose.waitForIdle()
    }

    private fun show(player: LessonPlayer, onRelatedPuzzles: (() -> Unit)? = null) {
        compose.setContent {
            CtTheme(reduceMotion = true) {
                LessonPlayerScreen(player, AppSettings(), onFeedback = {}, onNextLesson = {}, onReplay = {}, onBack = {}, onRelatedPuzzles = onRelatedPuzzles)
            }
        }
    }

    @Test
    fun watchThenLearnThenTry() {
        val player = LessonPlayer(course, course.lessons[0], InMemoryLessonProgressRepository(), scope, nextLesson = course.lessons[1])
        player.start()
        show(player)
        step()
        compose.onNodeWithText("Video coming soon").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/lesson_watch.png")

        compose.onNodeWithText("Start").performClick()
        step()
        compose.onNodeWithText("When a piece attacks the king", substring = true).assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/lesson_learn.png")

        compose.onNodeWithText("Next").performClick()
        step()
        compose.onNodeWithText("Next").performClick()
        step()
        compose.onNodeWithText("Your turn: give check with the rook.").assertExists()
        val boardBefore = compose.onNodeWithTag(CtBoardScreenTags.BOARD).getBoundsInRoot()
        compose.onNodeWithText("Hint").performClick()
        step()
        compose.onNodeWithText("Rooks move in straight lines", substring = true).assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/lesson_try_hint.png")

        player.board.onMove(Move.fromUci("a1a8"))
        step()
        compose.onNodeWithText("Correct!").assertExists()
        // D39: the hint, the longer coach text and the new buttons never move the board.
        assertEquals(boardBefore, compose.onNodeWithTag(CtBoardScreenTags.BOARD).getBoundsInRoot())
    }

    @Test
    fun quizShowsChallengeNumbersAndDoneShowsXp() {
        val lesson = course.lessons[1]
        val player = LessonPlayer(course, lesson, InMemoryLessonProgressRepository(), scope, startStep = 3)
        var related = false
        player.start()
        show(player, onRelatedPuzzles = { related = true })
        step()
        compose.onNodeWithText("Challenge 1/2").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/lesson_quiz.png")

        player.board.onMove(Move.fromUci("b1b8"))
        step()
        player.next()
        step()
        player.board.onMove(Move.fromUci("c1c8"))
        step()
        player.next()
        step()
        compose.onNodeWithText("Finish").performClick()
        step()
        compose.onNodeWithText("Lesson complete").assertExists()
        compose.onNodeWithText("+20 XP").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/lesson_done.png")
        compose.onNodeWithText("Practise in Opening Battle").assertDoesNotExist()
        compose.onNodeWithText("Related puzzles").performScrollTo().performClick()
        assertTrue("Related puzzles opens the puzzles", related)
    }
}
