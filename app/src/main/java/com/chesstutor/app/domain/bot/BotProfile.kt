package com.chesstutor.app.domain.bot

/** The playing styles a bot can have (plan 6.1); each sets how it leans among good moves. */
enum class BotStyle(val label: String, val aggression: Float, val trades: Float) {
    BALANCED("Balanced", 0f, 0f),
    ATTACKER("Attacker", 0.9f, -0.5f),
    DEFENDER("Defender", -0.6f, 0.2f),
    GAMBITEER("Gambit lover", 0.7f, -0.3f),
    GRINDER("Endgame grinder", -0.3f, 0.8f),
    TRICKSTER("Trickster", 0.5f, -0.2f),
}

/** How long a bot pauses before it moves, on top of the engine's search. */
enum class ThinkSpeed(val factor: Float) { FAST(0.5f), NORMAL(1f), SLOW(1.6f) }

/** When a bot gives up a lost game. */
enum class ResignPolicy { NEVER, WHEN_LOST, EARLY }

/**
 * Everything that makes a bot play the way it does. Roster bots and the player's own bots
 * ("My bots", step 3D) are both profiles.
 *
 * [rating] is the target strength on the app's own scale: it sets how far from the best
 * move the bot may stray and how often it slips. It is an approximation, not a FIDE rating.
 */
data class BotProfile(
    val id: String,
    val name: String,
    val rating: Int,
    val style: BotStyle = BotStyle.BALANCED,
    /** -1 calm and positional, +1 always looking for the attack. */
    val aggression: Float = style.aggression,
    /** -1 keeps pieces on the board, +1 trades down whenever it can. */
    val trades: Float = style.trades,
    /** Scales how often it makes mistakes: 0 never, 1 normal for its rating, 2 twice as often. */
    val mistakeLevel: Float = 1f,
    val thinkSpeed: ThinkSpeed = ThinkSpeed.NORMAL,
    val resign: ResignPolicy = ResignPolicy.WHEN_LOST,
    /** Opening names it plays as White and as Black (see [com.chesstutor.app.domain.opening.Repertoire]). */
    val whiteOpenings: List<String> = emptyList(),
    val blackOpenings: List<String> = emptyList(),
) {
    init {
        require(rating in MIN_RATING..MAX_RATING) { "rating $rating outside $MIN_RATING..$MAX_RATING" }
    }

    companion object {
        const val MIN_RATING = 250
        const val MAX_RATING = 3000
    }
}
