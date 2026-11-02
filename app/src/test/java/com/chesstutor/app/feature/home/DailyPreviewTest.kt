package com.chesstutor.app.feature.home

import com.chesstutor.app.domain.puzzle.Puzzle
import com.chesstutor.app.domain.puzzle.PuzzleSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
