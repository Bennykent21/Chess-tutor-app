package com.chesstutor.app.data.learn

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chesstutor.app.data.local.AppDatabase
import com.chesstutor.app.domain.learn.ChallengeResult
import com.chesstutor.app.domain.learn.Lesson
import com.chesstutor.app.domain.learn.LessonResult
import com.chesstutor.app.domain.learn.LessonStep
import com.chesstutor.app.domain.learn.LessonXp
import com.chesstutor.app.domain.learn.MoveGoal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomLessonProgressRepositoryTest {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val repository = RoomLessonProgressRepository(db, now = { 1_000 })

    private val fen = "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1"
    private val lesson = Lesson(
        id = "mate-1", title = "Mate", summary = "",
        steps = listOf(LessonStep.Explain("Look", fen), LessonStep.Play("Mate", fen, quiz = true, goal = MoveGoal.MATE)),
    )

    @After
    fun tearDown() = db.close()

    @Test
    fun stepsAreSavedThenCompletionRecordsXpOnce() = runBlocking {
        repository.saveStep("mate-1", stepIndex = 1, stepCount = 2)
        val started = repository.get("mate-1")!!
        assertEquals(1, started.stepIndex)
        assertFalse(started.completed)
        assertTrue(started.started)

        val clean = LessonResult("mate-1", listOf(ChallengeResult(1, true)), mistakes = 0, hintsUsed = 0, playedAllChallenges = true)
        val first = repository.complete(lesson, clean)
        assertEquals(Lesson.DEFAULT_XP + LessonXp.PERFECT_BONUS, first.xp)
        val again = repository.complete(lesson, clean)
        assertEquals(0, again.xp)

        val saved = repository.observe().first().getValue("mate-1")
        assertTrue(saved.completed)
        assertTrue(saved.perfect)
        assertEquals(2, saved.plays)
        assertEquals(0, saved.stepIndex)
        assertEquals(1_000L, saved.completedAt)
        assertEquals("XP lands in the shared ledger", Lesson.DEFAULT_XP + LessonXp.PERFECT_BONUS, db.puzzleDao().totalXp().first())

        // Opening a finished lesson again keeps it finished.
        repository.saveStep("mate-1", stepIndex = 1, stepCount = 2)
        assertTrue(repository.get("mate-1")!!.completed)
    }
}
