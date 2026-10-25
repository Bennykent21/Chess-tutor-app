package com.chesstutor.app.domain.learn

import com.chesstutor.app.core.PieceColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LessonParserTest {

    private val start = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    private fun course(steps: String, lessonExtra: String = "", courseExtra: String = "") = """
        { "id": "c", "category": "openings", "title": "Course", "level": "intermediate", "order": 3 $courseExtra,
          "lessons": [ { "id": "l1", "title": "Lesson" $lessonExtra, "steps": [ $steps ] } ] }
    """.trimIndent()

    private fun failsWith(json: String, fragment: String) {
        try {
            LessonParser.parseCourse(json, "test.json")
            fail("expected a LessonFormatException containing \"$fragment\"")
        } catch (e: LessonFormatException) {
            assertTrue("message was: ${e.message}", e.message!!.contains(fragment))
        }
    }

    @Test
    fun readsACourseWithEveryStepType() {
        val parsed = LessonParser.parseCourse(
            course(
                """
                { "type": "watch", "title": "Intro", "text": "A video" },
                { "type": "explain", "text": "e4 first", "fen": "$start", "move": "e2e4", "arrows": ["e2e4", "d2d4:idea"], "highlights": ["e4", "d5:bad"] },
                { "type": "play", "text": "Answer", "expect": ["e7e5", "c7c5"], "hint": "Centre", "ifWrong": "No", "ifRight": "Yes", "feedback": { "a7a6": "Too slow" } },
                { "type": "quiz", "text": "Develop", "line": ["g1f3", "b8c6", "f1c4|f1b5"] }
                """,
                courseExtra = """, "puzzleThemes": ["opening"], "battleLine": "Italian Game", "side": "white" """,
            ),
        )
        assertEquals("c", parsed.id)
        assertEquals(LessonCategory.OPENINGS, parsed.category)
        assertEquals(LessonLevel.INTERMEDIATE, parsed.level)
        assertEquals(3, parsed.order)
        assertEquals(listOf("opening"), parsed.puzzleThemes)
        assertEquals("Italian Game", parsed.battleLine)
        assertEquals(PieceColor.WHITE, parsed.side)

        val lesson = parsed.lessons.single()
        assertEquals(Lesson.DEFAULT_XP, lesson.xp)
        assertEquals(1, lesson.quizCount)
        val explain = lesson.steps[1] as LessonStep.Explain
        assertEquals(listOf(LessonArrow("e2", "e4"), LessonArrow("d2", "d4", MarkTone.IDEA)), explain.arrows)
        assertEquals(listOf(LessonHighlight("e4"), LessonHighlight("d5", MarkTone.BAD)), explain.highlights)

        // The play step carries on from after 1.e4, so Black is to move and the board faces Black.
        val play = lesson.steps[2] as LessonStep.Play
        assertTrue(play.fen.startsWith("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b"))
        assertEquals(listOf(PlayerTurn(listOf("e7e5", "c7c5"))), play.turns)
        assertEquals("Too slow", play.feedback["a7a6"])
        assertEquals(PieceColor.BLACK, lesson.orientation)

        // The quiz carries on after the first expected move, 1...e5; alternatives split on "|".
        val quiz = lesson.steps[3] as LessonStep.Play
        assertTrue(quiz.quiz)
        assertTrue(quiz.fen.startsWith("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w"))
        assertEquals(listOf(PlayerTurn(listOf("g1f3"), "b8c6"), PlayerTurn(listOf("f1c4", "f1b5"))), quiz.turns)
    }

    @Test
    fun goalStepsAreCheckedForASolution() {
        val parsed = LessonParser.parseCourse(course("""{ "type": "quiz", "text": "Mate", "fen": "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", "goal": "mate" }"""))
        assertEquals(MoveGoal.MATE, (parsed.lessons[0].steps[0] as LessonStep.Play).goal)
        failsWith(course("""{ "type": "quiz", "text": "Mate", "fen": "$start", "goal": "mate" }"""), "no move reaches the goal")
    }

    @Test
    fun aStepAfterAGoalMustGiveItsOwnPosition() {
        failsWith(
            course(
                """
                { "type": "play", "text": "Mate", "fen": "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", "goal": "mate" },
                { "type": "explain", "text": "Then" }
                """,
            ),
            "step 2: needs a \"fen\"",
        )
    }

    @Test
    fun orientationCanBeSetAndXpLimited() {
        val parsed = LessonParser.parseCourse(course("""{ "type": "explain", "text": "Hi", "fen": "$start" }""", lessonExtra = """, "orientation": "black", "xp": 35"""))
        assertEquals(PieceColor.BLACK, parsed.lessons[0].orientation)
        assertEquals(35, parsed.lessons[0].xp)
        assertNull(parsed.side)
        failsWith(course("""{ "type": "explain", "text": "Hi", "fen": "$start" }""", lessonExtra = """, "xp": 500"""), "xp must be")
    }

    @Test
    fun brokenFilesSayWhereAndWhy() {
        failsWith("{ not json", "not valid JSON")
        failsWith(course("""{ "type": "explain", "text": "Hi" }"""), "the first step needs a \"fen\"")
        failsWith(course("""{ "type": "explain", "text": "Hi", "fen": "8/8/8/8/8/8/8/8 w - - 0 1" }"""), "bad fen")
        failsWith(course("""{ "type": "dance", "text": "Hi", "fen": "$start" }"""), "unknown step type \"dance\"")
        failsWith(course("""{ "type": "explain", "text": "Hi", "fen": "$start", "move": "e2e5" }"""), "move \"e2e5\" is not legal")
        failsWith(course("""{ "type": "play", "text": "Go", "fen": "$start", "expect": ["e2e5"] }"""), "expected move \"e2e5\" is not legal")
        failsWith(course("""{ "type": "play", "text": "Go", "fen": "$start", "line": ["e2e4", "e7e4"] }"""), "reply \"e7e4\"")
        failsWith(course("""{ "type": "play", "text": "Go", "fen": "$start" }"""), "give exactly one of")
        failsWith(course("""{ "type": "play", "text": "Go", "fen": "$start", "expect": ["e2e4"], "goal": "check" }"""), "give exactly one of")
        failsWith(course("""{ "type": "play", "text": "Go", "fen": "$start", "expect": ["e2e4"], "feedback": { "e2e5": "x" } }"""), "feedback move \"e2e5\"")
        failsWith(course("""{ "type": "explain", "text": "Hi", "fen": "$start", "arrows": ["e2e9"] }"""), "\"e9\" is not a square")
        failsWith(course("""{ "type": "explain", "text": "Hi", "fen": "$start", "highlights": ["e4:pink"] }"""), "unknown mark colour")
        failsWith(course("""{ "type": "play", "fen": "$start", "expect": ["e2e4"] }"""), "needs \"text\"")
        failsWith(course("""{ "type": "explain", "text": "Hi", "fen": "$start" }""").replace("openings", "cooking"), "unknown category")
        failsWith(
            """{ "id": "c", "category": "basics", "title": "C", "lessons": [
                 { "id": "a", "title": "A", "steps": [ { "type": "explain", "text": "x", "fen": "$start" } ] },
                 { "id": "a", "title": "B", "steps": [ { "type": "explain", "text": "x", "fen": "$start" } ] } ] }""",
            "used twice",
        )
    }

    @Test
    fun libraryFindsLessonsAndTheNextOne() {
        fun c(id: String, order: Int, vararg lessons: String) = Course(
            id = id, category = LessonCategory.TACTICS, title = id, intro = "", level = LessonLevel.BEGINNER, order = order,
            lessons = lessons.map { Lesson(it, it, "", listOf(LessonStep.Explain("x", start))) },
        )
        val library = LessonLibrary(listOf(c("forks", 2, "f1", "f2"), c("pins", 1, "p1")))
        assertEquals(listOf("pins", "forks"), library.coursesIn(LessonCategory.TACTICS).map { it.id })
        assertEquals("f2", library.nextAfter("f1")?.id)
        assertEquals("f1", library.nextAfter("p1")?.id)
        assertNull(library.nextAfter("f2"))
        assertEquals("forks", library.courseOf("f2")?.id)
    }
}
