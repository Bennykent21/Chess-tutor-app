package com.chesstutor.app

import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.domain.ReviewItem
import com.chesstutor.app.domain.ReviewScheduler
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks kept from the original specification suite; the rest covered the classic screens (removed in 6A). */
class ChessTutorSpecVerificationTest {

    @Test
    fun reviewScheduler_returnsImmutableUpdatedCopy() {
        val now = Instant.parse("2026-10-07T00:00:00Z")
        val original = ReviewItem(
            id = "rev1",
            fen = ChessPosition.STARTING_FEN,
            dueAt = now,
            stage = 0,
            attempts = 0,
            mistakeUci = "g1h3",
            bestMoveUci = "g1f3",
            explanation = "Develop knights toward the center.",
            intervalDays = 1
        )
        val updated = ReviewScheduler.recordAttempt(
            item = original,
            correct = true,
            usedHint = false,
            now = now
        )
        assertNotEquals(original, updated)
        assertEquals(0, original.stage)
        assertEquals(1, updated.stage)
        assertTrue(updated.intervalDays > original.intervalDays)
    }

    @Test
    fun pgnSanTokenizer_stripsCommentsVariationsAndNags_andFlagsIncompleteOnCorruptToken() {
        val client = com.chesstutor.app.data.network.RatingApiClient()
        val annotatedPgn = """
            [Event "Live Chess"]
            [Result "1-0"]
            1. e4 { [%clk 0:09:59] } 1... e5 $1 (1... c5 2. Nf3) 2. Nf3! Nc6? 3. Bb5 Nf6 4. 0-0 1-0
        """.trimIndent()
        val cleanStream = client.extractMovesFromPgn(annotatedPgn)
        val parsed = client.convertSanStreamToUciWithStatus(cleanStream)
        assertFalse("Valid annotated PGN should not be flagged incomplete", parsed.incomplete)
        assertEquals("e2e4 e7e5 g1f3 b8c6 f1b5 g8f6 e1g1", parsed.uciMoves)

        val corruptStream = "1. e4 e5 2. INVALID_TOKEN Nc6 1-0"
        val corruptParsed = client.convertSanStreamToUciWithStatus(corruptStream)
        assertTrue("Corrupt token must flag incomplete = true", corruptParsed.incomplete)
        assertEquals("e2e4 e7e5", corruptParsed.uciMoves)
    }
}
