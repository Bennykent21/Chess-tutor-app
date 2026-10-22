package com.chesstutor.app.data.puzzle

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chesstutor.app.data.local.AppDatabase
import com.chesstutor.app.domain.puzzle.AttemptRecord
import com.chesstutor.app.domain.puzzle.LevelRun
import com.chesstutor.app.domain.puzzle.Puzzle
import com.chesstutor.app.domain.puzzle.PuzzleMode
import com.chesstutor.app.domain.puzzle.PuzzleOutcome
import com.chesstutor.app.domain.puzzle.PuzzleProgress
import com.chesstutor.app.domain.puzzle.PuzzleWorld
import com.chesstutor.app.domain.puzzle.XpRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class RoomPuzzleRepositoryTest {

    private lateinit var db: AppDatabase
    private val zone = ZoneOffset.UTC
    private val day1 = LocalDate.of(2026, 10, 20)

    private fun at(day: LocalDate, hour: Int = 12): Long = day.atTime(hour, 0).toInstant(zone).toEpochMilli()

    private fun puzzle(id: String, rating: Int = 1200) =
        Puzzle(id, "8/8/8/8/8/8/8/K6k w - - 0 1", listOf("a1a2", "h1h2"), rating, "fork", emptySet())

    private val clean = PuzzleOutcome(solved = true, clean = true, mistakes = 0, hintsUsed = 0, revealed = false)
    private val revealed = PuzzleOutcome(solved = false, clean = false, mistakes = 1, hintsUsed = 0, revealed = true)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun oldProgressIsCopiedInOnceAndNeverAgain() = runBlocking {
        var legacyReads = 0
        val legacy = PuzzleProgress(rating = 1450, seenIds = setOf("old1", "old2"), attempts = 30, solved = 25, cleanSolves = 20, streak = 4, bestStreak = 9)
        val repo = RoomPuzzleRepository(db, legacy = { legacyReads++; legacy }, zone = zone)

        val loaded = repo.loadProgress(seedRating = 800)
        assertEquals(1450, loaded.rating)
        assertEquals(setOf("old1", "old2"), loaded.seenIds)
        assertEquals(9, loaded.bestStreak)

        repo.recordAttempt(AttemptRecord(puzzle("new1"), clean, PuzzleMode.LEVEL, 1, 30_000, at(day1)))
        val again = RoomPuzzleRepository(db, legacy = { legacyReads++; legacy }, zone = zone).loadProgress(800)
        assertEquals("the copy happened once", 1, legacyReads)
        assertEquals(setOf("old1", "old2", "new1"), again.seenIds)
        assertEquals(31, again.attempts)
        assertEquals(5, again.streak)
    }

    @Test
    fun withNothingToCopyTheSeedRatingIsUsed() = runBlocking {
        val repo = RoomPuzzleRepository(db, zone = zone)
        assertEquals(800, repo.loadProgress(seedRating = 800).rating)
    }

    @Test
    fun attemptsEarnXpAndBuildTheDayStreak() = runBlocking {
        val repo = RoomPuzzleRepository(db, zone = zone)
        repo.recordAttempt(AttemptRecord(puzzle("a"), clean, PuzzleMode.LEVEL, 1, 10_000, at(day1)))
        repo.recordAttempt(AttemptRecord(puzzle("b"), clean, PuzzleMode.RUSH, null, 50_000, at(day1.plusDays(1))))
        repo.recordAttempt(AttemptRecord(puzzle("c"), revealed, PuzzleMode.DAILY, null, 90_000, at(day1.plusDays(2))))

        val stats = repo.stats { day1.plusDays(2) }.first()
        assertEquals("15 (fast) + 11 (streak 2) + 0 (revealed)", 26, stats.totalXp)
        assertEquals("a revealed puzzle does not keep the streak alive", 2, stats.dayStreak)
        assertEquals(0, stats.cleanStreak)
        assertEquals(2, stats.solved)
    }

    @Test
    fun passingLevelsEarnsBonusesAndCompletesWorlds() = runBlocking {
        val repo = RoomPuzzleRepository(db, zone = zone)
        assertEquals(XpRules.LEVEL_PASSED, repo.recordLevelRun(LevelRun(1, 4, 5), at(day1)).bonusXp)
        assertEquals("no second bonus", 0, repo.recordLevelRun(LevelRun(1, 5, 5), at(day1)).bonusXp)
        repo.recordLevelRun(LevelRun(2, 2, 5), at(day1))

        val levels = repo.levelProgress().first()
        assertEquals(5, levels.getValue(1).bestScore)
        assertEquals(2, levels.getValue(1).plays)
        assertTrue(levels.getValue(1).passed)
        assertEquals(false, levels.getValue(2).passed)

        (2..9).forEach { repo.recordLevelRun(LevelRun(it, 5, 5), at(day1)) }
        assertEquals(PuzzleWorld.MEADOW, repo.recordLevelRun(LevelRun(10, 5, 5), at(day1)).worldCompleted)
        assertEquals(10 * XpRules.LEVEL_PASSED + XpRules.WORLD_COMPLETED, repo.stats { day1 }.first().totalXp)
    }

    @Test
    fun mistakesAreTheLatestUncleanAttemptsAndReportsAreSaved() = runBlocking {
        val repo = RoomPuzzleRepository(db, zone = zone)
        repo.recordAttempt(AttemptRecord(puzzle("missed"), revealed, PuzzleMode.LEVEL, 1, 1, at(day1, 9)))
        repo.recordAttempt(AttemptRecord(puzzle("fixed"), revealed, PuzzleMode.LEVEL, 1, 1, at(day1, 10)))
        repo.recordAttempt(AttemptRecord(puzzle("fixed"), clean, PuzzleMode.REVIEW, null, 1, at(day1, 11)))
        repo.recordAttempt(AttemptRecord(puzzle("missed2"), revealed, PuzzleMode.RUSH, null, 1, at(day1, 12)))
        assertEquals(listOf("missed2", "missed"), repo.mistakePuzzleIds())

        repo.reportAnotherMove("p1", "fen", "e2e4", "Also wins", at(day1))
        assertEquals(1, db.puzzleDao().reportCount())
    }
}
