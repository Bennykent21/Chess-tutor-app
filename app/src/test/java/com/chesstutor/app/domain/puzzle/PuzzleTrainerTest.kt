package com.chesstutor.app.domain.puzzle

import com.chesstutor.app.domain.ChessPosition
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleTrainerTest {

    private val foolsMate = Puzzle(
        id = "fool",
        fen = "rnbqkbnr/pppp1ppp/8/4p3/8/5P2/PPPPP1PP/RNBQKBNR w KQkq - 0 2",
        moves = listOf("g2g4", "d8h4"),
        rating = 500, primaryTheme = "mateIn1", themes = setOf("mateIn1")
    )
    private val scholars = Puzzle(
        id = "scholar",
        fen = "rnbqkbnr/pppp1ppp/8/4p3/2B1P3/8/PPPP1PPP/RNBQK1NR b KQkq - 1 2",
        moves = listOf("b8c6", "d1h5", "g8f6", "h5f7"),
        rating = 800, primaryTheme = "mateIn2", themes = setOf("mateIn2")
    )
    private val tworooks = Puzzle(
        id = "rooks",
        fen = "6k1/p4ppp/8/8/8/8/8/1RR3K1 b - - 0 1",
        moves = listOf("a7a6", "b1b8"),
        rating = 600, primaryTheme = "backRankMate", themes = setOf("backRankMate")
    )
    private val promo = Puzzle(
        id = "promo",
        fen = "8/P6k/8/8/8/8/8/K7 b - - 0 1",
        moves = listOf("h7g6", "a7a8q"),
        rating = 700, primaryTheme = "promotion", themes = setOf("promotion")
    )

    private fun trainer(vararg puzzles: Puzzle, rating: Int = 500, store: PuzzleProgressStore = InMemoryPuzzleProgressStore()): PuzzleTrainer {
        val t = PuzzleTrainer(PuzzlePack(puzzles.toList()), store.load(rating), store, Random(7))
        assertTrue(t.loadNext())
        t.beginSolving()
        return t
    }

    @Test
    fun setupShowsPositionBeforeOpponentMoveThenAfter() {
        val t = PuzzleTrainer(PuzzlePack(listOf(foolsMate)), PuzzleProgress(rating = 500), null, Random(1))
        assertTrue(t.loadNext())
        assertEquals(PuzzlePhase.SETUP, t.state.phase)
        assertEquals(foolsMate.fen, t.state.fen)
        assertTrue(t.state.flipped)                       // solver is Black: board turned
        assertNull(t.state.lastMove)
        t.beginSolving()
        assertEquals(PuzzlePhase.SOLVING, t.state.phase)
        assertEquals("g2" to "g4", t.state.lastMove)
        assertEquals('b', t.state.sideToMove)
    }

    @Test
    fun tapSelectsPieceShowsTargetsThenSolves() {
        val store = InMemoryPuzzleProgressStore()
        val t = trainer(foolsMate, store = store)
        t.onSquareTapped("d8")
        assertEquals("d8", t.state.selectedSquare)
        assertTrue("h4" in t.state.legalTargets)
        t.onSquareTapped("h4")
        assertEquals(PuzzlePhase.SOLVED, t.state.phase)
        assertEquals(16, t.state.ratingDelta)             // equal rating, clean solve
        assertEquals(1, t.state.streak)
        assertEquals(1, t.state.solvedCount)
        assertEquals(516, store.load(0).rating)           // saved
        assertTrue("fool" in store.load(0).seenIds)
    }

    @Test
    fun tappingEmptyOrEnemySquareDeselects() {
        val t = trainer(foolsMate)
        t.onSquareTapped("d8")
        t.onSquareTapped("a4")                            // empty square that is not a legal target
        assertNull(t.state.selectedSquare)
        t.onSquareTapped("d1")                            // enemy piece, nothing selected
        assertNull(t.state.selectedSquare)
    }

    @Test
    fun wrongMoveMarksSquareBlocksTapsThenRecovers() {
        val t = trainer(tworooks, rating = 600)
        t.onSquareTapped("b1")
        t.onSquareTapped("b7")                            // legal, not mate
        assertEquals(PuzzlePhase.WRONG, t.state.phase)
        assertEquals("b7", t.state.badSquare)
        assertEquals(1, t.state.mistakes)
        t.onSquareTapped("b1")                            // ignored while the mistake is showing
        assertNull(t.state.selectedSquare)
        t.clearWrong()
        assertEquals(PuzzlePhase.SOLVING, t.state.phase)
        assertNull(t.state.badSquare)
        t.onSquareTapped("b1")
        t.onSquareTapped("b8")
        assertEquals(PuzzlePhase.SOLVED, t.state.phase)
        assertEquals(-16, t.state.ratingDelta)            // solved, but not cleanly
        assertEquals(0, t.state.streak)
    }

    @Test
    fun multiMovePuzzleShowsReplyBetweenMoves() {
        val t = trainer(scholars, rating = 800)
        assertEquals(2, t.state.totalSteps)
        t.onSquareTapped("d1"); t.onSquareTapped("h5")
        assertEquals(PuzzlePhase.REPLYING, t.state.phase)
        assertEquals('b', ChessPosition(t.state.fen).sideToMove)   // reply not shown yet
        assertEquals("d1" to "h5", t.state.lastMove)
        t.showReply()
        assertEquals(PuzzlePhase.SOLVING, t.state.phase)
        assertEquals("g8" to "f6", t.state.lastMove)
        assertEquals(2, t.state.step)
        t.onSquareTapped("h5"); t.onSquareTapped("f7")
        assertEquals(PuzzlePhase.SOLVED, t.state.phase)
        assertTrue(ChessPosition(t.state.fen).isCheckmate)
    }

    @Test
    fun hintsEscalateFromPieceToArrowAndCostTheCleanSolve() {
        val t = trainer(tworooks, rating = 600)
        t.hint()
        assertEquals("b1", t.state.selectedSquare)
        assertTrue("b8" in t.state.legalTargets)
        assertNull(t.state.hintArrow)
        t.hint()
        assertEquals("b1" to "b8", t.state.hintArrow)
        assertEquals(2, t.state.hintsUsed)
        t.onSquareTapped("b8")                            // piece is already selected
        assertEquals(PuzzlePhase.SOLVED, t.state.phase)
        assertEquals(-16, t.state.ratingDelta)
    }

    @Test
    fun revealPlaysOutTheSolutionAndCountsAsAFailure() {
        val store = InMemoryPuzzleProgressStore()
        val t = trainer(scholars, rating = 800, store = store)
        val moves = t.reveal()
        assertEquals(listOf("d1h5", "g8f6", "h5f7"), moves)
        assertEquals(PuzzlePhase.REVEALED, t.state.phase)
        assertEquals(-16, t.state.ratingDelta)
        moves.forEach { t.applyRevealMove(it) }
        assertTrue(ChessPosition(t.state.fen).isCheckmate)
        assertEquals(0, store.load(0).solved)
        assertTrue("scholar" in store.load(0).seenIds)
        assertTrue(t.reveal().isEmpty())                  // already finished
    }

    @Test
    fun promotionAsksForAPieceAndJudgesIt() {
        val t = trainer(promo, rating = 700)
        t.onSquareTapped("a7"); t.onSquareTapped("a8")
        assertEquals(setOf('q', 'r', 'b', 'n'), t.state.pendingPromotion!!.choices.toSet())
        t.choosePromotion('n')                            // legal but not the solution
        assertEquals(PuzzlePhase.WRONG, t.state.phase)
        t.clearWrong()
        t.onSquareTapped("a7"); t.onSquareTapped("a8")
        t.choosePromotion('q')
        assertEquals(PuzzlePhase.SOLVED, t.state.phase)
    }

    @Test
    fun cancellingPromotionKeepsPlaying() {
        val t = trainer(promo, rating = 700)
        t.onSquareTapped("a7"); t.onSquareTapped("a8")
        t.cancelPromotion()
        assertNull(t.state.pendingPromotion)
        assertEquals(PuzzlePhase.SOLVING, t.state.phase)
    }

    @Test
    fun finishedAndSkippedPuzzlesAreNotServedAgainRightAway() {
        val t = trainer(foolsMate, scholars, rating = 500)
        val first = t.state.puzzle!!.id
        t.markSkipped()
        t.loadNext()
        assertTrue(t.state.puzzle!!.id != first)
    }

    @Test
    fun solvedPuzzleIsNotRepeatedWhileOthersRemain() {
        val t = trainer(foolsMate, scholars, rating = 500)
        if (t.state.puzzle!!.id == "fool") {
            t.onSquareTapped("d8"); t.onSquareTapped("h4")
            t.loadNext()
            assertEquals("scholar", t.state.puzzle!!.id)
        } else {
            t.reveal()
            t.loadNext()
            assertEquals("fool", t.state.puzzle!!.id)
        }
    }

    @Test
    fun themeFilterAndOrderedChips() {
        val t = trainer(tworooks, scholars, foolsMate, promo)
        assertEquals(
            listOf("Mixed", "Mate in 1", "Mate in 2", "Back-rank mate", "Promotion"),
            t.state.themeOptions.map { it.label }
        )
        t.setTheme("mateIn2")
        t.loadNext()
        assertEquals("scholar", t.state.puzzle!!.id)
    }

    @Test
    fun emptyPackIsReportedAsUnavailable() {
        val t = PuzzleTrainer(PuzzlePack.EMPTY, PuzzleProgress(), null)
        assertEquals(PuzzlePhase.UNAVAILABLE, t.state.phase)
        assertFalse(t.loadNext())
    }

    @Test
    fun streakCountsCleanSolvesAndBestStreakIsKept() {
        val p = PuzzleProgress(rating = 1000)
        val clean = PuzzleOutcome(solved = true, clean = true, mistakes = 0, hintsUsed = 0, revealed = false)
        val messy = clean.copy(clean = false, mistakes = 1)
        val after = p.afterFinish(foolsMate, clean).afterFinish(scholars, clean).afterFinish(tworooks, messy)
        assertEquals(0, after.streak)
        assertEquals(2, after.bestStreak)
        assertEquals(3, after.solved)
        assertEquals(2, after.cleanSolves)
        assertEquals(3, after.attempts)
    }
}
