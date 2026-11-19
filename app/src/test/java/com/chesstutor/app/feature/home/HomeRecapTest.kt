package com.chesstutor.app.feature.home

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.domain.account.AnalysisSummary
import com.chesstutor.app.domain.account.GameOutcome
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Home's last game and week (D92). */
class HomeRecapTest {

    private fun game(id: String, at: Long, result: String, color: String = "white") =
        GameRecord(id = id, dateMillis = at, botName = "Marlo", botRating = 1400, result = result, pgn = "", moveCount = 61, userColor = color, finalFen = "")

    @Test
    fun theNewestGameWithItsAccuracyOnceReviewed() {
        val games = listOf(game("bot_a", 1_000, "1-0"), game("bot_b", 2_000, "1-0", color = "black"))
        val last = HomeRecap.lastGame(games, mapOf("bot_b" to AnalysisSummary("bot_b", 61, 80.0, 72.4, lite = false, analysedAt = 0)))!!
        assertEquals("bot_b", last.id)
        assertEquals(GameOutcome.LOSS, last.outcome)
        assertEquals("Lost to Marlo", last.title)
        assertEquals(31, last.moves)
        assertEquals("Black's accuracy, as the player was Black", 72.4, last.accuracy!!, 1e-9)
        assertNull("no review yet", HomeRecap.lastGame(games.take(1), emptyMap())!!.accuracy)
        assertNull(HomeRecap.lastGame(emptyList(), emptyMap()))
    }
}
