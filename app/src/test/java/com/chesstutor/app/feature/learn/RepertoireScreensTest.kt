package com.chesstutor.app.feature.learn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.board.MoveEffects
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.Appearance
import com.chesstutor.app.design.AppTheme
import com.chesstutor.app.design.CtBoardScreenTags
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.learn.OpenLines
import com.chesstutor.app.domain.repertoire.LevelProgress
import com.chesstutor.app.domain.repertoire.Mastery
import com.chesstutor.app.domain.repertoire.RepertoireIndex
import com.chesstutor.app.domain.repertoire.RepertoireLevel
import com.chesstutor.app.domain.repertoire.TestRepertoire
import com.chesstutor.app.domain.repertoire.TestRepertoire.P
import com.github.takahirom.roborazzi.captureRoboImage
import com.chesstutor.app.domain.repertoire.Variation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneOffset

/** The opening trainer's page, runs and mastery sheet (Phase 10, mock-up round 2), at phone size. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class RepertoireScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val book = TestRepertoire.italian()
    private val index = RepertoireIndex(book)

    private fun pageUi(): RepertoireBookUi = RepertoireBookUi(
        book = book,
        index = index,
        levels = LevelProgress(book.id),
        mastery = RepertoireLevel.entries.associateWith { Mastery.of(book, it, emptyMap(), emptyList(), ZoneOffset.UTC) },
        due = 12,
        tricky = 3,
        fresh = 5,
        chart = ReplyChart("3.Bc4", listOf(ChartReply("Bc5", 0.44, false), ChartReply("Nf6", 0.35, false), ChartReply("Nd4", 0.06, true), ChartReply("Be7", 0.05, false))),
        introduced = 4,
        total = 9,
        variations = listOf(
            Variation("$P f8c5".split(' '), "3...Bc5", "Giuoco Piano", 0.44, cards = 4, introduced = 3, solid = 2),
            Variation("$P g8f6".split(' '), "3...Nf6", "Two Knights Defense", 0.35, cards = 3, introduced = 0, solid = 0),
            Variation("$P c6d4".split(' '), "3...Nd4", null, 0.06, cards = 0, introduced = 0, solid = 0),
        ),
        readiness = 0.42,
    )

    @Test
    fun thePageLeadsWithReviewAndShowsTheCupsAndTheVariations() {
        var reviewed = false
        var learnt: List<String>? = null
        compose.setContent {
            CtTheme(reduceMotion = true) {
                RepertoirePage(
                    pageUi(),
                    RepertoireActions(onBack = {}, onReview = { reviewed = true }, onLearn = {}, onTricky = {}, onPlayOut = {}, onLearnVariation = { learnt = it }, onIdeas = {}),
                )
            }
        }
        compose.onNodeWithText("Italian Game").assertExists()
        compose.onNodeWithText("Tricky lines").assertExists()
        compose.onNodeWithText("42%").assertExists()
        compose.onNodeWithText("Ideas").assertExists()
        compose.onNodeWithText("Two Knights Defense").performScrollTo().performClick()
        assertEquals("$P g8f6".split(' '), learnt)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/repertoire_page_variations.png")
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/repertoire_page.png")
        compose.onNodeWithText("Review 12").performClick()
        assertEquals(true, reviewed)
    }

    @Test
    fun aLockedOpeningSendsTheLearnerToTheCourseOrTheTest() {
        var course = false
        var test = false
        val locked = pageUi().let { ui ->
            ui.copy(
                due = 0, fresh = 0, introduced = 0, readiness = 0.0,
                open = OpenLines(emptyList(), ui.variations.map { it.path }, rest = false),
                variations = ui.variations.map { it.copy(locked = true) },
            )
        }
        compose.setContent {
            CtTheme(reduceMotion = true) {
                RepertoirePage(locked, RepertoireActions(onBack = {}, onReview = {}, onLearn = {}, onTricky = {}, onCourse = { course = true }, onTest = { test = true }))
            }
        }
        compose.onNodeWithText("Review", substring = true).assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/repertoire_page_locked.png")
        compose.onNodeWithText("Take the course").performClick()
        compose.onNodeWithText("Know it already? Take the test").performClick()
        assertEquals(true, course)
        assertEquals(true, test)
    }

    @Test
    fun thePageInTheDark() {
        compose.setContent {
            CtTheme(theme = AppTheme.PARCHMENT, appearance = Appearance.DARK, reduceMotion = true) {
                RepertoirePage(pageUi(), RepertoireActions(onBack = {}, onReview = {}, onLearn = {}, onTricky = {}, onPlayOut = {}))
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/repertoire_page_dark.png")
    }

    private fun runUi(moment: RunMoment, line: List<String>, mastered: RepertoireLevel? = null, ring: String? = null, why: String? = null): RunUi {
        val board = RepertoireBoard(line = { line to (moment == RunMoment.YourMove) }, submit = { MoveEffects.ILLEGAL })
        return RunUi(
            kind = RunKind.LEARN, title = "Two Knights Defense", side = book.side, board = board, moment = moment,
            against = if (moment == RunMoment.YourMove) "3...Nf6" else null, step = 2, steps = 5, tricky = false,
            mastered = mastered, masteredBook = "Italian Game", nextLevelCards = 55, moreToLearn = true,
            ringSquare = ring, why = why, canWhy = why != null,
        )
    }

    @Test
    fun theDrillFlowsAndTheBoardStaysPutFromQuestionToSlipToTheCup() {
        val toNf6 = "$P g8f6".split(' ')
        var shown by mutableStateOf(runUi(RunMoment.YourMove, toNf6))
        compose.setContent {
            CtTheme(reduceMotion = true) {
                RepertoireRunScreen(
                    run = shown, settings = AppSettings(), onFeedback = {}, onHint = {}, onWhy = {},
                    onLearnMore = {}, onDismissMastered = {}, onBack = {},
                )
            }
        }
        compose.onNodeWithText("Your move").assertExists()
        compose.onNodeWithText("Why").assertExists()
        val board = compose.onNodeWithTag(CtBoardScreenTags.BOARD).getBoundsInRoot()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/repertoire_learn.png")

        // The watch: the learner's next move as an arrow, with its note's first sentence.
        shown = runUi(RunMoment.Watch("d2d3", "4.d3 protects e4, so the knight on f6 has nothing to take."), toNf6)
        compose.waitForIdle()
        compose.onNodeWithText("4.d3 protects e4, so the knight on f6 has nothing to take.").assertExists()
        compose.onNodeWithText("Skip").assertExists()
        assertEquals(board, compose.onNodeWithTag(CtBoardScreenTags.BOARD).getBoundsInRoot())
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/repertoire_watch.png")

        // Why opens the whole note under the learner's last move.
        val note = "4.d3 protects e4, so the knight on f6 has nothing to take. This is the quiet way."
        shown = runUi(RunMoment.YourMove, toNf6 + "d2d3" + "f8c5", ring = "d3", why = note)
        compose.waitForIdle()
        compose.onNodeWithText(note).assertExists()
        assertEquals(board, compose.onNodeWithTag(CtBoardScreenTags.BOARD).getBoundsInRoot())
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/repertoire_why.png")

        // A slip: the right move is drawn and named, nothing to tap.
        shown = runUi(RunMoment.Shown("4.d3", "d2d3"), toNf6, ring = "a3")
        compose.waitForIdle()
        compose.onNodeWithText("Play 4.d3").assertExists()
        assertEquals(board, compose.onNodeWithTag(CtBoardScreenTags.BOARD).getBoundsInRoot())
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/repertoire_slip.png")

        shown = runUi(RunMoment.Finished(right = 5, total = 5), toNf6 + "d2d3")
        compose.waitForIdle()
        compose.onNodeWithText("Line learnt").assertExists()
        compose.onNodeWithText("Next line").assertExists()
        assertEquals(board, compose.onNodeWithTag(CtBoardScreenTags.BOARD).getBoundsInRoot())
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/repertoire_done.png")

        shown = runUi(RunMoment.Finished(right = 5, total = 5), toNf6 + "d2d3", mastered = RepertoireLevel.CORE)
        compose.waitForIdle()
        compose.onNodeWithText("Core mastered").assertExists()
        compose.onNodeWithText("Standard is open: 55 new positions.").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/repertoire_mastered.png")
    }
}
