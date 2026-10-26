package com.chesstutor.app.domain.puzzle

import com.chesstutor.app.domain.ReviewItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class DueReviewTest {

    private val now = Instant.parse("2026-10-25T12:00:00Z")

    /** White to move: Ra8 mates on the back rank; Ra7 does not. */
    private val backRank = "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1"

    private fun item(id: String, due: Instant = now, best: String = "a1a8", fen: String = backRank, stage: Int = -1) =
        ReviewItem(id = id, fen = fen, dueAt = due, stage = stage, bestMoveUci = best, sourceDescription = "Game $id")

    @Test
    fun onlyDuePositionsInTheOrderTheyFellDue() {
        val review = DueReview(
            listOf(item("later", due = now.plus(1, ChronoUnit.DAYS)), item("b", due = now.minusSeconds(10)), item("a", due = now.minusSeconds(60))),
        ) { now }
        assertEquals(listOf("a", "b"), review.items.map { it.id })
        assertEquals("a", review.current?.id)
    }

    @Test
    fun aCleanSolveMovesThePositionOut() {
        val review = DueReview(listOf(item("a"))) { now }
        assertEquals(DueMoveResult.ILLEGAL to null, review.submit("a1h8"))
        val (result, saved) = review.submit("a1a8")
        assertEquals(DueMoveResult.CORRECT, result)
        assertEquals(0, saved!!.stage)
        assertEquals(now.plus(1, ChronoUnit.DAYS), saved.dueAt)
        assertEquals(1, review.cleanCount)
        assertTrue(review.done)
        assertEquals("solved positions take no more moves", DueMoveResult.NOT_NOW, review.submit("a1a7").first)

        review.next()
        assertTrue(review.finished)
        assertNull(review.current)
    }

    @Test
    fun aWrongMoveBringsItBackNowAndCountsOnce() {
        val review = DueReview(listOf(item("a", stage = 2))) { now }
        val (wrong, saved) = review.submit("a1a7")
        assertEquals(DueMoveResult.WRONG, wrong)
        assertEquals(-1, saved!!.stage)
        assertEquals(now, saved.dueAt)
        assertTrue(review.missed)
        assertNull("a second wrong move does not save again", review.submit("a1a6").second)
        val (right, again) = review.submit("a1a8")
        assertEquals(DueMoveResult.CORRECT, right)
        assertNull("solved after a miss: it stays due now", again)
        assertEquals(0, review.cleanCount)
    }

    @Test
    fun hintsShowThePieceThenTheMoveAndKeepItClose() {
        val review = DueReview(listOf(item("a"))) { now }
        assertEquals(DueHint("a1", null), review.hint())
        assertEquals(DueHint("a1", "a8"), review.hint())
        val (_, saved) = review.submit("a1a8")
        assertEquals("solved with a hint: back tomorrow", 0, saved!!.stage)
        assertEquals(0, review.cleanCount)
        assertNull("no hints once solved", review.hint())
    }

    @Test
    fun anyMateCountsAndShowAnswerIsAMiss() {
        // The saved answer is a quiet move, but Ra8 mates: it counts.
        val mate = DueReview(listOf(item("a", best = "g1f1"))) { now }
        assertEquals(DueMoveResult.CORRECT, mate.submit("a1a8").first)

        val reveal = DueReview(listOf(item("a"))) { now }
        val (move, saved) = reveal.reveal()
        assertEquals("a1a8", move!!.uci)
        assertEquals(-1, saved!!.stage)
        assertTrue(reveal.done)
        assertFalse(reveal.finished)
    }
}
