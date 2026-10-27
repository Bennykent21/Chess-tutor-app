package com.chesstutor.app.feature.puzzles

import com.chesstutor.app.domain.puzzle.AttemptRecord
import com.chesstutor.app.domain.puzzle.InMemoryPuzzleRepository
import com.chesstutor.app.domain.puzzle.LevelProgress
import com.chesstutor.app.domain.puzzle.LevelRun
import com.chesstutor.app.domain.puzzle.Puzzle
import com.chesstutor.app.domain.puzzle.PuzzleMode
import com.chesstutor.app.domain.puzzle.PuzzleOutcome
import com.chesstutor.app.domain.puzzle.PuzzlePack
import com.chesstutor.app.domain.puzzle.PuzzleStats
import com.chesstutor.app.domain.puzzle.PuzzleWorld
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PuzzlesHomeModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun passed(vararg levels: Int) = levels.associateWith { LevelProgress(it, 5, true, 1) }

    @Test
    fun nodesArePassedCurrentOrLocked() {
        val levels = passed(1, 2) + (3 to LevelProgress(3, 2, false, 1))
        assertEquals(LevelNodeState.PASSED, PuzzlesHomeModel.nodeState(2, levels))
        assertEquals(LevelNodeState.CURRENT, PuzzlesHomeModel.nodeState(3, levels))
        assertEquals(LevelNodeState.LOCKED, PuzzlesHomeModel.nodeState(4, levels))
        assertEquals(LevelNodeState.CURRENT, PuzzlesHomeModel.nodeState(1, emptyMap()))
    }

    @Test
    fun thePathHasABannerThenTenLevelsPerWorld() {
        val items = PuzzlesHomeModel.pathItems(passed(*(1..10).toList().toIntArray()))
        assertEquals(55, items.size)
        val firstBanner = items.first() as PathItem.WorldBanner
        assertEquals(PuzzleWorld.MEADOW, firstBanner.world)
        assertEquals(10, firstBanner.passedLevels)
        assertEquals(PuzzleWorld.TIDES, (items[11] as PathItem.WorldBanner).world)
        assertEquals(11, (items[12] as PathItem.Level).level)
        assertEquals("played levels show their best score", 5, (items[1] as PathItem.Level).bestScore)
        assertEquals("unplayed levels do not", null, (items[12] as PathItem.Level).bestScore)
    }

    @Test
    fun thePathWindsBothWays() {
        val sways = (1..8).map(PuzzlesHomeModel::sway)
        assertTrue(sways.all { it in -1f..1f })
        assertTrue(sways.any { it > 0.5f } && sways.any { it < -0.5f })
    }

    @Test
    fun theCoachSpeaksToWhereThePlayerIs() {
        assertTrue(PuzzlesHomeModel.coachLine(PuzzleStats(), 1, false).startsWith("Welcome"))
        assertTrue(PuzzlesHomeModel.coachLine(PuzzleStats(solved = 9, dayStreak = 4), 3, solvedToday = false).contains("4-day streak is waiting"))
        assertTrue(PuzzlesHomeModel.coachLine(PuzzleStats(solved = 9, dayStreak = 4), 3, solvedToday = true).contains("Day 4"))
        assertTrue(PuzzlesHomeModel.coachLine(PuzzleStats(solved = 9), 10, false).contains("Sunrise Meadow"))
        assertEquals("Ready for level 7?", PuzzlesHomeModel.coachLine(PuzzleStats(solved = 9), 7, false))
    }

    @Test
    fun theViewModelLoadsThePackAndFollowsTheRepository() = runTest {
        val repository = InMemoryPuzzleRepository()
        val puzzle = Puzzle("p", "8/8/8/8/8/8/8/K6k w - - 0 1", listOf("a1a2", "h1h2"), 500, "fork", emptySet())
        val vm = PuzzlesViewModel(repository, loadPack = { PuzzlePack(listOf(puzzle)) })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.home.collect {} }

        val loaded = vm.home.first { !it.loading }
        assertTrue(loaded.packAvailable)
        assertEquals(1, loaded.currentLevel)

        val clean = PuzzleOutcome(solved = true, clean = true, mistakes = 0, hintsUsed = 0, revealed = false)
        repository.recordAttempt(AttemptRecord(puzzle, clean, PuzzleMode.LEVEL, 1, 10_000, System.currentTimeMillis()))
        repository.recordLevelRun(LevelRun(1, 5, 5), System.currentTimeMillis())
        val after = vm.home.first { it.levels.isNotEmpty() }
        assertEquals(2, after.currentLevel)
        assertEquals(15 + 25, after.stats.totalXp)
        assertTrue(after.stats.solvedToday)
    }

    @Test
    fun anEmptyPackIsReportedAsUnavailable() = runTest {
        val vm = PuzzlesViewModel(InMemoryPuzzleRepository(), loadPack = { PuzzlePack.EMPTY })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.home.collect {} }
        assertFalse(vm.home.first { !it.loading }.packAvailable)
    }
}
