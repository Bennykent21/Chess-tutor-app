package com.chesstutor.app.domain.repertoire

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Position
import java.time.Instant

/**
 * A card due for review, with what the board needs: its position and the moves that led
 * there. [fromStart] cards (tricky ones, D76) are played from the first move of their line.
 */
data class DueCard(
    val index: RepertoireIndex,
    val node: RepertoireNode,
    val state: CardState,
    val path: List<String>,
    val fromStart: Boolean = false,
) {
    val book: RepertoireBook get() = index.book
    val position: Position get() = index.positionOf(node.key)

    /** The opponent's move that led here, which the card names ("what do you play against 3...Nf6?"). */
    val lastMove: String? get() = path.lastOrNull()
}

/**
 * The day's review (D73): due cards from every opening the learner has started, oldest due
 * first, mixed so that two cards from one opening do not follow each other while another
 * opening still has one waiting. Tricky cards (D76) come from the start of their line.
 */
object ReviewQueue {
    const val DEFAULT_LIMIT = 30

    fun build(indexes: List<RepertoireIndex>, states: Collection<CardState>, now: Instant, limit: Int = DEFAULT_LIMIT): List<DueCard> =
        mixed(cards(indexes, states.filter { it.isDue(now) }.sortedWith(compareBy({ it.dueAt }, { it.id }))), limit)

    /** The "Tricky lines" run: every tricky card, the most often missed first, each from the start. */
    fun tricky(indexes: List<RepertoireIndex>, states: Collection<CardState>, limit: Int = DEFAULT_LIMIT): List<DueCard> =
        cards(indexes, states.filter { it.tricky }.sortedWith(compareByDescending<CardState> { it.lapses }.thenBy { it.dueAt }))
            .take(limit)

    private fun cards(indexes: List<RepertoireIndex>, states: List<CardState>): List<DueCard> {
        val byBook = indexes.associateBy { it.book.id }
        return states.mapNotNull { state ->
            val index = byBook[state.bookId] ?: return@mapNotNull null
            val node = index.book.nodes[state.key]?.takeIf { index.book.isCard(it) } ?: return@mapNotNull null
            val path = index.pathTo(state.key) ?: return@mapNotNull null
            DueCard(index, node, state, path, fromStart = state.tricky)
        }
    }

