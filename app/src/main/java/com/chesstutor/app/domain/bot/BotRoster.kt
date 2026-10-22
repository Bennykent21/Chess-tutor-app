package com.chesstutor.app.domain.bot

/** A roster bot: its profile plus the words shown on its card. */
data class RosterBot(val profile: BotProfile, val animal: String, val tagline: String) {
    val id: String get() = profile.id
    val name: String get() = profile.name
    val rating: Int get() = profile.rating
}

/**
 * The twelve bots on Home (plan 6.1), weakest first. Original characters and names; Wayne is
 * kept from the old Arena. Each Black repertoire answers both 1.e4 and 1.d4.
 */
object BotRoster {
    private fun bot(
        id: String, name: String, rating: Int, style: BotStyle, animal: String, tagline: String,
        white: List<String>, black: List<String>, resign: ResignPolicy = ResignPolicy.WHEN_LOST,
    ) = RosterBot(BotProfile(id, name, rating, style, resign = resign, whiteOpenings = white, blackOpenings = black), animal, tagline)

    val bots: List<RosterBot> = listOf(
        bot("pebble", "Pebble", 250, BotStyle.BALANCED, "snail", "Just learning how the pieces move.",
            listOf("Italian Game"), listOf("Italian Game", "Queen's Gambit Declined"), ResignPolicy.NEVER),
        bot("wayne", "Wayne", 600, BotStyle.BALANCED, "bear cub", "Friendly, but leaves pieces hanging.",
            listOf("Italian Game"), listOf("Scandinavian Defense", "Queen's Gambit Declined")),
        bot("juno", "Juno", 800, BotStyle.TRICKSTER, "fox", "Loves a cheeky trap.",
            listOf("Vienna Game"), listOf("Alekhine Defense", "Indian Defense: Budapest Gambit")),
        bot("bramble", "Bramble", 1000, BotStyle.DEFENDER, "hedgehog", "Curls up and dares you to attack.",
            listOf("London System"), listOf("Caro-Kann Defense", "Slav Defense")),
        bot("ziggy", "Ziggy", 1200, BotStyle.ATTACKER, "parrot", "Goes straight for your king.",
            listOf("King's Gambit"), listOf("Sicilian Defense: Dragon Variation", "King's Indian Defense")),
        bot("marlo", "Marlo", 1400, BotStyle.GAMBITEER, "raccoon", "Gives a pawn away for a wild game.",
            listOf("Italian Game: Evans Gambit", "Danish Gambit", "Sicilian Defense: Smith-Morra Gambit"),
            listOf("Latvian Gambit", "Benko Gambit")),
        bot("tessa", "Tessa", 1600, BotStyle.GRINDER, "otter", "Trades down and squeezes the endgame.",
            listOf("Queen's Gambit"), listOf("French Defense", "Queen's Gambit Declined")),
        bot("kofi", "Kofi", 1800, BotStyle.ATTACKER, "lion", "Sharp, fast and fearless.",
            listOf("Ruy Lopez", "Scotch Game"), listOf("Sicilian Defense: Najdorf Variation", "King's Indian Defense")),
        bot("ingrid", "Ingrid", 2000, BotStyle.DEFENDER, "penguin", "Cool, careful and hard to break.",
            listOf("Catalan Opening", "English Opening"), listOf("Caro-Kann Defense", "Nimzo-Indian Defense")),
        bot("nova", "Nova", 2200, BotStyle.TRICKSTER, "cat", "Plays the move you did not expect.",
            listOf("Bird Opening", "Vienna Game"), listOf("Pirc Defense", "Dutch Defense")),
        bot("titan", "Titan", 2500, BotStyle.ATTACKER, "dragon", "A master-strength fire breather.",
            listOf("Ruy Lopez", "Queen's Gambit Declined"), listOf("Sicilian Defense", "Grünfeld Defense")),
        bot("bolt", "Bolt", 3000, BotStyle.BALANCED, "robot", "Stockfish at full strength. Good luck!",
            emptyList(), emptyList(), ResignPolicy.NEVER),
    )

    fun byId(id: String): RosterBot? = bots.firstOrNull { it.id == id }

    /** The bot closest to [rating], for "Quick match". */
    fun closestTo(rating: Int): RosterBot = bots.minBy { kotlin.math.abs(it.rating - rating) }
}
