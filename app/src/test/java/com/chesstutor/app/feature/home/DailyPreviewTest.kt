package com.chesstutor.app.feature.home

import com.chesstutor.app.domain.puzzle.DailyPreview
import com.chesstutor.app.domain.puzzle.Puzzle
import com.chesstutor.app.domain.puzzle.PuzzleHistoryEntry
import com.chesstutor.app.domain.puzzle.PuzzleModes
import com.chesstutor.app.domain.puzzle.PuzzleSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DailyPreviewTest {

    private fun puzzle(fen: String, vararg moves: String) =
        Puzzle("p", fen, moves.toList(), rating = 1200, primaryTheme = "mate", themes = emptySet())

    /** White plays Nf3 and Black solves: the tile shows what the puzzle opens on, from Black's side. */
    private val blackToSolve = puzzle("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2", "g1f3", "b8c6")

    @Test
    fun theTileShowsThePositionThePuzzleOpensOn() {
        val preview = DailyPreview.of(blackToSolve)!!
        assertEquals(PuzzleSession.start(blackToSolve)!!.fen, preview.fen)
        assertFalse("the board is turned for Black", preview.whiteAtBottom)
    }

    @Test
    fun aPuzzleForWhiteKeepsWhiteAtTheBottom() {
        val preview = DailyPreview.of(puzzle("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1", "e7e5", "g1f3"))!!
        assertTrue(preview.whiteAtBottom)
    }

    @Test
    fun aBrokenPuzzleShowsNoPosition() {
        assertNull(DailyPreview.of(puzzle("not a position", "e2e4", "e7e5")))
    }

    private val today = LocalDate.of(2026, 11, 2)
    private fun at(date: LocalDate) = date.atTime(12, 0).atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
    private fun entry(id: String, solved: Boolean, date: LocalDate) = PuzzleHistoryEntry(id, solved, clean = solved, ratingAfter = 1200, finishedAt = at(date))
    private fun solved(vararg history: PuzzleHistoryEntry) = PuzzleModes.solvedOn("daily", history.toList(), today, ZoneOffset.UTC)

    /** Before, any puzzle solved today made the tile say "Solved today". */
    @Test
    fun onlyTheDailyPuzzleItselfCountsAsSolved() {
        assertTrue(solved(entry("daily", true, today)))
        assertFalse("another puzzle", solved(entry("other", true, today)))
        assertFalse("tried and failed", solved(entry("daily", false, today)))
        assertFalse("the same puzzle on another day", solved(entry("daily", true, today.minusDays(9))))
        assertFalse("not loaded yet", PuzzleModes.solvedOn(null, listOf(entry("daily", true, today)), today, ZoneOffset.UTC))
    }
}
