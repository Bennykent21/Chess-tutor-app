package com.chesstutor.app.domain.bot

import com.chesstutor.app.domain.game.GameRules

/** The builder's "Mistakes" choice, as a multiplier on the rating's normal slip rate. */
enum class MistakeLevel(val label: String, val factor: Float) {
    NEVER("Never", 0f), RARE("Rare", 0.5f), NORMAL("Normal", 1f), OFTEN("Often", 2f);

    companion object {
        fun closestTo(factor: Float): MistakeLevel = entries.minBy { kotlin.math.abs(it.factor - factor) }
    }
}

/**
 * The bot builder's form (plan 6.1: one page, grouped controls, no dropdowns). Openings are
 * names from the opening database: a family ("Sicilian Defense") covers all its lines, a line
 * name covers just that line (see [com.chesstutor.app.domain.opening.Opening.isWithin]).
 */
data class BotDraft(
    val id: String? = null,
    val name: String = "",
    val face: String = "wayne",
    val rating: Int = 1200,
    val style: BotStyle = BotStyle.BALANCED,
    val aggression: Float = 0f,
    val trades: Float = 0f,
    val mistakes: MistakeLevel = MistakeLevel.NORMAL,
    val thinkSpeed: ThinkSpeed = ThinkSpeed.NORMAL,
    val resign: ResignPolicy = ResignPolicy.WHEN_LOST,
    val white: Set<String> = setOf("Italian Game"),
    val black: Set<String> = setOf("Italian Game", "Ruy Lopez", "Scotch Game", "Petrov's Defense", "Queen's Gambit Declined"),
    val rules: GameRules = GameRules(),
    val createdAt: Long? = null,
) {
    val isNew: Boolean get() = id == null
    val trimmedName: String get() = name.trim()
    val canSave: Boolean get() = trimmedName.isNotEmpty() && trimmedName.length <= MAX_NAME

    /** A style preset also moves the two style sliders to its values. */
    fun withStyle(style: BotStyle): BotDraft = copy(style = style, aggression = style.aggression, trades = style.trades)

    fun toggleWhite(name: String): BotDraft = copy(white = white.toggle(name))
    fun toggleBlack(name: String): BotDraft = copy(black = black.toggle(name))

    fun toCustomBot(newId: String, now: Long): CustomBot = CustomBot(
        profile = BotProfile(
            id = id ?: newId,
            name = trimmedName,
            rating = rating.coerceIn(BotProfile.MIN_RATING, BotProfile.MAX_RATING),
            style = style,
            aggression = aggression,
            trades = trades,
            mistakeLevel = mistakes.factor,
            thinkSpeed = thinkSpeed,
            resign = resign,
            whiteOpenings = white.sorted(),
            blackOpenings = black.sorted(),
            face = face,
        ),
        rules = rules,
        createdAt = createdAt ?: now,
    )

    companion object {
        const val MAX_NAME = 20

        /** Opens a saved bot for editing; a choice counts as picked when all its names are there. */
        fun from(bot: CustomBot): BotDraft {
            val p = bot.profile
            return BotDraft(
                id = p.id, name = p.name, face = p.face, rating = p.rating, style = p.style,
                aggression = p.aggression, trades = p.trades, mistakes = MistakeLevel.closestTo(p.mistakeLevel),
                thinkSpeed = p.thinkSpeed, resign = p.resign,
                white = p.whiteOpenings.toSet(),
                black = p.blackOpenings.toSet(),
                rules = bot.rules, createdAt = bot.createdAt,
            )
        }

        private fun Set<String>.toggle(label: String) = if (label in this) this - label else this + label
    }
}
