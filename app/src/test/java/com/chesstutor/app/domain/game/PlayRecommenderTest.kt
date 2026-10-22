package com.chesstutor.app.domain.game

import com.chesstutor.app.data.model.GameRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayRecommenderTest {

    private fun game(bot: String, outcome: String, at: Long, id: String = "bot_${bot.lowercase()}_$at") = GameRecord(
        id = id, dateMillis = at, botName = bot, botRating = 0,
        result = when (outcome) { "Win" -> "1-0"; "Loss" -> "0-1"; else -> "1/2-1/2" },
        pgn = "", moveCount = 20, userColor = "white", finalFen = "",
    )

    @Test
    fun aNewPlayerGetsABotALittleBelowTheirRating() {
        val rec = PlayRecommender.recommend(emptyList(), playerRating = 800)
        assertEquals("Wayne", rec.bot.name)
        assertTrue(rec.reason.contains("first game"))
    }

    @Test
    fun aWinPointsToTheNextBotUp() {
        val rec = PlayRecommender.recommend(listOf(game("Juno", "Win", 2)), 800)
        assertEquals("Bramble", rec.bot.name)
        assertEquals("You beat Juno. Ready for Bramble?", rec.reason)
    }

    @Test
    fun twoLossesInARowPointToTheBotBelow() {
        val rec = PlayRecommender.recommend(listOf(game("Juno", "Loss", 3), game("Juno", "Loss", 2)), 800)
        assertEquals("Wayne", rec.bot.name)
    }

    @Test
    fun oneLossOrADrawOffersARematch() {
        assertEquals("Rematch Juno?", PlayRecommender.recommend(listOf(game("Juno", "Loss", 3), game("Juno", "Win", 2)), 800).reason)
        assertEquals("Juno", PlayRecommender.recommend(listOf(game("Juno", "Draw", 3)), 800).bot.name)
    }

    @Test
    fun importedAndOldArenaGamesAreIgnored() {
        val imported = game("Juno", "Win", 5, id = "chesscom_123")
        assertEquals("Wayne", PlayRecommender.recommend(listOf(imported), 800).bot.name)
    }

    @Test
    fun beatingTheStrongestBotOffersItAgain() {
        assertEquals("Bolt", PlayRecommender.recommend(listOf(game("Bolt", "Win", 1)), 2800).bot.name)
    }
}
