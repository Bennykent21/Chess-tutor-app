package com.chesstutor.app.domain.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Looking back in a lesson: a step at a time, then the lesson before in the course. */
class LessonBackTest {

    private val course = LessonParser.parseCourse(File("src/main/assets/lessons/basics-check-and-mate.json").readText())
    private val start = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    private val explain = LessonStep.Explain(text = "Look", fen = start)
    private val play = LessonStep.Play(text = "Your go", fen = start, turns = listOf(PlayerTurn(accepted = listOf("e2e4"))))
    private val lesson = Lesson(id = "l", title = "L", summary = "", steps = listOf(explain, play, explain))

    @Test
    fun aStepBackReturnsToTheStepBefore() {
        val session = LessonSession(lesson)
        session.advance()
        assertTrue(session.stepBack())
        assertEquals(0, session.stepIndex)
    }

    @Test
    fun theFirstStepHasNothingBeforeIt() {
        assertFalse(LessonSession(lesson).stepBack())
    }

    @Test
    fun aChallengeAlreadyPassedStaysOpenWhenLookingBack() {
        val session = LessonSession(lesson)
        session.advance()
        assertFalse("a new challenge must be solved first", session.canAdvance)
        session.submit("e2e4")
        session.advance()
        session.stepBack()
        assertEquals(1, session.stepIndex)
        assertTrue(session.canAdvance)
    }

    @Test
    fun lookingBackDoesNotCountAChallengeTwice() {
        val quiz = Lesson(id = "q", title = "Q", summary = "", steps = listOf(LessonStep.Play(text = "Go", fen = start, turns = listOf(PlayerTurn(accepted = listOf("e2e4"))), quiz = true), explain))
        val session = LessonSession(quiz)
        session.submit("e2e4")
        session.advance()
        session.stepBack()
        session.submit("e2e4")
        assertEquals(1, session.result().challenges.size)
    }

    @Test
    fun thePreviousLessonStaysInTheCourse() {
        val library = LessonLibrary(listOf(course))
        val first = course.lessons.first()
        val second = course.lessons[1]
        assertEquals(first, library.previousIn(second.id))
        assertNull("the course's first lesson has none before it", library.previousIn(first.id))
        assertNull(library.previousIn("no such lesson"))
    }
}
