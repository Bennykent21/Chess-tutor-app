package com.chesstutor.app.domain.opening

import kotlin.random.Random

/** Opening Battle's three modes (plan 6.1). */
enum class BattleMode(val label: String, val description: String) {
    PLAY("Play it", "The bot follows the line to its end, then plays freely."),
    DRILL("Drill it", "Book moves only. A move off the line is taken back so you can try again."),
    SURPRISE("Surprise me", "The bot picks a variation of this opening for you to meet."),
}

/** What the player chose: a line, a mode, and whether the bot replies with exactly that line. */
data class BattleChoice(val line: Opening, val mode: BattleMode, val exactReplies: Boolean = true)

/**
 * The book for one battle: [repertoire] holds the moves both sides may play from the book,
 * [target] the line being met (for Surprise me, the variation the bot picked).
 */
data class BattlePlan(
    val choice: BattleChoice,
    val target: Opening,
    val repertoire: Repertoire,
) {
    val isDrill: Boolean get() = choice.mode == BattleMode.DRILL
    /** "Sicilian Defense: Najdorf Variation", or the family alone for a family-wide battle. */
    val title: String get() = if (choice.exactReplies || choice.mode == BattleMode.SURPRISE) target.name else target.family
}

object OpeningBattles {

    /**
     * Builds the battle's book. Exact replies follow the chosen line only; otherwise every line
     * of its family counts as book. Surprise me picks one line of the family at random.
     */
    fun plan(tree: OpeningTree, choice: BattleChoice, random: Random = Random.Default): BattlePlan {
        val family = tree.linesWithin(listOf(choice.line.family)).ifEmpty { listOf(choice.line) }
        return when {
            choice.mode == BattleMode.SURPRISE -> {
                // Prefer real variations (longer than the family's own first moves).
                val pool = family.filter { it.moves.size > choice.line.moves.size }.ifEmpty { family }
                val target = pool.random(random)
                BattlePlan(choice, target, Repertoire.line(target.moves))
            }
            choice.exactReplies -> BattlePlan(choice, choice.line, Repertoire.line(choice.line.moves))
            else -> BattlePlan(choice, choice.line, Repertoire.of(tree, listOf(choice.line.family), listOf(choice.line.family)))
        }
    }

    /** Families for the picker: the biggest first, or those matching [query] in any line name. */
    fun searchFamilies(tree: OpeningTree, query: String, limit: Int = 60): List<OpeningFamily> {
        val q = query.trim()
        if (q.isEmpty()) return tree.families.take(limit)
        return tree.families.filter { family -> family.name.contains(q, ignoreCase = true) || family.lines.any { it.name.contains(q, ignoreCase = true) } }
    }
}

/** One opening inside a family ("Najdorf Variation") and its lines; the family's own lines are "Main line". */
data class OpeningGroup(val name: String, val lines: List<Opening>)

/** The family's lines grouped by opening, main line first, then the biggest groups. */
fun OpeningFamily.groups(): List<OpeningGroup> =
    lines.groupBy { it.variation.substringBefore(',').trim().ifEmpty { MAIN_LINE } }
        .map { (name, lines) -> OpeningGroup(name, lines) }
        .sortedWith(compareBy<OpeningGroup> { it.name != MAIN_LINE }.thenByDescending { it.lines.size }.thenBy { it.name })

const val MAIN_LINE = "Main line"
