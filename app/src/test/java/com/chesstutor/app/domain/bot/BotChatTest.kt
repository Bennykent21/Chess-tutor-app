package com.chesstutor.app.domain.bot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BotChatTest {

    @Test
    fun everyRosterBotHasItsOwnVoiceForEveryMoment() {
        assertEquals(BotRoster.bots.map { it.id }.toSet(), BotChat.voicedBots)
        BotRoster.bots.forEach { bot ->
            ChatEvent.entries.forEach { event ->
                assertNotNull("${bot.name} says nothing for $event", BotChat.lineFor(bot.id, bot.profile.style, event, Random(1)))
            }
        }
    }

    @Test
    fun theirOwnBotsTalkByStyleThenInGeneral() {
        val attackerGreeting = BotChat.lineFor("my_1", BotStyle.ATTACKER, ChatEvent.GREETING, Random(1))
        assertEquals("Ready? I'm coming for your king.", attackerGreeting)
        assertNotNull("no style line for takebacks: the general one", BotChat.lineFor("my_1", BotStyle.ATTACKER, ChatEvent.TAKEBACK, Random(1)))
    }

    @Test
    fun theSettingDecidesHowOftenBotsSpeak() {
        assertFalse((0 until 50).any { BotChat.shouldSpeak(ChatEvent.GREETING, ChatLevel.OFF, Random(it)) })
        assertTrue("greetings and results always", (0 until 50).all { BotChat.shouldSpeak(ChatEvent.BOT_WINS, ChatLevel.SOME, Random(it)) })
        val some = (0 until 400).count { BotChat.shouldSpeak(ChatEvent.BOT_CAPTURES, ChatLevel.SOME, Random(it)) }
        val lots = (0 until 400).count { BotChat.shouldSpeak(ChatEvent.BOT_CAPTURES, ChatLevel.LOTS, Random(it)) }
        assertTrue("some $some, lots $lots", some in 80..200 && lots > some + 100)
    }
}
