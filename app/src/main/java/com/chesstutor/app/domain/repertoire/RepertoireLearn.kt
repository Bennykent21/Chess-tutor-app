package com.chesstutor.app.domain.repertoire

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Position
import java.time.Instant

/**
 * A line to learn: the moves from the start, the new cards on it in order, and the opening
 * database's name for the deepest named position on it ("Italian Game: Giuoco Pianissimo").
 */
data class LearnSegment(val moves: List<String>, val newCards: List<String>, val name: String? = null) {
    /** The variation's own words ("Giuoco Pianissimo"), or the whole name for a family's line. */
    val variation: String? get() = name?.substringAfter(':', name)?.trim()?.ifEmpty { null }
}

/**
 * Picks what to learn next (D71, D73, D77): the open card the learner meets most often (highest
 * reach) that is not introduced yet, reached by its likeliest move order, then on down the
 * most common replies to the end of the line, at most [maxNew] new cards. Its earlier cards have
 * at least its reach, so they were introduced first: a line is learnt from the top. [within]
 * keeps it inside one variation: only cards whose way in starts with those moves.
 */
object LearnPlanner {
    const val MAX_NEW = 8

    fun next(
        index: RepertoireIndex,
        open: RepertoireLevel,
        introduced: Set<String>,
        maxNew: Int = MAX_NEW,
        within: List<String>? = null,
    ): LearnSegment? {
        val book = index.book
        val target = book.cards(open)
            .filter { it.key !in introduced && index.pathTo(it.key)?.let { path -> within == null || path.take(within.size) == within } == true }
            .minWithOrNull(compareByDescending<RepertoireNode> { it.reach }.thenBy { it.ply }.thenBy { it.key })
            ?: return null
        val moves = index.pathTo(target.key)!!.toMutableList()
        val fresh = ArrayList<String>()
        // Cards on the way in that are still new (only where reaches tie, as at the start).
        var position = Position.initial()
        var name: String? = null
        for (uci in moves) {
            book.at(position)?.takeIf { book.isCard(it) && it.key !in introduced && it.level <= open }?.let { fresh += it.key }
            position = RepertoireIndex.play(position, uci)!!
            book.at(position)?.name?.let { name = it }
        }
        var node: RepertoireNode = target
        while (fresh.size < maxNew) {
            fresh += node.key
            moves += node.move!!
            position = RepertoireIndex.play(position, node.move)!!
            val opponent = book.at(position) ?: break
            opponent.name?.let { name = it }
            val reply = opponent.replies.filter { it.level <= open }.maxByOrNull { it.share } ?: break
            val after = RepertoireIndex.play(position, reply.uci)!!
            val card = book.at(after)?.takeIf { book.isCard(it) && it.key !in introduced && it.level <= open } ?: break
            moves += reply.uci
            position = after
            card.name?.let { name = it }
            node = card
        }
        return LearnSegment(moves, fresh.distinct(), name)
    }
}

/** What happened on the learner's move in a learn run. */
enum class LearnMoveResult {
    /** First time at a new card: the first guess was the repertoire move. */
    GUESSED,

    /**
     * First time at a new card: another move. It is not played: the repertoire move is shown
     * and the learner plays it (no penalty in the guess pass), then its note follows.
     */
    REVEALED,

    /** The repertoire move. */
    RIGHT,

    /** Just as good, not the repertoire's move: the repertoire move is shown, try again, no penalty. */
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

    /** The opening's defining moves are on the board from the start of each pass. */
    val startPly = index.book.startPlies(segment.moves)
    private val startPosition = segment.moves.take(startPly).fold(Position.initial()) { p, uci -> RepertoireIndex.play(p, uci)!! }

    var pass: Pass = Pass.GUESS
        private set
    var ply: Int = startPly
        private set
    var position: Position = startPosition
        private set

    /** The repertoire move after a reveal or a wrong move, until it is played. */
    var shown: String? = null
        private set
    private var hinted = false

    /** The ply where the guess pass showed the answer after a wrong guess. */
    private var revealedAt: Int? = null
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
            if (answer == CardAnswer.RIGHT) {
                val firstTry = revealedAt != ply
                advance(expected)
                return if (firstTry) LearnMoveResult.GUESSED else LearnMoveResult.RIGHT
            }
            // Not played: the right move is shown, and the learner plays it.
            revealedAt = ply
            shown = expected
            return if (answer == CardAnswer.ALSO) LearnMoveResult.ALSO else LearnMoveResult.REVEALED
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
        revealedAt = null
        if (ply >= segment.moves.size) {
            if (pass == Pass.GUESS) {
                pass = Pass.RECALL
                ply = startPly
                position = startPosition
            } else {
                pass = Pass.DONE
            }
        }
    }
}
