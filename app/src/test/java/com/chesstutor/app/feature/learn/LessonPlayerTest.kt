package com.chesstutor.app.feature.learn

import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.learn.InMemoryLessonProgressRepository
import com.chesstutor.app.domain.learn.Lesson
import com.chesstutor.app.domain.learn.LessonParser
import com.chesstutor.app.domain.learn.LessonPhase
import com.chesstutor.app.domain.learn.LessonXp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LessonPlayerTest {

    private val start = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    private val course = LessonParser.parseCourse(
        """
        { "id": "c", "category": "basics", "title": "Course", "lessons": [ { "id": "l", "title": "Lesson", "steps": [
          { "type": "explain", "text": "Watch e4", "fen": "$start", "move": "e2e4", "arrows": ["d2d4:idea"] },
          { "type": "play", "text": "Answer in the centre", "expect": ["e7e5"], "ifWrong": "Centre!", "hint": "Mirror it" },
          { "type": "quiz", "text": "Play the line", "fen": "$start", "line": ["e2e4", "e7e5", "g1f3"] }
        ] } ] }
        """.trimIndent(),
    )
    private val lesson: Lesson = course.lessons.single()
    private val timing = LessonTiming(demonstrationMs = 100, replyMs = 100, mistakeFlashMs = 300)

    private fun TestScope.player(repo: InMemoryLessonProgressRepository, startStep: Int = 0) =
        LessonPlayer(course, lesson, repo, backgroundScope, startStep = startStep, timing = timing)

    private fun TestScope.settle(ms: Long = 1_000) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private fun LessonPlayer.move(uci: String) = board.onMove(Move.fromUci(uci))

    @Test
    fun playsALessonFromExplanationToDone() = runTest {
        val repo = InMemoryLessonProgressRepository()
        val p = player(repo)
        p.start()
        assertTrue("the demonstration move is about to slide", p.state.value.busy)
        assertFalse(p.state.value.canAdvance)
        settle()
        assertEquals("e2e4", p.board.state.value.lastMove?.uci)
        assertTrue(p.state.value.canAdvance)
        assertEquals(LessonPhase.LEARN, p.state.value.phase)

        p.next()
        assertEquals(LessonPhase.TRY, p.state.value.phase)
        assertEquals(PieceColor.BLACK, p.board.state.value.movable)
        assertEquals(PieceColor.BLACK, p.state.value.orientation)

        p.move("d7d5")
        assertEquals("Centre!", p.state.value.coachText)
        assertEquals(FeedbackTone.BAD, p.state.value.feedbackTone)
        assertEquals("d5", p.state.value.mistakeSquare)
        settle()
        assertNull("the mistake ring fades", p.state.value.mistakeSquare)
        assertEquals("Centre!", p.state.value.coachText)

        p.hint()
        assertEquals("Mirror it", p.state.value.coachText)
        assertEquals("e7", p.state.value.hint?.from)

        p.move("e7e5")
        assertEquals(LessonPlayer.DEFAULT_RIGHT, p.state.value.coachText)
        assertTrue(p.state.value.canAdvance)
        assertNull("nobody moves once the step is solved", p.board.state.value.movable)

        p.next()
        assertEquals(1 to 1, p.state.value.challenge)
        p.move("e2e4")
        assertTrue("waiting for the reply", p.state.value.busy)
        assertNull(p.board.state.value.movable)
        settle()
        assertEquals("e7e5", p.board.state.value.lastMove?.uci)
        assertEquals(PieceColor.WHITE, p.board.state.value.movable)
        p.move("g1f3")
        assertTrue(p.state.value.isLastStep)
        p.next()
        settle()

        val done = p.state.value
        assertEquals(LessonPhase.DONE, done.phase)
        assertNotNull(done.completion)
        // The only challenge was solved cleanly, so the perfect bonus comes on top.
        assertEquals(Lesson.DEFAULT_XP + LessonXp.PERFECT_BONUS, done.completion!!.xp)
        assertEquals(listOf(Lesson.DEFAULT_XP + LessonXp.PERFECT_BONUS), repo.xpAwards)
        assertTrue(repo.get("l")!!.completed)
        assertEquals(0, repo.get("l")!!.stepIndex)
    }

    @Test
    fun savesTheStepToResumeAt() = runTest {
        val repo = InMemoryLessonProgressRepository()
        val p = player(repo, startStep = 1)
        p.start()
        settle()
        assertEquals(1, repo.get("l")!!.stepIndex)
        assertEquals(3, repo.get("l")!!.stepCount)
        p.move("e7e5")
        p.next()
        settle()
        assertEquals(2, repo.get("l")!!.stepIndex)
    }

    @Test
    fun againReplaysTheDemonstration() = runTest {
        val p = player(InMemoryLessonProgressRepository())
        p.start()
        settle()
        p.replayStep()
        assertEquals("the board jumps back to the start", null, p.board.state.value.lastMove)
        settle()
        assertEquals("e2e4", p.board.state.value.lastMove?.uci)
    }
}
