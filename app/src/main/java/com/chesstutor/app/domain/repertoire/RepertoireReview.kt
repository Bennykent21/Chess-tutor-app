package com.chesstutor.app.domain.repertoire

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Position
import java.time.Instant

/** A card due for review, with what the board needs: its position and the moves that led there. */
data class DueCard(val index: RepertoireIndex, val node: RepertoireNode, val state: CardState, val path: List<String>) {
    val book: RepertoireBook get() = index.book
    val position: Position get() = index.positionOf(node.key)

    /** The opponent's move that led here, which the card names ("what do you play against 3...Nf6?"). */
    val lastMove: String? get() = path.lastOrNull()
}

/**
 * The day's review (D73): due cards from every opening the learner has started, oldest due
 * first, mixed so that two cards from one opening do not follow each other while another
 * opening still has one waiting.
 */
object ReviewQueue {
    const val DEFAULT_LIMIT = 30

    fun build(indexes: List<RepertoireIndex>, states: Collection<CardState>, now: Instant, limit: Int = DEFAULT_LIMIT): List<DueCard> {
        val byBook = indexes.associateBy { it.book.id }
        val due = states.filter { it.isDue(now) }.sortedWith(compareBy({ it.dueAt }, { it.id })).mapNotNull { state ->
            val index = byBook[state.bookId] ?: return@mapNotNull null
            val node = index.book.nodes[state.key]?.takeIf { it.own } ?: return@mapNotNull null
            val path = index.pathTo(state.key) ?: return@mapNotNull null
            DueCard(index, node, state, path)
        }
        val waiting = due.toMutableList()
        val out = ArrayList<DueCard>()
        while (waiting.isNotEmpty() && out.size < limit) {
            val last = out.lastOrNull()?.book?.id
            val pick = waiting.firstOrNull { it.book.id != last } ?: waiting.first()
            waiting.remove(pick)
            out += pick
        }
        return out
    }
}

/**
 * One review run over [cards] (D71): one move per card, and only the first try counts. An
 * accepted alternative asks again with no penalty; a hint keeps a right answer at a day; a
 * wrong move or "Show me" sends the card back to Learning, due now. Pure, so it is unit
 * tested; the screen saves [outcome].
 */
class ReviewSession(val cards: List<DueCard>, private val now: () -> Instant = Instant::now) {
    var index: Int = 0
        private set

    /** Solved, or its answer shown: the learner moves on with [next]. */
    var done: Boolean = false
        private set
    var shown: String? = null
        private set
    private var hinted = false
    private var missed = false
    private val results = ArrayList<Pair<CardState, CardReview>>()

    val current: DueCard? get() = cards.getOrNull(index)
    val finished: Boolean get() = index >= cards.size
    val outcome: List<Pair<CardState, CardReview>> get() = results.toList()
    val rightFirstTime: Int get() = results.count { it.second.firstTryRight }

    fun submit(uci: String): CardAnswer? {
        val card = current ?: return null
        if (done || LegalMoveGenerator.generateLegalMoves(card.position).none { it.uci == uci }) return null
        return when (val answer = RepertoireCards.judge(card.node, uci)) {
            CardAnswer.ALSO -> answer
            CardAnswer.RIGHT -> answer.also { finish(card, right = !missed) }
            CardAnswer.WRONG -> answer.also {
                missed = true
                shown = card.node.move
            }
        }
    }

    fun hint(): String? {
        val card = current ?: return null
        if (done) return null
        hinted = true
        return card.node.move!!.substring(0, 2)
    }

    fun showAnswer(): String? {
        val card = current ?: return null
        if (done) return null
        missed = true
        shown = card.node.move
        finish(card, right = false)
        return shown
    }

    fun next() {
        if (!done) return
        index++
        done = false
        shown = null
        hinted = false
        missed = false
    }

    private fun finish(card: DueCard, right: Boolean) {
        results += RepertoireCards.afterRecall(card.state, right, hinted, now())
        done = true
    }
}
