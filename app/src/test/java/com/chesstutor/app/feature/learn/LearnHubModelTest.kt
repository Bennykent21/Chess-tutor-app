package com.chesstutor.app.feature.learn

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.domain.learn.Course
import com.chesstutor.app.domain.learn.Lesson
import com.chesstutor.app.domain.learn.LessonCategory
import com.chesstutor.app.domain.learn.LessonLevel
import com.chesstutor.app.domain.learn.LessonLibrary
import com.chesstutor.app.domain.learn.LessonParser
import com.chesstutor.app.domain.learn.LessonProgress
import com.chesstutor.app.domain.learn.LessonStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnHubModelTest {

    private val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    private fun lesson(id: String, title: String = id) = Lesson(id, title, "", listOf(LessonStep.Explain("x", fen)))

    private fun course(
        id: String,
        category: LessonCategory,
        vararg lessons: String,
        level: LessonLevel = LessonLevel.BEGINNER,
        side: PieceColor? = null,
        order: Int = 0,
        intro: String = "",
    ) = Course(id, category, id.replaceFirstChar { it.uppercase() }, intro, level, order, lessons.map { lesson(it) }, side = side)

    private val library = LessonLibrary(
        listOf(
            course("pieces", LessonCategory.BASICS, "b1", "b2", order = 1),
            course("mates", LessonCategory.BASICS, "b3", order = 2),
            course("italian", LessonCategory.OPENINGS, "o1", "o2", side = PieceColor.WHITE, intro = "Aim at f7"),
            course("sicilian", LessonCategory.OPENINGS, "o3", side = PieceColor.BLACK, level = LessonLevel.INTERMEDIATE),
            course("london", LessonCategory.OPENINGS, "o4", side = PieceColor.WHITE, level = LessonLevel.INTERMEDIATE),
        ),
    )

    @Test
    fun tilesCountLessonsPerCategory() {
        val progress = mapOf(
            "b1" to LessonProgress("b1", completed = true),
            "b2" to LessonProgress("b2", stepIndex = 2),
        )
        val tiles = LearnHubModel.tiles(library, progress)
        assertEquals(listOf("Basics", "Openings", "Tactics", "Middlegame", "Endgames", "Blunders"), tiles.map { it.title })

        val basics = tiles.first()
        assertEquals(3, basics.totalLessons)
        assertEquals(1, basics.completedLessons)
        assertEquals(1, basics.startedLessons)
        assertEquals(1f / 3, basics.progress, 0.0001f)
        assertEquals(4, tiles[1].totalLessons)
        assertFalse("no lessons in this category", tiles[2].isAvailable)
        assertEquals(1f / 7, LearnHubModel.overallProgress(tiles), 0.0001f)
    }

    @Test
    fun everyCategoryHasBundledLessons() {
        val files = java.io.File("src/main/assets/lessons").listFiles { f -> f.extension == "json" }.orEmpty()
        val bundled = LessonLibrary(files.map { LessonParser.parseCourse(it.readText(), it.name) })
        val tiles = LearnHubModel.tiles(bundled, emptyMap())
        tiles.forEach { assertTrue("${it.title} has no lessons", it.isAvailable) }
        assertTrue("about 40 lessons (plan section 11)", tiles.sumOf { it.totalLessons } >= 40)
    }

    @Test
    fun continueCardPrefersAStartedLessonThenTheNextNewOne() {
        assertEquals("b1", LearnHubModel.continueLesson(library, emptyMap())?.second?.id)
        val progress = mapOf(
            "b1" to LessonProgress("b1", completed = true),
            "o3" to LessonProgress("o3", stepIndex = 1),
        )
        assertEquals("o3", LearnHubModel.continueLesson(library, progress)?.second?.id)
        assertEquals("b2", LearnHubModel.continueLesson(library, progress - "o3")?.second?.id)
        val everything = library.courses.flatMap { it.lessons }.associate { it.id to LessonProgress(it.id, completed = true) }
        assertNull(LearnHubModel.continueLesson(library, everything))
    }

    @Test
    fun searchAndChipsFilterTheCategoryPage() {
        val openings = library.coursesIn(LessonCategory.OPENINGS)
        assertTrue(LearnHubModel.offersSideFilter(openings))
        assertFalse(LearnHubModel.offersSideFilter(library.coursesIn(LessonCategory.BASICS)))

        fun ids(filter: CourseFilter) = LearnHubModel.filter(openings, filter).map { it.id }
        assertEquals(listOf("italian", "sicilian", "london"), ids(CourseFilter()))
        assertEquals(listOf("italian"), ids(CourseFilter(query = "  F7 ")))
        assertEquals(listOf("sicilian", "london"), ids(CourseFilter(level = LessonLevel.INTERMEDIATE)))
        assertEquals(listOf("london"), ids(CourseFilter(level = LessonLevel.INTERMEDIATE, side = PieceColor.WHITE)))
        assertEquals(listOf("sicilian"), ids(CourseFilter(query = "o3")))
        assertEquals(emptyList<String>(), ids(CourseFilter(query = "dragon")))
    }

    @Test
    fun countsAndStatuses() {
        val progress = mapOf(
            "o1" to LessonProgress("o1", completed = true),
            "o2" to LessonProgress("o2", stepIndex = 0),
        )
        val italian = library.course("italian")!!
        assertEquals(LessonCount(completed = 1, started = 0, total = 2), LearnHubModel.countOf(italian.lessons, progress))
        assertEquals(LessonStatus.DONE, LearnHubModel.status(italian.lessons[0], progress))
        assertEquals("a row at step 0 that is not finished counts as new", LessonStatus.NEW, LearnHubModel.status(italian.lessons[1], progress))
        assertEquals(0.5f, LearnHubModel.countOf(italian.lessons, progress).progress, 0.0001f)
    }
}
