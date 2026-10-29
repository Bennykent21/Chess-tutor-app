package com.chesstutor.app.domain.bot

import com.chesstutor.app.domain.game.GameRules
import kotlinx.coroutines.flow.Flow

/** A bot the player built (plan 6.1, "My bots"): its profile and the rules its games use. */
data class CustomBot(val profile: BotProfile, val rules: GameRules, val createdAt: Long) {
    val id: String get() = profile.id

    /** Shown like a roster bot: on cards, its page and in games. */
    fun asRosterBot(): RosterBot = RosterBot(profile, animal = profile.face, tagline = "You built me. Let's see if I learned anything.")

    companion object {
        /** Custom bot ids start with this, so they never clash with the roster. */
        const val ID_PREFIX = "my_"
    }
}

/** The player's saved bots. */
interface BotRepository {
    fun observe(): Flow<List<CustomBot>>
    suspend fun get(id: String): CustomBot?
    suspend fun save(bot: CustomBot)
    suspend fun delete(id: String)
}

/** One choice in the builder's opening lists; some choices stand for several database names. */
data class OpeningChoice(val label: String, val names: List<String>) {
    constructor(name: String) : this(name, listOf(name))
}

/**
 * The openings offered in the bot builder (plan 6.1: "pick openings it plays as White and as
 * Black"). Black answers to 1.e4 and to 1.d4 are listed apart so a bot can cover both.
 * Every name exists in the bundled opening database (tested).
 */
object BotOpeningChoices {
    val white: List<OpeningChoice> = listOf(
        OpeningChoice("Italian Game"), OpeningChoice("Ruy Lopez"), OpeningChoice("Scotch Game"),
        OpeningChoice("Vienna Game"), OpeningChoice("King's Gambit"), OpeningChoice("London System"),
        OpeningChoice("Queen's Gambit"), OpeningChoice("Catalan Opening"), OpeningChoice("English Opening"),
        OpeningChoice("Bird Opening"),
    )
    val blackVsE4: List<OpeningChoice> = listOf(
        OpeningChoice("1...e5", listOf("Italian Game", "Ruy Lopez", "Scotch Game", "Petrov's Defense")),
        OpeningChoice("Sicilian Defense"), OpeningChoice("French Defense"), OpeningChoice("Caro-Kann Defense"),
        OpeningChoice("Scandinavian Defense"), OpeningChoice("Pirc Defense"), OpeningChoice("Alekhine Defense"),
    )
    val blackVsD4: List<OpeningChoice> = listOf(
        OpeningChoice("Queen's Gambit Declined"), OpeningChoice("Slav Defense"), OpeningChoice("King's Indian Defense"),
        OpeningChoice("Nimzo-Indian Defense"), OpeningChoice("Grünfeld Defense"), OpeningChoice("Dutch Defense"),
        OpeningChoice("Benko Gambit"),
    )

    val black: List<OpeningChoice> get() = blackVsE4 + blackVsD4
}
