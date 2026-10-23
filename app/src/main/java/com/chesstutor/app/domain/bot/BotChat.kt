package com.chesstutor.app.domain.bot

import kotlin.random.Random

/** Moments a bot may talk about during a game (review R6). */
enum class ChatEvent {
    GREETING, BOT_CAPTURES, PLAYER_CAPTURES, BOT_CHECKS, PLAYER_CHECKS,
    PLAYER_BLUNDER, TAKEBACK, BOT_WINS, PLAYER_WINS, DRAW,
}

/** How chatty bots are; a player setting. */
enum class ChatLevel(val label: String) { OFF("Off"), SOME("Some"), LOTS("Lots") }

/**
 * Each roster bot's voice: a few short lines per moment, in character. Bots the player built
 * talk by their style. Lines never mention the engine's evaluation, so they stay friendly.
 */
object BotChat {

    /** A line for [event] from the bot [botId] (style [style] for the player's own bots), or null. */
    fun lineFor(botId: String, style: BotStyle, event: ChatEvent, random: Random): String? {
        val lines = roster[botId]?.get(event) ?: byStyle[style]?.get(event) ?: general[event]
        return lines?.takeIf { it.isNotEmpty() }?.random(random)
    }

    /** Whether a move-by-move moment is worth a line at [level]; greetings and results always are. */
    fun shouldSpeak(event: ChatEvent, level: ChatLevel, random: Random): Boolean = when {
        level == ChatLevel.OFF -> false
        event in ALWAYS -> true
        level == ChatLevel.LOTS -> random.nextDouble() < 0.8
        else -> random.nextDouble() < 0.35
    }

    private val ALWAYS = setOf(ChatEvent.GREETING, ChatEvent.BOT_WINS, ChatEvent.PLAYER_WINS, ChatEvent.DRAW, ChatEvent.PLAYER_BLUNDER)

    private val general: Map<ChatEvent, List<String>> = mapOf(
        ChatEvent.GREETING to listOf("Good luck, have fun!", "Let's play!"),
        ChatEvent.BOT_CAPTURES to listOf("I'll take that.", "Thank you!"),
        ChatEvent.PLAYER_CAPTURES to listOf("Hey, that was mine!", "Fair enough."),
        ChatEvent.BOT_CHECKS to listOf("Check!"),
        ChatEvent.PLAYER_CHECKS to listOf("Whoa, check.", "Careful with my king!"),
        ChatEvent.PLAYER_BLUNDER to listOf("Are you sure about that?", "Ooh, I think I see something."),
        ChatEvent.TAKEBACK to listOf("Changed your mind?", "Try again, then."),
        ChatEvent.BOT_WINS to listOf("Good game! Want a rematch?"),
        ChatEvent.PLAYER_WINS to listOf("Well played, you got me!"),
        ChatEvent.DRAW to listOf("A draw. Fair result!"),
    )

    private val byStyle: Map<BotStyle, Map<ChatEvent, List<String>>> = mapOf(
        BotStyle.ATTACKER to mapOf(
            ChatEvent.GREETING to listOf("Ready? I'm coming for your king."),
            ChatEvent.BOT_CHECKS to listOf("Check! Here I come!", "Your king looks lonely."),
            ChatEvent.BOT_WINS to listOf("Attack always wins!"),
        ),
        BotStyle.DEFENDER to mapOf(
            ChatEvent.GREETING to listOf("Let's see if you can break through."),
            ChatEvent.PLAYER_CHECKS to listOf("That doesn't scare me."),
            ChatEvent.BOT_WINS to listOf("Patience pays off."),
        ),
        BotStyle.GAMBITEER to mapOf(
            ChatEvent.GREETING to listOf("Fancy a pawn? I've got plenty."),
            ChatEvent.PLAYER_CAPTURES to listOf("Take it, take it all!"),
        ),
        BotStyle.GRINDER to mapOf(
            ChatEvent.GREETING to listOf("Let's play a long one."),
            ChatEvent.BOT_CAPTURES to listOf("One less piece. Getting closer to the endgame."),
        ),
        BotStyle.TRICKSTER to mapOf(
            ChatEvent.GREETING to listOf("Watch closely..."),
            ChatEvent.PLAYER_BLUNDER to listOf("Hehe. You fell for it."),
        ),
    )

