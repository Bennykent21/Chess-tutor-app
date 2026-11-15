package com.chesstutor.app.domain.learn

import com.chesstutor.app.core.PieceColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseGateTest {
    private fun lesson(id: String) = Lesson(id, id, "", listOf(LessonStep.Explain("A move.", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")))

    private fun course(id: String, opening: String?, variations: List<String>, lessons: Int = 2) = Course(
        id = id, category = LessonCategory.OPENINGS, title = id, intro = "", level = LessonLevel.BEGINNER, order = 0,
        lessons = (1..lessons).map { lesson("$id-$it") }, side = PieceColor.BLACK, opening = opening,
        variations = variations.map { it.split(' ') },
    )

    private val advance = "e2e4 c7c6 d2d4 d7d5 e4e5"
    private val exchange = "e2e4 c7c6 d2d4 d7d5 e4d5"
    private val library = LessonLibrary(
        listOf(
            course("caro-ideas", "caro-kann", emptyList()),
            course("caro-advance", "caro-kann", listOf(advance)),
            course("caro-exchange", "caro-kann", listOf(exchange)),
            course("french", null, emptyList()),
        ),
    )

    private fun done(vararg ids: String) = ids.associateWith { LessonProgress(it, completed = true) }

    @Test
    fun aFinishedCourseOpensItsVariationOnly() {
        val opened = CourseGate.opened(library, done("caro-advance-1", "caro-advance-2", "caro-exchange-1"), emptySet())
        assertEquals(listOf(advance.split(' ')), opened["caro-kann"]!!.open)
        assertNull("an opening without variation courses stays open", opened["french"])
        val open = opened["caro-kann"]
        assertTrue(CourseGate.isOpen("e2e4 c7c6 d2d4 d7d5 e4e5 c8f5 g1f3".split(' '), open))
        assertTrue("the moves leading into it are open too", CourseGate.isOpen("e2e4 c7c6".split(' '), open))
        assertFalse(CourseGate.isOpen(exchange.split(' '), open))
        assertTrue("no gate means open", CourseGate.isOpen(exchange.split(' '), null))
    }

    @Test
    fun aPassedTestOpensTheWholeOpening() {
        assertNull(CourseGate.opened(library, emptyMap(), setOf("caro-kann"))["caro-kann"])
        val closed = CourseGate.opened(library, emptyMap(), emptySet())["caro-kann"]!!
        assertEquals(OpenLines(emptyList(), listOf(advance.split(' '), exchange.split(' ')), rest = false), closed)
    }

    @Test
    fun linesNoCourseTeachesOpenWithTheIdeasCourse() {
        val fantasy = "e2e4 c7c6 d2d4 d7d5 f2f3".split(' ')
        assertFalse(CourseGate.isOpen(fantasy, CourseGate.opened(library, emptyMap(), emptySet())["caro-kann"]))
        val lines = CourseGate.opened(library, done("caro-ideas-1", "caro-ideas-2"), emptySet())["caro-kann"]!!
        assertTrue(CourseGate.isOpen(fantasy, lines))
        assertTrue("the start leads into it", CourseGate.isOpen("e2e4 c7c6".split(' '), lines))
        assertFalse("a taught variation still waits for its course", CourseGate.isOpen(advance.split(' '), lines))
    }

    @Test
    fun aCourseIsDoneWhenEveryLessonIs() {
        val advanceCourse = library.course("caro-advance")!!
        assertFalse(CourseGate.isDone(advanceCourse, done("caro-advance-1")))
        assertTrue(CourseGate.isDone(advanceCourse, done("caro-advance-1", "caro-advance-2")))
    }

    @Test
    fun passedTestsAreKept() {
        val tests = InMemoryOpeningTests()
        tests.pass("italian")
        tests.pass("italian")
        assertEquals(setOf("italian"), tests.passed().value)
    }
}