    private fun mixed(due: List<DueCard>, limit: Int): List<DueCard> {
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
 * One review run over [cards] (D71): one scored move per card, and only the first try
 * counts. An accepted alternative asks again with no penalty; a hint keeps a right answer at
 * a day; a wrong move or "Show me" sends the card back to Learning, due now. A [DueCard.fromStart]
 * card first walks its line: the learner's earlier moves are practice (a slip is shown and
 * costs nothing) and the opponent's come with [playOpponent]. Pure, so it is unit tested; the
 * screen saves [outcome].
 */
class ReviewSession(
    cards: List<DueCard>,
    /**
     * Drills and challenges (D101, D102): a first miss is not shown, only noted, and the card comes
     * back at a random later point; from the second miss the move is shown. After a miss a card
     * must come right twice (a first pass, then once more) before it leaves the run.
     */
    private val repeatMisses: Boolean = false,
    private val random: kotlin.random.Random = kotlin.random.Random.Default,
    private val now: () -> Instant = Instant::now,
) {
    private class Track {
        var misses = 0
        var confirming = false
    }

    private val tracks = HashMap<String, Track>()

    /** The card just finished comes back later in the run: missed, or a first pass after a miss. */
    var returning: Boolean = false
        private set

    private val queue = cards.toMutableList()

    /** The run's cards, a missed one again at the end when [repeatMisses]. */
    val cards: List<DueCard> get() = queue

    /** How many different positions the run asks, however often a missed one comes back. */
    val positions: Int = cards.map { it.state.id }.distinct().size

    /** Positions answered right the first time they were asked. */
    val rightFirstTry: Int get() = results.groupBy { it.first.id }.values.count { it.first().second.firstTryRight }

    var index: Int = 0
        private set

    /** Solved, or its answer shown: the learner moves on with [next]. */
    var done: Boolean = false
        private set
    var shown: String? = null
        private set

    /** Where the board is for the current card. */
    var position: Position = Position.initial()
        private set

    /** How many moves of the current card's path have been played. */
    var ply: Int = 0
        private set
    private var hinted = false
    private var missed = false
    private val results = ArrayList<Pair<CardState, CardReview>>()

    init {
        startCard()
    }

    val current: DueCard? get() = queue.getOrNull(index)
    val finished: Boolean get() = index >= queue.size
    val outcome: List<Pair<CardState, CardReview>> get() = results.toList()
    val rightFirstTime: Int get() = results.count { it.second.firstTryRight }

    /** True at the card itself; before it, a fromStart card is still walking its line. */
    val atCard: Boolean get() = current?.let { ply >= it.path.size } ?: false

    val isOwnTurn: Boolean get() = !done && current?.let { position.sideToMove == it.book.side } ?: false

    /** The move the learner should play now. */
    private val expected: String? get() = current?.let { if (atCard) it.node.move else it.path.getOrNull(ply) }

    fun submit(uci: String): CardAnswer? {
        val card = current ?: return null
        if (done || !isOwnTurn || LegalMoveGenerator.generateLegalMoves(position).none { it.uci == uci }) return null
        if (!atCard) {
            val node = card.book.at(position) ?: return null
            return when (val answer = if (uci == expected) CardAnswer.RIGHT else RepertoireCards.judge(node, uci)) {
                CardAnswer.RIGHT -> answer.also { advance(expected!!) }
                CardAnswer.ALSO -> answer
                CardAnswer.WRONG -> answer.also { shown = expected }
            }
        }
        return when (val answer = RepertoireCards.judge(card.node, uci)) {
            CardAnswer.ALSO -> answer
            CardAnswer.RIGHT -> answer.also { finish(card, right = !missed) }
            CardAnswer.WRONG -> answer.also {
                val track = if (repeatMisses) tracks.getOrPut(card.state.id, ::Track).also { it.misses++ } else null
                if (track != null && track.misses == 1) {
                    // Noted, not shown: the learner moves on and meets it again later.
                    finish(card, right = false)
                } else {
                    missed = true
                    shown = card.node.move
                }
            }
        }
    }

    /** Plays the opponent's move on the way to a fromStart card. False when it is not their turn. */
    fun playOpponent(): Boolean {
        val card = current ?: return false
        if (done || atCard || isOwnTurn) return false
        advance(card.path[ply])
        return true
    }

    fun hint(): String? {
        if (done || !isOwnTurn) return null
        if (atCard) hinted = true
        return expected?.substring(0, 2)
    }

    /** "Show me": on the way to the card it only shows the move; at the card it counts as missed. */
    fun showAnswer(): String? {
        val card = current ?: return null
        if (done || !isOwnTurn) return null
        shown = expected
        if (atCard) {
            missed = true
            if (repeatMisses) tracks.getOrPut(card.state.id, ::Track).misses++
            finish(card, right = false)
        }
        return shown
    }

    fun next() {
        if (!done) return
        index++
        startCard()
    }

    private fun startCard() {
        done = false
        shown = null
        hinted = false
        missed = false
        val card = current ?: return
        if (card.fromStart) {
            // The opening's defining moves are already on the board.
            ply = card.book.startPlies(card.path)
            position = card.path.take(ply).fold(Position.initial()) { p, uci -> RepertoireIndex.play(p, uci)!! }
        } else {
            position = card.position
            ply = card.path.size
        }
    }

    private fun advance(uci: String) {
        position = RepertoireIndex.play(position, uci)!!
        ply++
        shown = null
    }

    private fun finish(card: DueCard, right: Boolean) {
        results += RepertoireCards.afterRecall(card.state, right, hinted, now())
        returning = false
        if (repeatMisses) {
            val track = tracks.getOrPut(card.state.id, ::Track)
            if (!right) {
                track.confirming = false
                comeBack(card)
            } else if (track.misses > 0 && !track.confirming) {
                track.confirming = true
                comeBack(card)
            }
        }
        done = true
    }

    /** Puts [card] back at a random point later in the run, not straight after itself when there is room. */
    private fun comeBack(card: DueCard) {
        returning = true
        val from = minOf(index + 2, queue.size)
        queue.add(random.nextInt(from, queue.size + 1), card.copy(fromStart = false))
    }
}

/**
 * Practice from memory, any time (D101): no teaching, nothing scheduled. A drill walks one
 * variation's learnt positions in the order of its lines; a challenge mixes learnt positions from
 * every variation of an opening, more of them the higher the learner's cup.
 */
object Drills {
    /** Lines with something learnt before the challenge opens. */
    const val CHALLENGE_LINES = 2

    /** Positions per challenge at each level: Core, Standard, Deep. */
    const val CHALLENGE_STEP = 10

    fun challengeSize(level: RepertoireLevel, learnt: Int, linesLearnt: Int): Int =
        if (linesLearnt < CHALLENGE_LINES) 0 else minOf(learnt, CHALLENGE_STEP * (level.ordinal + 1))

    /** The learnt positions of the variation that starts with [path], in the order of its lines. */
    fun variation(index: RepertoireIndex, path: List<String>, states: Collection<CardState>, now: Instant): List<DueCard> =
        learnt(index, states, now).filter { it.path.take(path.size) == path }.sortedBy { it.path.joinToString(" ") }

    /** [size] learnt positions of the opening, mixed. */
    fun challenge(index: RepertoireIndex, states: Collection<CardState>, size: Int, now: Instant, random: kotlin.random.Random = kotlin.random.Random.Default): List<DueCard> =
        learnt(index, states, now).shuffled(random).take(size)

    private fun learnt(index: RepertoireIndex, states: Collection<CardState>, now: Instant): List<DueCard> {
        val mine = states.filter { it.bookId == index.book.id }.associateBy { it.key }
        return index.book.cards().mapNotNull { node ->
            val state = mine[node.key] ?: return@mapNotNull null
            val path = index.pathTo(node.key) ?: return@mapNotNull null
            DueCard(index, node, state, path)
        }
    }
}
