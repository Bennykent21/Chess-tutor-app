package com.chesstutor.app.data.network

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.GameSource
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.chesstutor.app.data.repository.InMemoryGameRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RatingImportResilienceTest {

    @Test
    fun gameRepositoryIsIdempotentOnRepeatedImports() = runBlocking {
        val repository = InMemoryGameRepository()
        val game1 = GameRecord(
            id = "chesscom_1001",
            dateMillis = 1000L,
            botName = "Opponent",
            botRating = 1500,
            result = "1-0",
            pgn = "1. e4 e5 2. Nf3",
            moveCount = 2,
            userColor = "w",
            finalFen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2",
            uciMoves = "e2e4 e7e5 g1f3",
            source = GameSource.CHESS_COM
        )

        // First save
        repository.saveGames(listOf(game1))
        assertEquals(1, repository.getRecentGames(10).size)

        // Repeated save of same game
        repository.saveGames(listOf(game1))
        assertEquals(1, repository.getRecentGames(10).size)
        assertEquals("chesscom_1001", repository.getRecentGames(10).first().id)
    }

    @Test
    fun parseLichessNdjsonFiltersAbortedAndPreservesStandardGames() {
        val client = RatingApiClient()
        val sampleNdjson = """
            {"id":"valid1","variant":"standard","status":"mate","winner":"white","createdAt":1700000000,"speed":"rapid","players":{"white":{"user":{"name":"Hero"},"rating":1500},"black":{"user":{"name":"Villain"},"rating":1450}},"pgn":"1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7#"}
            {"id":"aborted1","variant":"standard","status":"aborted","createdAt":1700000100,"speed":"rapid","players":{"white":{"user":{"name":"Hero"},"rating":1500},"black":{"user":{"name":"Villain"},"rating":1450}},"pgn":""}
            {"id":"noStart1","variant":"standard","status":"noStart","createdAt":1700000200,"speed":"rapid","players":{"white":{"user":{"name":"Hero"},"rating":1500},"black":{"user":{"name":"Villain"},"rating":1450}},"pgn":""}
            {"id":"valid2","variant":"standard","status":"resign","winner":"black","createdAt":1700000300,"speed":"blitz","players":{"white":{"user":{"name":"Opponent"},"rating":1600},"black":{"user":{"name":"Hero"},"rating":1520}},"pgn":"1. d4 d5"}
        """.trimIndent()

        val records = client.parseLichessNdjson(sampleNdjson, "Hero")
        assertEquals(2, records.size)
        assertEquals("lichess_valid1", records[0].id)
        assertEquals("1-0", records[0].result)
        assertEquals("white", records[0].userColor)
        assertEquals("lichess_valid2", records[1].id)
        assertEquals("0-1", records[1].result)
        assertEquals("black", records[1].userColor)
    }

    @Test
    fun theAutomaticSyncSkipsArchiveMonthsThatEndedBeforeTheNewestGame() {
        val client = RatingApiClient()
        val october = client.archiveMonthEnd("https://api.chess.com/pub/player/kenny/games/2026/10")!!
        assertEquals(java.time.Instant.parse("2026-11-01T00:00:00Z").toEpochMilli(), october)
        assertEquals("December rolls into the next year", java.time.Instant.parse("2027-01-01T00:00:00Z").toEpochMilli(), client.archiveMonthEnd("https://x/games/2026/12/"))
        assertEquals(null, client.archiveMonthEnd("https://x/games/latest"))
        assertEquals("a resumed import skips months that start after its oldest game", java.time.Instant.parse("2026-10-01T00:00:00Z").toEpochMilli(), client.archiveMonthStart("https://x/games/2026/10"))
    }

    @Test
    fun fetchStatsRejectsBlankUsernamesGracefully() = runBlocking {
        val client = RatingApiClient()
        val chessComResult = client.fetchChessComStats("   ")
        assertTrue(chessComResult.isFailure)
        assertTrue(chessComResult.exceptionOrNull() is IllegalArgumentException)

        val lichessResult = client.fetchLichessStats("")
        assertTrue(lichessResult.isFailure)
        assertTrue(lichessResult.exceptionOrNull() is IllegalArgumentException)
    }
}
