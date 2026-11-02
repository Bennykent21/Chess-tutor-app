package com.chesstutor.app.domain.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpeningCoursesTest {

    private fun course(id: String, title: String, category: LessonCategory = LessonCategory.OPENINGS) =
        Course(id = id, category = category, title = title, intro = "", level = LessonLevel.BEGINNER, order = 0, lessons = emptyList())

    private val courses = listOf(
        course("openings-italian", "Italian Game"),
        course("openings-sicilian", "Sicilian Defence"),
        course("openings-queens-gambit", "Queen’s Gambit"),
        course("openings-principles", "Opening principles"),
        course("tactics-forks", "Forks", LessonCategory.TACTICS),
    )

    @Test
    fun databaseFamiliesFindTheirCourse() {
        assertEquals("openings-italian", OpeningCourses.courseFor("Italian Game", courses)?.id)
        assertEquals("American spelling", "openings-sicilian", OpeningCourses.courseFor("Sicilian Defense", courses)?.id)
        assertEquals("a longer family name", "openings-queens-gambit", OpeningCourses.courseFor("Queen's Gambit Declined", courses)?.id)
    }

    @Test
    fun theLongestMatchingTitleWins() {
        val withDeclined = courses + course("openings-qgd", "Queen's Gambit Declined")
        assertEquals("openings-qgd", OpeningCourses.courseFor("Queen's Gambit Declined", withDeclined)?.id)
    }

    @Test
    fun noCourseForOtherFamiliesOrOtherCategories() {
        assertNull(OpeningCourses.courseFor("Queen's Pawn Game", courses))
        assertNull("only whole words match", OpeningCourses.courseFor("Italian Gamesmanship", courses))
        assertNull(OpeningCourses.courseFor("Forks", courses))
    }

    @Test
    fun everyBundledOpeningCourseIsFoundByItsOwnTitle() {
        val library = java.io.File("src/main/assets/lessons").listFiles { f -> f.name.startsWith("openings-") }!!
            .map { LessonParser.parseCourse(it.readText(), it.name) }
        library.forEach { c -> assertEquals(c.id, OpeningCourses.courseFor(c.title, library)?.id) }
    }
}
