package com.chesstutor.app.feature.learn

import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.learn.LessonAssetLoader
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.learn.Course
import com.chesstutor.app.domain.learn.LessonCategory
import com.chesstutor.app.domain.learn.LessonLevel
import com.chesstutor.app.domain.learn.LessonParser
import com.chesstutor.app.domain.learn.LessonProgress
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The Learn hub, a category page and a course page (step 4B). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1300dp-xxhdpi")
class LearnPagesTest {

    @get:Rule
    val compose = createComposeRule()

    private val checkAndMate = LessonParser.parseCourse(File("src/main/assets/lessons/basics-check-and-mate.json").readText())

    /** Extra openings courses, so the category page has something to search and filter. */
    private fun opening(id: String, title: String, level: LessonLevel, side: PieceColor, intro: String) =
        checkAndMate.copy(
            id = id, category = LessonCategory.OPENINGS, title = title, intro = intro, level = level, side = side,
            lessons = checkAndMate.lessons.map { it.copy(id = "$id-${it.id}") },
        )

    private val courses: List<Course> = listOf(
        checkAndMate,
        opening("italian", "Italian Game", LessonLevel.BEGINNER, PieceColor.WHITE, "Quick development and pressure on f7."),
        opening("sicilian", "Sicilian Defence", LessonLevel.INTERMEDIATE, PieceColor.BLACK, "Fight for the centre from the side."),
        opening("london", "London System", LessonLevel.BEGINNER, PieceColor.WHITE, "One solid set-up against everything."),
        opening("caro", "Caro-Kann", LessonLevel.INTERMEDIATE, PieceColor.BLACK, "Rock-solid and reliable."),
    )
    private val learn = LearnState(
        loading = false,
        library = LessonAssetLoader.library(courses),
        progress = mapOf(
            "basics-check" to LessonProgress("basics-check", completed = true),
            "basics-checkmate" to LessonProgress("basics-checkmate", stepIndex = 2, stepCount = 6),
        ),
    )

    @Test
    fun hubShowsContinueAndCategoryTiles() {
        var opened: String? = null
        var category: LessonCategory? = null
        compose.setContent {
            CtTheme(reduceMotion = true) {
                LearnScreen(
                    learn = learn,
                    actions = LearnHubActions(
                        onOpenCategory = { category = it },
                        onOpenLesson = { opened = it },
                    ),
                )
            }
        }
        compose.onNodeWithText("Lesson ", substring = true).assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/learn_hub.png")
        compose.onNodeWithText("Checkmate wins the game").performClick()
        assertEquals("basics-checkmate", opened)
        compose.onNodeWithText("Openings").performClick()
        assertEquals(LessonCategory.OPENINGS, category)
        compose.onNodeWithText("Endgames").performClick()
        assertEquals("a category with no lessons does not open", LessonCategory.OPENINGS, category)
        compose.onAllNodesWithText("Lessons on the way").assertCountEquals(4)
    }

    @Test
    fun categoryPageSearchesAndFilters() {
        compose.setContent {
            CtTheme(reduceMotion = true) {
                LearnCategoryScreen(LessonCategory.OPENINGS, learn, onOpenCourse = {}, onBack = {})
            }
        }
        compose.onNodeWithText("Italian Game").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/learn_category.png")
        // Side is a dropdown chip: open it and pick Black.
        compose.onNodeWithText("Both sides").performClick()
        compose.onNodeWithText("Black").performClick()
        compose.onNodeWithText("Italian Game").assertDoesNotExist()
        compose.onNodeWithText("Caro-Kann").assertExists()
        compose.onNodeWithText("Black").performClick()
        compose.onNodeWithText("Both sides").performClick()
        // Search waits behind its icon.
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithContentDescription("Search courses").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("solid set")
        compose.onNodeWithText("London System").assertExists()
        compose.onNodeWithText("Caro-Kann").assertDoesNotExist()
    }

    @Test
    fun coursePageListsNumberedLessons() {
        var opened: String? = null
        compose.setContent {
            CtTheme(reduceMotion = true) {
                CourseScreen(
                    course = checkAndMate,
                    progress = learn.progress,
                    onOpenLesson = { opened = it },
                    onBack = {},
                    onRelatedPuzzles = {},
                )
            }
        }
        compose.onAllNodesWithContentDescription("Finished").onFirst().assertExists()
        compose.onNodeWithText("Resume at step 3 of 6").assertExists()
        compose.onNodeWithText("Continue").assertExists()
        compose.onNodeWithText("Related puzzles").assertExists()
        compose.onNodeWithText("Practise in Opening Battle").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/learn_course.png")
        compose.onNodeWithText("What is check?").performClick()
        assertEquals("basics-check", opened)
    }
}
