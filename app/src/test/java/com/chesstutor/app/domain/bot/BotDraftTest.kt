package com.chesstutor.app.domain.bot

import com.chesstutor.app.data.bot.RoomBotRepository
import com.chesstutor.app.domain.game.GameRules
import com.chesstutor.app.domain.game.TimeControl
import com.chesstutor.app.domain.opening.OpeningTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BotDraftTest {

    @Test
    fun aBotNeedsANameOfAtMostTwentyLetters() {
        assertFalse(BotDraft().canSave)
        assertFalse(BotDraft(name = "   ").canSave)
        assertTrue(BotDraft(name = " Captain Fork ").canSave)
        assertFalse(BotDraft(name = "x".repeat(21)).canSave)
    }

    @Test
    fun aStylePresetMovesBothSliders() {
        val draft = BotDraft().withStyle(BotStyle.GRINDER)
        assertEquals(BotStyle.GRINDER.aggression, draft.aggression)
        assertEquals(BotStyle.GRINDER.trades, draft.trades)
    }

    @Test
    fun savingTurnsChoicesIntoOpeningNamesAndBack() {
        val draft = BotDraft(name = " Captain Fork ", face = "kofi", rating = 1450, mistakes = MistakeLevel.RARE)
            .toggleWhite("Ruy Lopez").toggleWhite("Italian Game")
            .toggleBlack("Sicilian Defense").toggleBlack("Italian Game")
            .copy(rules = GameRules(TimeControl.BLITZ_5, takebacks = false))
        val bot = draft.toCustomBot(newId = "my_1", now = 99)
        assertEquals("Captain Fork", bot.profile.name)
        assertEquals("my_1", bot.id)
        assertEquals("kofi", bot.profile.face)
        assertEquals(0.5f, bot.profile.mistakeLevel)
        assertEquals(listOf("Ruy Lopez"), bot.profile.whiteOpenings)
        assertEquals(listOf("Petrov's Defense", "Queen's Gambit Declined", "Ruy Lopez", "Scotch Game", "Sicilian Defense"), bot.profile.blackOpenings)
        assertEquals(99, bot.createdAt)

        val reopened = BotDraft.from(bot)
        assertEquals(setOf("Ruy Lopez"), reopened.white)
        assertEquals(setOf("Petrov's Defense", "Queen's Gambit Declined", "Ruy Lopez", "Scotch Game", "Sicilian Defense"), reopened.black)
        assertEquals(MistakeLevel.RARE, reopened.mistakes)
        assertEquals("editing keeps the id and creation time", bot, reopened.toCustomBot(newId = "my_2", now = 500))
    }

    @Test
    fun theDatabaseRowRoundTrips() {
        val bot = BotDraft(name = "Rowan", style = BotStyle.ATTACKER, resign = ResignPolicy.EARLY)
            .withStyle(BotStyle.ATTACKER)
            .toggleBlack("Grünfeld Defense")
            .toCustomBot("my_7", 10)
        val row = RoomBotRepository.toEntity(bot, now = 20)
        assertEquals("Italian Game", row.whiteOpenings)
        assertEquals(20, row.updatedAt)
        assertEquals(bot, RoomBotRepository.toDomain(row))
        assertNull("an unreadable row is skipped, not a crash", RoomBotRepository.toDomain(row.copy(style = "WIZARD")))
    }

    @Test
    fun theDefaultRepertoireExistsInTheDatabase() {
        val tree = File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream)
        (BotDraft().white + BotDraft().black).forEach { name ->
            assertTrue("\"$name\" names no line", tree.linesWithin(listOf(name)).isNotEmpty())
        }
    }

    @Test
    fun everyBuilderOpeningExistsInTheDatabase() {
        val tree = File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream)
        (BotOpeningChoices.white + BotOpeningChoices.black).flatMap { it.names }.forEach { name ->
            assertTrue("\"$name\" names no line", tree.linesWithin(listOf(name)).isNotEmpty())
        }
    }

    @Test
    fun theBuilderNeverReusesARosterId() {
        assertTrue(BotRoster.bots.none { it.id.startsWith(CustomBot.ID_PREFIX) })
    }
}
