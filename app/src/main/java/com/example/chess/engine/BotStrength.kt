package com.example.chess.engine

/**
 * Canonical bot-strength scale used across the app.
 *
 * These values are Stockfish UCI_Elo configuration targets, not claims about
 * equivalent human or FIDE playing strength.
 */
data class BotStrengthPreset(
    val key: String,
    val rating: Int,
    val name: String,
    val description: String
)

object BotStrength {
    val presets: List<BotStrengthPreset> = listOf(
        BotStrengthPreset("Beginner", 250, "Martin", "Frequent simple mistakes"),
        BotStrengthPreset("Casual", 600, "Wayne", "Misses many basic tactics"),
        BotStrengthPreset("Intermediate", 1000, "Casual", "Understands fundamentals"),
        BotStrengthPreset("Club", 1300, "Nelson", "Solid club-level challenge"),
        BotStrengthPreset("Advanced", 1600, "Elena", "Strong practical play"),
        BotStrengthPreset("Expert", 2000, "Expert", "Punishes most inaccuracies"),
        BotStrengthPreset("Master", 2400, "Master", "Very strong tactical play"),
        BotStrengthPreset("Grandmaster", 3200, "Stockfish", "Maximum preset strength")
    )

    fun presetForKey(key: String): BotStrengthPreset? =
        presets.firstOrNull { it.key.equals(key, ignoreCase = true) }
}