    private val roster: Map<String, Map<ChatEvent, List<String>>> = mapOf(
        "pebble" to mapOf(
            ChatEvent.GREETING to listOf("Hi! I'm Pebble. I'm still learning how the pieces move...", "Hello! Please go easy on me."),
            ChatEvent.BOT_CAPTURES to listOf("Ooh, I took something!", "Is that allowed? Yay!"),
            ChatEvent.PLAYER_CAPTURES to listOf("Oh no, my piece!", "Wait, where did it go?"),
            ChatEvent.BOT_CHECKS to listOf("Check! Did I do that right?"),
            ChatEvent.PLAYER_CHECKS to listOf("Eek! My king!"),
            ChatEvent.PLAYER_BLUNDER to listOf("Hmm, is that piece free?"),
            ChatEvent.TAKEBACK to listOf("I take moves back all the time too!"),
            ChatEvent.BOT_WINS to listOf("I won?! Really? Wow!"),
            ChatEvent.PLAYER_WINS to listOf("You're good! Can you teach me?"),
            ChatEvent.DRAW to listOf("A draw! That's like both of us winning."),
        ),
        "wayne" to mapOf(
            ChatEvent.GREETING to listOf("Hey there! Ready for a friendly game?", "Hiya! Grab a snack, let's play."),
            ChatEvent.BOT_CAPTURES to listOf("Yum, a free piece!", "Don't mind if I do."),
            ChatEvent.PLAYER_CAPTURES to listOf("Oops. I was going to move that.", "Hey, I needed that one!"),
            ChatEvent.BOT_CHECKS to listOf("Check! Bear hug for your king."),
            ChatEvent.PLAYER_CHECKS to listOf("Uh oh, my king's in trouble."),
            ChatEvent.PLAYER_BLUNDER to listOf("Wait a second... is that a gift?"),
            ChatEvent.TAKEBACK to listOf("No worries, take your time."),
            ChatEvent.BOT_WINS to listOf("Good game! That was fun."),
            ChatEvent.PLAYER_WINS to listOf("Nicely done! You beat me fair and square."),
            ChatEvent.DRAW to listOf("Even steven! Good game."),
        ),
        "juno" to mapOf(
            ChatEvent.GREETING to listOf("Hello! Don't trust anything I do.", "Let's play. I promise there are no traps. Probably."),
            ChatEvent.BOT_CAPTURES to listOf("Snatched it!", "Quick paws!"),
            ChatEvent.PLAYER_CAPTURES to listOf("Ah, you saw that one.", "Fine, keep it. I have other plans."),
            ChatEvent.BOT_CHECKS to listOf("Check! Surprise!"),
            ChatEvent.PLAYER_CHECKS to listOf("Sneaky! I like it."),
            ChatEvent.PLAYER_BLUNDER to listOf("Hehe, I was hoping you'd do that.", "Oh, you walked right into it."),
            ChatEvent.TAKEBACK to listOf("Smelled a trap, did you?"),
            ChatEvent.BOT_WINS to listOf("Tricked you! Good game."),
            ChatEvent.PLAYER_WINS to listOf("Outfoxed the fox. Well played!"),
            ChatEvent.DRAW to listOf("A draw? I'll get you next time."),
        ),
        "bramble" to mapOf(
            ChatEvent.GREETING to listOf("Hello. My defences are ready.", "Come on then, try to get through."),
            ChatEvent.BOT_CAPTURES to listOf("Prickly, aren't I?", "That's what you get for coming close."),
            ChatEvent.PLAYER_CAPTURES to listOf("Ouch. I'll curl up tighter.", "Hmph. Still safe."),
            ChatEvent.BOT_CHECKS to listOf("Check. Defence can bite too."),
            ChatEvent.PLAYER_CHECKS to listOf("My spikes are up, don't worry."),
            ChatEvent.PLAYER_BLUNDER to listOf("Was that piece supposed to be undefended?"),
            ChatEvent.TAKEBACK to listOf("Wise. Think it through."),
            ChatEvent.BOT_WINS to listOf("Nobody gets past these spikes!"),
            ChatEvent.PLAYER_WINS to listOf("You broke through. Impressive."),
            ChatEvent.DRAW to listOf("Solid on both sides. A draw."),
        ),
        "ziggy" to mapOf(
            ChatEvent.GREETING to listOf("Attack! Attack! Squawk!", "Hello hello! Let's go, let's go!"),
            ChatEvent.BOT_CAPTURES to listOf("Mine! Mine!", "Squawk! Got it!"),
            ChatEvent.PLAYER_CAPTURES to listOf("Rude! Rude!", "Hey! Give it back!"),
            ChatEvent.BOT_CHECKS to listOf("Check! Check! Squawk!", "King in danger! Danger!"),
            ChatEvent.PLAYER_CHECKS to listOf("Eek! Feathers everywhere!"),
            ChatEvent.PLAYER_BLUNDER to listOf("Ooh! Pretty piece! Shiny!"),
            ChatEvent.TAKEBACK to listOf("Again! Again!"),
            ChatEvent.BOT_WINS to listOf("Victory! Victory! Squawk!"),
            ChatEvent.PLAYER_WINS to listOf("Squawk... well played, well played."),
            ChatEvent.DRAW to listOf("Draw! Draw! Again?"),
        ),
        "marlo" to mapOf(
            ChatEvent.GREETING to listOf("Care for a little gamble?", "Psst. Want a free pawn?"),
            ChatEvent.BOT_CAPTURES to listOf("Collecting my winnings.", "A raccoon never leaves empty-handed."),
            ChatEvent.PLAYER_CAPTURES to listOf("Go on, take it. Interest is coming.", "Every gambit has its price."),
            ChatEvent.BOT_CHECKS to listOf("Check. The bill's due."),
            ChatEvent.PLAYER_CHECKS to listOf("Bold move, partner."),
            ChatEvent.PLAYER_BLUNDER to listOf("Ooh, finders keepers."),
            ChatEvent.TAKEBACK to listOf("No bets taken back here... fine, just this once."),
            ChatEvent.BOT_WINS to listOf("The house always wins."),
            ChatEvent.PLAYER_WINS to listOf("You called my bluff. Respect."),
            ChatEvent.DRAW to listOf("We split the pot."),
        ),
        "tessa" to mapOf(
            ChatEvent.GREETING to listOf("Hello. Let's take our time.", "No rush. The endgame will come."),
            ChatEvent.BOT_CAPTURES to listOf("A fair trade. Fewer pieces suit me.", "Simplifying nicely."),
            ChatEvent.PLAYER_CAPTURES to listOf("Mm. I'll make it back slowly.", "Noted."),
            ChatEvent.BOT_CHECKS to listOf("Check. Steady now."),
            ChatEvent.PLAYER_CHECKS to listOf("Calm waters. I'm fine."),
            ChatEvent.PLAYER_BLUNDER to listOf("That pawn will matter later."),
            ChatEvent.TAKEBACK to listOf("Good idea. Check the endgame first."),
            ChatEvent.BOT_WINS to listOf("Slow and steady. Good game."),
            ChatEvent.PLAYER_WINS to listOf("You out-ground the grinder. Well played."),
            ChatEvent.DRAW to listOf("A draw. A fair way to end a long game."),
        ),
        "kofi" to mapOf(
            ChatEvent.GREETING to listOf("Let's see if you have the heart of a lion.", "Show me what you've got."),
            ChatEvent.BOT_CAPTURES to listOf("Roar! Mine now.", "The lion feeds."),
            ChatEvent.PLAYER_CAPTURES to listOf("A brave strike.", "You'll pay for that."),
            ChatEvent.BOT_CHECKS to listOf("Check! Your king trembles.", "Check. The hunt is on."),
            ChatEvent.PLAYER_CHECKS to listOf("A lion fears nothing."),
            ChatEvent.PLAYER_BLUNDER to listOf("I smell weakness."),
            ChatEvent.TAKEBACK to listOf("Hesitation? A true hunter commits."),
            ChatEvent.BOT_WINS to listOf("King of the board. Good game."),
            ChatEvent.PLAYER_WINS to listOf("You fought like a lion today."),
            ChatEvent.DRAW to listOf("Two lions, no winner."),
        ),
        "ingrid" to mapOf(
            ChatEvent.GREETING to listOf("Good day. Let's keep it tidy.", "Hello. Stay cool."),
            ChatEvent.BOT_CAPTURES to listOf("Taken. Cleanly.", "Thank you."),
            ChatEvent.PLAYER_CAPTURES to listOf("Fine. I had planned for that.", "Cold, but correct."),
            ChatEvent.BOT_CHECKS to listOf("Check. Chilly, isn't it?"),
            ChatEvent.PLAYER_CHECKS to listOf("I'm not worried."),
            ChatEvent.PLAYER_BLUNDER to listOf("That was a slip on the ice."),
            ChatEvent.TAKEBACK to listOf("Very well. Again."),
            ChatEvent.BOT_WINS to listOf("A well-defended game. Thank you."),
            ChatEvent.PLAYER_WINS to listOf("You cracked the ice. Well done."),
            ChatEvent.DRAW to listOf("A cool draw."),
        ),
        "nova" to mapOf(
            ChatEvent.GREETING to listOf("Mrrow. Let's play something unexpected.", "The stars say you'll be surprised today."),
            ChatEvent.BOT_CAPTURES to listOf("Pounce!", "Mine. Purr."),
            ChatEvent.PLAYER_CAPTURES to listOf("Hiss. I meant to do that.", "Cats always land on their feet."),
            ChatEvent.BOT_CHECKS to listOf("Check. Did you see that coming?"),
            ChatEvent.PLAYER_CHECKS to listOf("Curious move. Very curious."),
            ChatEvent.PLAYER_BLUNDER to listOf("Oh, a little mouse came out to play."),
            ChatEvent.TAKEBACK to listOf("Nine lives? Fine, take one."),
            ChatEvent.BOT_WINS to listOf("Purrfect. Good game."),
            ChatEvent.PLAYER_WINS to listOf("You surprised me. That's rare."),
            ChatEvent.DRAW to listOf("A draw. How mysterious."),
        ),
        "titan" to mapOf(
            ChatEvent.GREETING to listOf("A challenger approaches. Very well.", "Few have survived my fire."),
            ChatEvent.BOT_CAPTURES to listOf("Burned to ash.", "Another piece for my hoard."),
            ChatEvent.PLAYER_CAPTURES to listOf("A scratch on my scales.", "Bold. Foolish, but bold."),
            ChatEvent.BOT_CHECKS to listOf("Check. Feel the heat.", "Your king is in my shadow."),
            ChatEvent.PLAYER_CHECKS to listOf("You dare?"),
            ChatEvent.PLAYER_BLUNDER to listOf("A mistake. Dragons never forget."),
            ChatEvent.TAKEBACK to listOf("Even legends hesitate, it seems."),
            ChatEvent.BOT_WINS to listOf("The dragon remains undefeated."),
            ChatEvent.PLAYER_WINS to listOf("You have slain the dragon. Remarkable."),
            ChatEvent.DRAW to listOf("A truce, then. For now."),
        ),
        "bolt" to mapOf(
            ChatEvent.GREETING to listOf("Beep boop. Calculating your defeat.", "Hello, human. Initialising game."),
            ChatEvent.BOT_CAPTURES to listOf("Material gained. Efficiency: high.", "Capture executed."),
            ChatEvent.PLAYER_CAPTURES to listOf("Unexpected. Recalculating.", "Error: piece missing."),
            ChatEvent.BOT_CHECKS to listOf("Check. Probability of escape: low."),
            ChatEvent.PLAYER_CHECKS to listOf("King threat detected. Responding."),
            ChatEvent.PLAYER_BLUNDER to listOf("Blunder detected. Thank you."),
            ChatEvent.TAKEBACK to listOf("Undo accepted. Memory wiped."),
            ChatEvent.BOT_WINS to listOf("Game over. Result: as calculated."),
            ChatEvent.PLAYER_WINS to listOf("Does not compute... Congratulations, human!"),
            ChatEvent.DRAW to listOf("Draw. Both systems stable."),
        ),
    )

    /** Roster ids with their own voice, for tests. */
    val voicedBots: Set<String> get() = roster.keys
}
