package com.chesstutor.app.domain.game

import com.chesstutor.app.domain.account.ThemeScore
import com.chesstutor.app.domain.bot.BotRoster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextStepTest {

    private val bot = PlayRecommendation(BotRoster.bots.first(), "Warm up.")

    @Test
    fun mistakesFirstThenTheWeakestThemeThenABot() {
        val forks = ThemeScore("fork", attempts = 10, clean = 3)
        val pins = ThemeScore("pin", attempts = 10, clean = 7)

        val mistakes = NextStepRecommender.recommend(dueMistakes = 3, weaknesses = listOf(forks), bot = bot)
        assertEquals(NextStep.Mistakes(3), mistakes)
        assertEquals("3 mistakes from your games are ready to fix.", mistakes.reason)

        val theme = NextStepRecommender.recommend(dueMistakes = 2, weaknesses = listOf(forks, pins), bot = bot)
        assertEquals(NextStep.Theme(forks), theme)
        assertTrue(theme.reason, theme.reason.startsWith("Fork puzzles trip you up (30% clean)"))

        // 70% clean is shaky on the Training page but not weak enough to push over a game.
        assertEquals(NextStep.Bot(bot), NextStepRecommender.recommend(dueMistakes = 0, weaknesses = listOf(pins), bot = bot))
        assertEquals("Warm up.", NextStepRecommender.recommend(0, emptyList(), bot).reason)
    }
}
