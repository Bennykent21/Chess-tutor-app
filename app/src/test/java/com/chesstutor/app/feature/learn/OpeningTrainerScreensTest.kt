package com.chesstutor.app.feature.learn

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.learn.PracticeResult
import com.chesstutor.app.domain.opening.OpeningTrainer
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.domain.opening.TrainerLine
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The opening trainer's list and drill, and a course's practice set card (plan 6C). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1300dp-xxhdpi")
class OpeningTrainerScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val tree by lazy { File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream) }

    @Test
    fun theListShowsBothSidesWithLinesLearnedAndDue() {
        val state = TrainerState(
            loading = false,
            openings = OpeningTrainer.openings.map { o ->
                val lines = OpeningTrainer.linesFor(o, tree)
                TrainerOpeningState(o, lines, learned = if (o.id == "sicilian") 2 else 0, due = if (o.id == "sicilian") 1 else 0, started = if (o.id == "sicilian") 3 else 0)
            },
        )
        var opened: String? = null
        compose.setContent { CtTheme(reduceMotion = true) { OpeningTrainerScreen(state, onOpen = { opened = it }, onBack = {}) } }
        compose.onNodeWithText("As White").assertExists()
        compose.onNodeWithText("2 of 7 lines learned").assertExists()
        compose.onNodeWithText("1 due").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/learn_trainer.png")
        compose.onNodeWithText("Italian Game").performClick()
        assertEquals("italian", opened)
    }

    @Test
    fun theDrillShowsTheAnswerAfterASlip() {
        val opening = OpeningTrainer.byId("italian")!!
        val line = TrainerLine("Italian Game: Giuoco Piano", listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f8c5"), PieceColor.WHITE)
        val session = OpeningDrillSession(line)
        compose.setContent {
            CtTheme(reduceMotion = true) {
                OpeningDrillScreen(DrillUi(opening, line, session, lines = listOf(line)), AppSettings(), onFeedback = {}, onShowAnswer = {}, onAgain = {}, onNext = {}, onPickLine = {}, onBack = {})
            }
        }
        compose.onNodeWithText("Your move (0 of 3). You play White.").assertExists()
        session.onMove(LegalMoveGenerator.generateLegalMoves(session.state.value.position).first { it.uci == "d2d4" })
        compose.waitForIdle()
        compose.onNodeWithText("The book move is shown. Play it to carry on.").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/learn_trainer_drill.png")
    }

    @Test
    fun aCoursePageOffersItsPracticeSet() {
        val course = com.chesstutor.app.domain.learn.LessonParser.parseCourse(File("src/main/assets/lessons/tactics-forks.json").readText(), "tactics-forks.json")
        var played = false
        compose.setContent {
            CtTheme(reduceMotion = true) {
                CourseScreen(
                    course = course, progress = emptyMap(), onOpenLesson = {}, onBack = {},
                    onPracticeSet = { played = true },
                    practice = PracticeResult(course.id, best = 6, total = 10, plays = 2, lastPlayedAt = 0),
                )
            }
        }
        compose.onNodeWithText("Best 6/10, solve 8 to master it").assertExists()
        compose.onNodeWithText("Practice set").performClick()
        assertEquals(true, played)
    }
}
