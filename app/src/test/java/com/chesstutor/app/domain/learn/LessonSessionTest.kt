package com.chesstutor.app.domain.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonSessionTest {

    private val start = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    private val backRank = "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1"

    private val lesson = LessonParser.parseCourse(
        """
        { "id": "c", "category": "basics", "title": "C", "lessons": [ { "id": "l", "title": "L", "steps": [
          { "type": "explain", "text": "Look", "fen": "$start", "move": "e2e4" },
          { "type": "play", "text": "Reply", "expect": ["e7e5"], "ifWrong": "Centre!", "ifRight": "Good", "feedback": { "c7c5": "Fine too, but play e5", "a7a6": "Too slow" }, "hint": "Mirror it" },
          { "type": "quiz", "text": "Line", "fen": "$start", "line": ["e2e4", "e7e5", "g1f3|b1c3"] },
          { "type": "quiz", "text": "Mate", "fen": "$backRank", "goal": "mate" }
        ] } ] }
        """.trimIndent(),
    ).lessons.single()

    @Test
    fun walksThroughExplainPlayAndQuiz() {
        val s = LessonSession(lesson)
        assertEquals(LessonPhase.LEARN, s.phase)
        assertFalse(s.expectsMove)
        assertTrue(s.canAdvance)
        assertEquals(LessonMoveResult.NotExpected, s.submit("e2e4"))

        assertTrue(s.advance())
        assertEquals(LessonPhase.TRY, s.phase)
        assertTrue(s.expectsMove)
        assertFalse(s.canAdvance)
        assertNull(s.challengeNumber)

        assertEquals(LessonMoveResult.Wrong("Too slow"), s.submit("a7a6"))
        assertEquals(LessonMoveResult.Wrong("Centre!"), s.submit("d7d5"))
        assertEquals(LessonMoveResult.NotExpected, s.submit("e7e4"))
        assertEquals(LessonMoveResult.Correct(reply = null, stepSolved = true, feedback = "Good"), s.submit("e7e5"))
        assertTrue(s.canAdvance)
        assertFalse(s.expectsMove)

        s.advance()
        assertEquals(LessonPhase.QUIZ, s.phase)
        assertEquals(1 to 2, s.challengeNumber)
        assertEquals(LessonMoveResult.Correct(reply = "e7e5", stepSolved = false, feedback = null), s.submit("e2e4"))
        assertFalse("waits for the reply", s.expectsMove)
        assertEquals("e7e5", s.playReply())
        assertTrue(s.expectsMove)
        assertEquals(LessonMoveResult.Correct(reply = null, stepSolved = true, feedback = null), s.submit("b1c3"))

        s.advance()
        assertEquals(2 to 2, s.challengeNumber)
        assertTrue(s.submit("a1a8") is LessonMoveResult.Correct)
        assertTrue(s.advance())
        assertTrue(s.finished)
        assertEquals(LessonPhase.DONE, s.phase)

        val result = s.result()
        assertEquals(2, result.mistakes)
        assertEquals(listOf(ChallengeResult(2, true), ChallengeResult(3, true)), result.challenges)
        assertTrue(result.perfect)
    }

    @Test
    fun goalAcceptsAnyMateAndRejectsChecksThatAreNot() {
        val s = LessonSession(lesson, startStep = 3)
        assertEquals(LessonMoveResult.Wrong(LessonSession.DEFAULT_WRONG), s.submit("a1a7"))
        assertTrue(s.submit("a1a8") is LessonMoveResult.Correct)
    }

    @Test
    fun hintsShowThePieceThenTheMoveAndSpoilTheChallenge() {
        val s = LessonSession(lesson, startStep = 2)
        assertEquals(LessonHint(from = "e2", to = null, text = null), s.hint())
        assertEquals(LessonHint(from = "e2", to = "e4", text = null), s.hint())
        s.submit("e2e4")
        assertNull("no hint while the reply is pending", s.hint())
        s.playReply()
        s.submit("g1f3")
        s.advance()
        assertEquals("a1", s.hint()!!.from)
        s.submit("a1a8")
        s.advance()
        val result = s.result()
        assertEquals(listOf(ChallengeResult(2, false), ChallengeResult(3, false)), result.challenges)
        assertEquals(3, result.hintsUsed)
        assertFalse("resumed part-way, so it cannot be perfect", result.perfect)
        assertFalse(result.playedAllChallenges)
    }

    @Test
    fun restartingAChallengeKeepsItsMistakes() {
        val s = LessonSession(lesson, startStep = 3)
        s.submit("a1a7")
        s.restartStep()
        s.submit("a1a8")
        assertEquals(listOf(ChallengeResult(3, false)), s.result().challenges)
    }

    @Test
    fun aStepCannotBeSkippedBeforeItIsSolved() {
        val s = LessonSession(lesson, startStep = 1)
        assertFalse(s.advance())
        assertEquals(1, s.stepIndex)
    }

    @Test
    fun xpComesOnceAndThePerfectBonusOnce() {
        val perfect = LessonResult("l", listOf(ChallengeResult(2, true), ChallengeResult(3, true)), 0, 0, playedAllChallenges = true)
        val flawed = perfect.copy(challenges = listOf(ChallengeResult(2, true), ChallengeResult(3, false)))

        val first = LessonXp.complete(lesson, previous = null, result = flawed, now = 10)
        assertEquals(Lesson.DEFAULT_XP, first.xp)
        assertTrue(first.firstTime)
        assertEquals(1, first.progress.bestCleanChallenges)
        assertEquals(10L, first.progress.completedAt)

        val again = LessonXp.complete(lesson, first.progress, flawed, now = 20)
        assertEquals(0, again.xp)
        assertEquals(2, again.progress.plays)

        val clean = LessonXp.complete(lesson, again.progress, perfect, now = 30)
        assertEquals(LessonXp.PERFECT_BONUS, clean.xp)
        assertTrue(clean.newlyPerfect)
        assertEquals(10L, clean.progress.completedAt)
        assertEquals(Lesson.DEFAULT_XP + LessonXp.PERFECT_BONUS, clean.progress.xpEarned)

        assertEquals(0, LessonXp.complete(lesson, clean.progress, perfect, now = 40).xp)
    }
}
