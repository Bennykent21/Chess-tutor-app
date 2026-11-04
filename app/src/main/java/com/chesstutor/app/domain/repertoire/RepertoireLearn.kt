package com.chesstutor.app.domain.repertoire

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Position
import java.time.Instant

/** A stretch of a line to learn: the moves from the start, and the new cards on it in order. */
data class LearnSegment(val moves: List<String>, val newCards: List<String>)

/**
 * Picks what to learn next (D71, D73): the open card the learner meets most often (highest
 * reach) that is not introduced yet, reached by its likeliest move order, then on down the
 * most common replies until [maxNew] new cards. Its earlier cards have at least its reach, so
 * they were introduced first: a line is learnt from the top.
 */
object LearnPlanner {
    const val MAX_NEW = 5

    fun next(index: RepertoireIndex, open: RepertoireLevel, introduced: Set<String>, maxNew: Int = MAX_NEW): LearnSegment? {
        val book = index.book
        val target = book.cards(open)
            .filter { it.key !in introduced && index.pathTo(it.key) != null }
            .minWithOrNull(compareByDescending<RepertoireNode> { it.reach }.thenBy { it.ply }.thenBy { it.key })
            ?: return null
        val moves = index.pathTo(target.key)!!.toMutableList()
        val fresh = ArrayList<String>()
        // Cards on the way in that are still new (only where reaches tie, as at the start).
        var position = Position.initial()
        for (uci in moves) {
            book.at(position)?.takeIf { it.own && it.key !in introduced && it.level <= open }?.let { fresh += it.key }
            position = RepertoireIndex.play(position, uci)!!
        }
        var node: RepertoireNode = target
        while (fresh.size < maxNew) {
            fresh += node.key
            moves += node.move!!
            position = RepertoireIndex.play(position, node.move)!!
            val opponent = book.at(position) ?: break
            val reply = opponent.replies.filter { it.level <= open }.maxByOrNull { it.share } ?: break
            val after = RepertoireIndex.play(position, reply.uci)!!
            val card = book.at(after)?.takeIf { it.own && it.key !in introduced && it.level <= open } ?: break
            moves += reply.uci
            position = after
            node = card
        }
        return LearnSegment(moves, fresh.distinct())
    }
}

/** What happened on the learner's move in a learn run. */
enum class LearnMoveResult {
    /** First time at a new card: the guess was the repertoire move. */
    GUESSED,

    /** First time at a new card: another move. The move is shown with its note; no penalty. */
    REVEALED,

    /** The repertoire move. */
    RIGHT,

    /** Just as good, not the repertoire's move: try again, no penalty. */
    ALSO,

    /** The answer is shown; on a new card in the recall pass this sets it back to Learning. */
    WRONG,
}

/**
 * One learn run over a [LearnSegment] (D71): first the guess pass (at each new card the
 * learner tries a move, then sees the repertoire move and its note: guessing first helps it
 * stick, and a wrong guess costs nothing), then the recall pass from the start, the first
 * real recall, which sets each new card's schedule. Cards met on the way that were already
 * known are practice and change nothing. Pure, so it is unit tested; the screen plays the
 * opponent's replies with [playOpponent] after a pause.
 */
class LearnRun(val index: RepertoireIndex, val segment: LearnSegment, private val now: () -> Instant = Instant::now) {
    enum class Pass { GUESS, RECALL, DONE }

    var pass: Pass = Pass.GUESS
        private set
    var ply: Int = 0
        private set
    var position: Position = Position.initial()
        private set

    /** The repertoire move after a reveal or a wrong move, until it is played. */
    var shown: String? = null
        private set
    private var hinted = false
    private val missed = HashSet<String>()
    private val results = LinkedHashMap<String, Pair<CardState, CardReview>>()

    val book: RepertoireBook get() = index.book
    val node: RepertoireNode? get() = book.at(position)
    val isOwnTurn: Boolean get() = pass != Pass.DONE && ply < segment.moves.size && position.sideToMove == book.side
    val isNewCard: Boolean get() = isOwnTurn && node?.key in segment.newCards

    /** The cards to save and the reviews to log, once the recall pass is over. */
    val outcome: List<Pair<CardState, CardReview>> get() = if (pass == Pass.DONE) results.values.toList() else emptyList()

    fun submit(uci: String): LearnMoveResult? {
        if (!isOwnTurn || LegalMoveGenerator.generateLegalMoves(position).none { it.uci == uci }) return null
        val card = node ?: return null
        val expected = segment.moves[ply]
        val answer = RepertoireCards.judge(card, uci)
        if (pass == Pass.GUESS && isNewCard) {
            advance(expected)
            return if (answer == CardAnswer.RIGHT) LearnMoveResult.GUESSED else LearnMoveResult.REVEALED.also { shown = expected }
        }
        return when (answer) {
            CardAnswer.RIGHT -> {
                if (pass == Pass.RECALL && isNewCard) record(card, right = card.key !in missed)
                advance(expected)
                LearnMoveResult.RIGHT
            }
            CardAnswer.ALSO -> LearnMoveResult.ALSO
            CardAnswer.WRONG -> {
                if (pass == Pass.RECALL && isNewCard) missed += card.key
                shown = expected
                LearnMoveResult.WRONG
            }
        }
    }

    /** "Show me" in the recall pass: the move appears and a new card counts as missed. */
    fun showAnswer(): String? {
        if (!isOwnTurn) return null
        if (pass == Pass.RECALL && isNewCard) missed += node!!.key
        return segment.moves[ply].also { shown = it }
    }

    /** A hint (the piece to move): a right first try then keeps the card at a day. */
    fun hint(): String? {
        if (!isOwnTurn) return null
        if (pass == Pass.RECALL && isNewCard) hinted = true
        return segment.moves[ply].substring(0, 2)
    }

    /** Plays the opponent's move. False when it is not their turn. */
    fun playOpponent(): Boolean {
        if (pass == Pass.DONE || ply >= segment.moves.size || position.sideToMove == book.side) return false
        advance(segment.moves[ply])
        return true
    }

    private fun record(card: RepertoireNode, right: Boolean) {
        val fresh = RepertoireCards.introduce(book.id, card, now())
        results[card.key] = RepertoireCards.afterRecall(fresh, right, hinted, now())
        hinted = false
    }

    private fun advance(uci: String) {
        position = RepertoireIndex.play(position, uci)!!
        ply++
        shown = null
        if (ply >= segment.moves.size) {
            if (pass == Pass.GUESS) {
                pass = Pass.RECALL
                ply = 0
                position = Position.initial()
            } else {
                pass = Pass.DONE
            }
        }
    }
}
