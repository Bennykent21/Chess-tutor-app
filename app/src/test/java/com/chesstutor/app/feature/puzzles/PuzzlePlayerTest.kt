package com.chesstutor.app.feature.puzzles

import com.chesstutor.app.core.Move
import com.chesstutor.app.core.Square
import com.chesstutor.app.domain.puzzle.InMemoryPuzzleRepository
import com.chesstutor.app.domain.puzzle.Puzzle
import com.chesstutor.app.domain.puzzle.PuzzleMode
import com.chesstutor.app.domain.puzzle.XpRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class PuzzlePlayerTest {

    /** Fool's mate: White's set-up move g4, then Black mates with Qh4 in one. */
    private fun fools(id: String) = Puzzle(
        id = id,
        fen = "rnbqkbnr/pppp1ppp/8/4p3/8/5P2/PPPPP1PP/RNBQKBNR w KQkq - 0 2",
        moves = listOf("g2g4", "d8h4"),
        rating = 500, primaryTheme = "mateIn1", themes = setOf("mateIn1", "short"),
    )

    private val timing = PlayerTiming(setupMs = 100, replyMs = 100, wrongFlashMs = 300, lineStepMs = 100, rushAdvanceMs = 100, tickMs = 50)

    private fun TestScope.player(spec: PuzzleRunSpec, repository: InMemoryPuzzleRepository = InMemoryPuzzleRepository()) =
        PuzzlePlayer(spec, repository, backgroundScope, clock = { testScheduler.currentTime }, timing = timing)

    private fun levelSpec(count: Int = 5) = PuzzleRunSpec(
        mode = PuzzleMode.LEVEL, title = "Level 1", level = 1, plannedCount = count,
        nextPuzzle = { index, _ -> if (index < count) fools("f$index") else null },
    )

    private fun TestScope.ready(p: PuzzlePlayer) {
        advanceTimeBy(timing.setupMs + 1)
        runCurrent()
        assertEquals(PlayerPhase.SOLVING, p.state.value.phase)
    }

    private fun PuzzlePlayer.play(uci: String) = state.value.board!!.onMove(Move.fromUci(uci))

    /**
     * The player works in the background scope, which advanceUntilIdle does not wait for, and
     * its clock ticks forever while a puzzle is open. Advancing time runs both.
     */
    private fun TestScope.settle() {
        advanceTimeBy(2_000)
        runCurrent()
    }

    @Test
    fun aLevelOfFiveIsPassedWithFourSolvedAndEarnsTheBonus() = runTest {
        val repository = InMemoryPuzzleRepository()
        val p = player(levelSpec(), repository)
        p.start()
        repeat(5) { i ->
            ready(p)
            assertEquals("Find the checkmate in one move", p.state.value.prompt)
            if (i == 2) p.reveal() else p.play("d8h4")
            settle()
            p.next()
        }
        settle()
        val s = p.state.value
        assertEquals(PlayerPhase.FINISHED, s.phase)
        assertEquals(4, s.solvedCount)
        assertEquals(true, s.levelPassed)
        assertEquals(XpRules.LEVEL_PASSED, s.levelResult?.bonusXp)
        assertTrue(repository.levelProgress().first().getValue(1).passed)
    }

    @Test
    fun solvingShowsThemesRatingAndXp() = runTest {
        val p = player(levelSpec())
        p.start()
        ready(p)
        advanceTimeBy(5_000)
        p.play("d8h4")
        settle()
        val s = p.state.value
        assertEquals(PlayerPhase.SOLVED, s.phase)
        assertEquals(listOf("Mate in 1"), s.revealedThemes)
        assertTrue(s.elapsedMs >= 5_000)
        assertEquals("10 base + 5 for a fast solve", 15, s.xpEarned)
        assertNotNull(s.rating)
    }

    @Test
    fun hintsShowThePieceThenAnArrow() = runTest {
        val p = player(levelSpec())
        p.start()
        ready(p)
        p.hint()
        assertEquals(Square.fromAlgebraic("d8"), p.state.value.hintSquare)
        p.hint()
        assertNull(p.state.value.hintSquare)
        assertEquals(Square.fromAlgebraic("h4"), p.state.value.hintArrow?.to)
    }

    @Test
    fun aWrongMoveFlashesItsSquareThenClears() = runTest {
        val p = player(levelSpec())
        p.start()
        ready(p)
        p.play("a7a6")
        assertEquals(Square.fromAlgebraic("a6"), p.state.value.mistakeSquare)
        assertEquals(PlayerPhase.SOLVING, p.state.value.phase)
        advanceTimeBy(timing.wrongFlashMs + 1)
        runCurrent()
        assertNull(p.state.value.mistakeSquare)
    }

    @Test
    fun rushFailsAPuzzleOnAWrongMoveAndEndsAtTheStrikeLimit() = runTest {
        val p = player(
            PuzzleRunSpec(
                mode = PuzzleMode.RUSH, title = "Survival", maxStrikes = 2, oneTry = true,
                nextPuzzle = { index, _ -> fools("r$index") },
            )
        )
        p.start()
        ready(p)
        p.play("d8h4")
        advanceTimeBy(timing.rushAdvanceMs + 1)
        ready(p)
        assertEquals("solved puzzles move straight on", 1, p.state.value.index)
        p.play("a7a6")
        assertEquals(1, p.state.value.strikes)
        advanceTimeBy(timing.rushAdvanceMs + 1)
        ready(p)
        p.play("h7h6")
        settle()
        assertEquals(PlayerPhase.FINISHED, p.state.value.phase)
        assertEquals(1, p.state.value.solvedCount)
    }

    @Test
    fun aTimedRunEndsWhenTheClockRunsOut() = runTest {
        val p = player(
            PuzzleRunSpec(mode = PuzzleMode.RUSH, title = "3 minutes", timeLimitMs = 1_000, oneTry = true, nextPuzzle = { index, _ -> fools("t$index") })
        )
        p.start()
        ready(p)
        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(PlayerPhase.FINISHED, p.state.value.phase)
        assertEquals(0L, p.state.value.remainingMs)
    }

    @Test
    fun reportingAnotherMoveIsSavedOnce() = runTest {
        val repository = InMemoryPuzzleRepository()
        val p = player(levelSpec(), repository)
        p.start()
        ready(p)
        p.play("a7a6")
        p.reportAnotherMove("I think a6 works")
        p.reportAnotherMove("again")
        settle()
        assertEquals(listOf("f0 a7a6"), repository.reports)
        assertTrue(p.state.value.reported)
    }

    @Test
    fun timeTakenReadsAsMinutesAndSeconds() {
        assertEquals("0:00", formatElapsed(400))
        assertEquals("0:07", formatElapsed(7_900))
        assertEquals("1:23", formatElapsed(83_000))
    }

    @Test
    fun anEmptyRunIsUnavailable() = runTest {
        val p = player(PuzzleRunSpec(mode = PuzzleMode.DAILY, title = "Daily", nextPuzzle = { _, _ -> null }))
        p.start()
        assertEquals(PlayerPhase.UNAVAILABLE, p.state.value.phase)
        assertFalse(p.state.value.results.isNotEmpty())
    }
}
