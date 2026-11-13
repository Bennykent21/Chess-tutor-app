package com.chesstutor.app.domain.repertoire

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.SanFormatter

/**
 * One way the opponent can answer the opening (D77): its first move, numbered ("3...Nf6"), the
 * opening database's name for it when there is one ("Two Knights Defense"), how often players
 * at the learner's level choose it, and the learner's cards in it up to the open level.
 */
data class Variation(
    /** The moves from the start to the opponent's choice, for learning just this variation. */
    val path: List<String>,
    val move: String,
    val name: String?,
    val share: Double,
    val cards: Int,
    /** Cards met at least once, and cards recalled right on two different days (stage 1 or more). */
    val introduced: Int,
    val solid: Int,
) {
    val label: String get() = name ?: move
}

/**
 * The opening page's picture of mastery (D77): the variations the learner meets, most common
 * first, and how ready the learner is overall.
 */
object RepertoireVariations {
    /** A reply more common than this is split into the variations that follow it (the Caro-Kann's 2.d4). */
    const val SPLIT_SHARE = 0.40

    /**
     * The opponent's choices at the first branch after the opening's defining moves. A choice
     * that most games take and that has no variation name of its own is split into the choices
     * after it, its share passed on, so the Caro-Kann lists the Advance, the Exchange and the main
     * line instead of one big "2.d4", while the Italian keeps the Two Knights and the Giuoco Piano.
     */
    fun of(index: RepertoireIndex, band: Int?, open: RepertoireLevel, states: Map<String, CardState>): List<Variation> {
        val book = index.book
        val first = branchFrom(book, book.startPosition, book.start) ?: return emptyList()
        val rows = ArrayList<Variation>()
        for (choice in choicesAt(book, first.first, first.second, band, 1.0)) {
            val named = variationName(book.at(choice.after)?.name) != null
            val deeper = if (choice.share > SPLIT_SHARE && !named) branchFrom(book, choice.after, choice.path) else null
            val parts = deeper?.let { (position, path) -> choicesAt(book, position, path, band, choice.share) }.orEmpty()
            if (parts.size >= 2) rows += parts.map { row(index, it, open, states) } else rows += row(index, choice, open, states)
        }
        return rows.sortedByDescending { it.share }
    }

    /**
     * How ready the learner is: the cards of the open levels, each weighted by how often the
     * learner meets it, and the share of that weight the learner knows solidly. 0 to 1.
     */
    fun readiness(book: RepertoireBook, open: RepertoireLevel, states: Map<String, CardState>): Double {
        val cards = book.cards(open)
        val total = cards.sumOf { it.reach }
        if (total <= 0.0) return 0.0
        return cards.filter { (states[it.key]?.stage ?: -1) >= 1 }.sumOf { it.reach } / total
    }

    private class Choice(val path: List<String>, val after: Position, val move: String, val share: Double)

    /** The first opponent position from [position] on the main path with two replies or more. */
    private fun branchFrom(book: RepertoireBook, position: Position, path: List<String>): Pair<Position, List<String>>? {
        var at = position
        var moves = path
        repeat(MAX_WALK) {
            val node = book.at(at) ?: return null
            if (!node.own && node.replies.size >= 2) return at to moves
            val next = (if (node.own) node.move else node.replies.singleOrNull()?.uci) ?: return null
            at = RepertoireIndex.play(at, next) ?: return null
            moves = moves + next
        }
        return null
    }

    private fun choicesAt(book: RepertoireBook, position: Position, path: List<String>, band: Int?, weight: Double): List<Choice> {
        val node = book.at(position) ?: return emptyList()
        return node.replies.mapNotNull { reply ->
            val legal = LegalMoveGenerator.generateLegalMoves(position).firstOrNull { it.uci == reply.uci } ?: return@mapNotNull null
            Choice(path + reply.uci, LegalMoveGenerator.makeMove(position, legal), numbered(position, SanFormatter.format(position, legal), path.size), reply.shareFor(band) * weight)
        }
    }

    private fun row(index: RepertoireIndex, choice: Choice, open: RepertoireLevel, states: Map<String, CardState>): Variation {
        val book = index.book
        val keys = cardsUnder(book, choice.after, open)
        return Variation(
            path = choice.path,
            move = choice.move,
            name = nameOf(book, choice.after),
            share = choice.share,
            cards = keys.size,
            introduced = keys.count { it in states },
            solid = keys.count { (states[it]?.stage ?: -1) >= 1 },
        )
    }

    /** Every card of the open levels reachable from [position] in the tree. */
    private fun cardsUnder(book: RepertoireBook, position: Position, open: RepertoireLevel): Set<String> {
        val found = LinkedHashSet<String>()
        val seen = HashSet<String>()
        val queue = ArrayDeque(listOf(position))
        while (queue.isNotEmpty()) {
            val at = queue.removeFirst()
            val node = book.at(at) ?: continue
            if (!seen.add(node.key)) continue
            if (node.own) {
                if (book.isCard(node) && node.level <= open) found += node.key
                node.move?.let { RepertoireIndex.play(at, it) }?.let(queue::add)
            } else {
                node.replies.filter { it.level <= open }.forEach { reply -> RepertoireIndex.play(at, reply.uci)?.let(queue::add) }
            }
        }
        return found
    }

    /**
     * The database's name for the variation: the first name with a variation part ("Italian
     * Game: Two Knights Defense") on its main path, a few moves deep at most.
     */
    private fun nameOf(book: RepertoireBook, position: Position): String? {
        var at = position
        repeat(NAME_DEPTH) {
            val node = book.at(at) ?: return null
            variationName(node.name)?.let { return it }
            val next = (if (node.own) node.move else node.replies.maxByOrNull { it.share }?.uci) ?: return null
            at = RepertoireIndex.play(at, next) ?: return null
        }
        return null
    }

    /** "Two Knights Defense" from "Italian Game: Two Knights Defense"; null for a family's own name. */
    private fun variationName(name: String?): String? = name?.substringAfter(':', "")?.trim()?.takeIf { it.isNotEmpty() }

    private fun numbered(position: Position, san: String, plyIndex: Int): String {
        val number = plyIndex / 2 + 1
        return if (position.sideToMove == PieceColor.WHITE) "$number.$san" else "$number...$san"
    }

    private const val MAX_WALK = 12
    private const val NAME_DEPTH = 4
}
