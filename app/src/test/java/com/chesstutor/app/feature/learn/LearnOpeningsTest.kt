package com.chesstutor.app.feature.learn

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.design.Appearance
import com.chesstutor.app.design.AppTheme
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.learn.Course
import com.chesstutor.app.domain.learn.Lesson
import com.chesstutor.app.domain.learn.LessonCategory
import com.chesstutor.app.domain.learn.LessonLevel
import com.chesstutor.app.domain.learn.LessonProgress
import com.chesstutor.app.domain.learn.LessonStep
import com.chesstutor.app.domain.learn.OpenLines
import com.chesstutor.app.domain.repertoire.LevelProgress
import com.chesstutor.app.domain.repertoire.RepertoireIndex
import com.chesstutor.app.domain.repertoire.RepertoireVariations
import com.chesstutor.app.domain.repertoire.TestRepertoire
import com.chesstutor.app.domain.repertoire.TestRepertoire.P
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Learn's openings (Phase 12, design round 4): by side, and one opening's courses, at phone size. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class LearnOpeningsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun lesson(id: String) = Lesson(id, id, "", listOf(LessonStep.Explain("A move.", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")))

    private fun course(id: String, title: String, opening: String?, variation: String?, lessons: Int, side: PieceColor, order: Int) = Course(
        id = id, category = LessonCategory.OPENINGS, title = title, intro = "", level = LessonLevel.BEGINNER, order = order,
        lessons = (1..lessons).map { lesson("$id-$it") }, side = side, opening = opening,
        variations = listOfNotNull(variation?.split(' ')),
    )

    private val book = TestRepertoire.italian()
    private val index = RepertoireIndex(book)
    private val open = OpenLines(listOf("$P f8c5".split(' ')), taught = listOf("$P f8c5".split(' '), "$P g8f6".split(' ')), rest = false)
    private val bookUi = RepertoireBookUi(
        book = book, index = index, levels = LevelProgress(book.id), mastery = emptyMap(), due = 0, tricky = 0, fresh = 2, chart = null,
        variations = RepertoireVariations.of(index, null, com.chesstutor.app.domain.repertoire.RepertoireLevel.CORE, emptyMap(), open), open = open,
    )
    private val courses = listOf(
        course("italian-ideas", "Italian Game", "italian", null, 2, PieceColor.WHITE, 10),
        course("italian-giuoco", "Giuoco Piano", "italian", "$P f8c5", 4, PieceColor.WHITE, 11),
        course("italian-two-knights", "Two Knights", "italian", "$P g8f6", 5, PieceColor.WHITE, 12),
        course("london", "London System", null, null, 1, PieceColor.WHITE, 20),
        course("caro", "Caro-Kann Defence", null, null, 1, PieceColor.BLACK, 30),
    )
    private val progress = (1..4).associate { "italian-giuoco-$it" to LessonProgress("italian-giuoco-$it", completed = true) } +
        ("italian-ideas-1" to LessonProgress("italian-ideas-1", completed = true))

    @Test
    fun openingsAreGroupedAndShownOneSideAtATime() {
        val groups = OpeningGroup.of(courses, listOf(bookUi))
        assertEquals(listOf("italian", "london", "caro"), groups.map { it.id })
        assertEquals("Italian Game", groups.first().title)
        assertEquals(2, groups.first().variations.size)
        var opened: String? = null
        compose.setContent {
            CtTheme(reduceMotion = true) { OpeningsScreen(groups, progress, BoardTheme(), onOpen = { opened = it.id }, onBack = {}) }
        }
        compose.onNodeWithText("3 courses").assertExists()
        compose.onNodeWithText("Caro-Kann Defence").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/learn_openings.png")
        compose.onNodeWithText("As Black").performClick()
        compose.onNodeWithText("Caro-Kann Defence").performClick()
        assertEquals("caro", opened)
    }

    @Test
    fun anOpeningListsItsIdeasThenItsVariationsWithTheirLocks() {
        val group = OpeningGroup.of(courses, listOf(bookUi)).first()
        var test = false
        var course: String? = null
        compose.setContent {
            CtTheme(reduceMotion = true) { LearnOpeningScreen(group, progress, onOpenCourse = { course = it.id }, onTest = { test = true }, onBack = {}) }
        }
        compose.onNodeWithText("The ideas").assertExists()
        compose.onNodeWithText("Drills open").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/learn_opening.png")
        compose.onNodeWithText("Two Knights").performClick()
        assertEquals("italian-two-knights", course)
        // Part of the opening is still closed, so the test is offered.
        compose.onNodeWithText("Know it already? Take the test").assertDoesNotExist()
        assertEquals(false, test)
    }

    @Test
    fun aLockedOpeningOffersTheTestInTheDark() {
        val locked = bookUi.copy(open = open.copy(open = emptyList()))
        val group = OpeningGroup.of(courses, listOf(locked)).first()
        var test = false
        compose.setContent {
            CtTheme(theme = AppTheme.PARCHMENT, appearance = Appearance.DARK, reduceMotion = true) {
                LearnOpeningScreen(group, emptyMap(), onOpenCourse = {}, onTest = { test = true }, onBack = {})
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/learn_opening_dark.png")
        compose.onNodeWithText("Know it already? Take the test").performClick()
        assertEquals(true, test)
    }
}
