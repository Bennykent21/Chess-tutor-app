package com.chesstutor.app.domain.game

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.domain.bot.BotRoster
import com.chesstutor.app.domain.bot.RosterBot

/** Home's "Recommended" card (plan 6.1): which bot to play next, and why, in plain words. */
data class PlayRecommendation(val bot: RosterBot, val reason: String)

object PlayRecommender {

    /**
     * Looks at the player's recent games against roster bots, newest first: after a win,
     * the next bot up; after two losses in a row to the same bot, the one below; otherwise
     * a rematch. With no games yet, a bot a little below the player's rating.
     */
    fun recommend(recentGames: List<GameRecord>, playerRating: Int): PlayRecommendation {
        val bots = BotRoster.bots
        val botGames = recentGames.filter { game -> bots.any { it.name == game.botName } && game.id.startsWith("bot_") }
        val last = botGames.firstOrNull()
            ?: return BotRoster.closestTo(playerRating - 200).let { PlayRecommendation(it, "A friendly first game to warm up.") }
        val lastBot = bots.first { it.name == last.botName }
        val index = bots.indexOf(lastBot)
        return when {
            last.userOutcomeLabel == "Win" && index < bots.lastIndex ->
                bots[index + 1].let { PlayRecommendation(it, "You beat ${lastBot.name}. Ready for ${it.name}?") }
            last.userOutcomeLabel == "Win" -> PlayRecommendation(lastBot, "You beat the strongest bot. Again?")
            botGames.take(2).size == 2 && botGames.take(2).all { it.botName == lastBot.name && it.userOutcomeLabel == "Loss" } && index > 0 ->
                bots[index - 1].let { PlayRecommendation(it, "${lastBot.name} is tough. Warm up with ${it.name}.") }
            else -> PlayRecommendation(lastBot, "Rematch ${lastBot.name}?")
        }
    }
}
