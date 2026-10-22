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
 * kept as the labels of [BotOpeningChoices]; saving turns them into database names.
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
    val black: Set<String> = setOf("1...e5", "Queen's Gambit Declined"),
    val rules: GameRules = GameRules(),
    val createdAt: Long? = null,
) {
    val isNew: Boolean get() = id == null
    val trimmedName: String get() = name.trim()
    val canSave: Boolean get() = trimmedName.isNotEmpty() && trimmedName.length <= MAX_NAME

    /** A style preset also moves the two style sliders to its values. */
    fun withStyle(style: BotStyle): BotDraft = copy(style = style, aggression = style.aggression, trades = style.trades)

    fun toggleWhite(label: String): BotDraft = copy(white = white.toggle(label))
    fun toggleBlack(label: String): BotDraft = copy(black = black.toggle(label))

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
            whiteOpenings = namesOf(BotOpeningChoices.white, white),
            blackOpenings = namesOf(BotOpeningChoices.black, black),
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
                white = labelsOf(BotOpeningChoices.white, p.whiteOpenings),
                black = labelsOf(BotOpeningChoices.black, p.blackOpenings),
                rules = bot.rules, createdAt = bot.createdAt,
            )
        }

        private fun namesOf(choices: List<OpeningChoice>, labels: Set<String>): List<String> =
            choices.filter { it.label in labels }.flatMap { it.names }.distinct()

        private fun labelsOf(choices: List<OpeningChoice>, names: List<String>): Set<String> =
            choices.filter { names.containsAll(it.names) }.map { it.label }.toSet()

        private fun Set<String>.toggle(label: String) = if (label in this) this - label else this + label
    }
}
