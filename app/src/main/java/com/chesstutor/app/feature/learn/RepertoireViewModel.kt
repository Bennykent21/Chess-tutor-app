package com.chesstutor.app.feature.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.MoveEffects
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.SanFormatter
import com.chesstutor.app.domain.repertoire.CardAnswer
import com.chesstutor.app.domain.repertoire.CardReview
import com.chesstutor.app.domain.repertoire.CardState
import com.chesstutor.app.domain.repertoire.DueCard
import com.chesstutor.app.domain.repertoire.LearnMoveResult
import com.chesstutor.app.domain.repertoire.LearnPlanner
import com.chesstutor.app.domain.repertoire.LearnRun
import com.chesstutor.app.domain.repertoire.LearnSegment
import com.chesstutor.app.domain.repertoire.LevelProgress
import com.chesstutor.app.domain.repertoire.LevelUnlocks
import com.chesstutor.app.domain.repertoire.Mastery
import com.chesstutor.app.domain.repertoire.MasteryProgress
import com.chesstutor.app.domain.repertoire.RepertoireBook
import com.chesstutor.app.domain.repertoire.RepertoireCards
import com.chesstutor.app.domain.repertoire.RepertoireIndex
import com.chesstutor.app.domain.repertoire.Variation
import com.chesstutor.app.domain.repertoire.RepertoireVariations
import com.chesstutor.app.domain.repertoire.RepertoireLevel
import com.chesstutor.app.domain.repertoire.RepertoireNote
import com.chesstutor.app.domain.repertoire.RepertoireProgressRepository
import com.chesstutor.app.domain.repertoire.ReviewQueue
import com.chesstutor.app.domain.repertoire.ReviewSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.chesstutor.app.domain.learn.CourseGate
import com.chesstutor.app.domain.learn.OpenLines
import com.chesstutor.app.domain.learn.OpeningTests
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.random.Random
import java.time.ZoneId

/** One taught opening on its page (Phase 10): its levels, what is due and the replies it meets first. */
data class RepertoireBookUi(
    val book: RepertoireBook,
    val index: RepertoireIndex,
    val levels: LevelProgress,
    val mastery: Map<RepertoireLevel, MasteryProgress>,
    val due: Int,
    val tricky: Int,
    /** New cards waiting in the open levels. */
    val fresh: Int,
    val chart: ReplyChart?,
    /** Cards met so far, of all the opening's cards. */
    val introduced: Int = 0,
    val total: Int = 0,
    /** The opponent's choices, each with its share and the learner's progress in it (D77). */
    val variations: List<Variation> = emptyList(),
    /** How much of what the learner meets in the open levels is solid, 0 to 1. */
    val readiness: Double = 0.0,
    /** The variation "New line" teaches next, when it has a name. */
    val nextLine: String? = null,
    /** The variations open for drilling (Phase 12); null when every one is. */
    val open: OpenLines? = null,
) {
    /** No variation is open yet: take the course, or the test. */
    val locked: Boolean get() = open != null && open.open.isEmpty() && !open.rest

    /** The opening database's name where the start line ends, for Opening Battle ("Caro-Kann Defense"). */
    val battleLine: String
        get() = book.start.fold(com.chesstutor.app.core.Position.initial()) { p, uci -> RepertoireIndex.play(p, uci) ?: p }
            .let { book.at(it)?.name } ?: book.title
}

data class RepertoireState(
    val loading: Boolean = true,
    val books: List<RepertoireBookUi> = emptyList(),
    /** Up to three due positions, in the order the review will ask them, and the side each is seen from. */
    val dueSamples: List<Pair<String, PieceColor>> = emptyList(),
) {
    val dueTotal: Int get() = books.sumOf { it.due }

    fun book(id: String): RepertoireBookUi? = books.firstOrNull { it.book.id == id }
}

/** How the opponent answers at the first branch of the tree ("After 3.Bc4"), at the learner's level. */
data class ReplyChart(val after: String, val replies: List<ChartReply>)

data class ChartReply(val san: String, val share: Double, val error: Boolean)

/** TEST is an opening's test-out (Phase 12): no hints, nothing saved, four of five right opens its drills. */
enum class RunKind { LEARN, REVIEW, TRICKY, TEST }

/** What the trainer is saying at this moment of a run. */
sealed interface RunMoment {
    /** The learner's turn: the zone asks "what do you play against ...?". */
    data object YourMove : RunMoment


    /** The play-back pass of a learn run starts from the top: the learner's turn. */
    data object FromTheTop : RunMoment

    /**
     * The watch pass: the line plays itself. [arrow] is the learner's next move, drawn just before
     * it is played, and [words] the first sentence of its note; both null while the opponent moves.
     */
    data class Watch(val arrow: String?, val words: String?) : RunMoment

    /**
     * Just as good, but not this repertoire's move. The first time a move is met it is named and
     * drawn ([san], [uci]); in a recall or review it stays hidden (null), so the card is still recalled.
     */
    data class Also(val san: String?, val uci: String?) : RunMoment

    /** The move to play is drawn on the board (after a wrong move or "Show me"). */
    data class Shown(val san: String, val uci: String) : RunMoment

    /** A review card is over: [played] is the right move now on the board, [shown] the answer drawn after "Show me". The next card follows by itself. */
    data class CardDone(val right: Boolean, val dueAt: Instant, val played: String?, val shown: String?) : RunMoment

    data class Finished(val right: Int, val total: Int) : RunMoment
}

/** The run on the board, for the screen. */
data class RunUi(
    val kind: RunKind,
    val title: String,
    val side: PieceColor,
    val board: RepertoireBoard,
    val moment: RunMoment,
    /** The opponent's last move, numbered ("3...Nf6"), when it is the learner's turn after one. */
    val against: String?,
    val step: Int,
    val steps: Int,
    val tricky: Boolean,
    /** The square of the piece to move, after a hint. */
    val hintFrom: String? = null,
    /** A level just mastered by this run: the cup sheet shows. */
    val mastered: RepertoireLevel? = null,
    /** The opening whose level was mastered, and how many cards the level it opens holds. */
    val masteredBook: String? = null,
    val nextLevelCards: Int = 0,
    val moreToLearn: Boolean = false,
    /** A ring on the square just played: green when right, red when the move slid back. */
    val ringSquare: String? = null,
    val ringRight: Boolean = true,
    /** The note on the learner's last move, when they asked "Why". */
    val why: String? = null,
    /** True when the learner's last move has a note to show. */
    val canWhy: Boolean = false,
)

/**
 * The repertoire trainer (Phase 10, D68 to D76): the taught openings from the bundled trees,
 * each one's progress, and the learn and review runs on the board. Shared by the trainer list,
 * the opening page and the run screen (it lives as long as the app).
 */
class RepertoireViewModel(
    private val repository: RepertoireProgressRepository,
    private val loadBooks: suspend () -> List<RepertoireBook>,
    private val rating: () -> Int? = { null },
    private val clock: () -> Instant = Instant::now,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val replyDelayMs: Long = REPLY_DELAY_MS,
    private val watchArrowMs: Long = WATCH_ARROW_MS,
    private val watchMoveMs: Long = WATCH_MOVE_MS,
    /** Per opening, the variations its finished courses opened; an opening missing here is fully open. */
    private val gate: Flow<Map<String, OpenLines>> = flowOf(emptyMap()),
    /** Where a passed test is kept. */
    private val tests: OpeningTests? = null,
) : ViewModel() {
    private val indexes = MutableStateFlow<List<RepertoireIndex>?>(null)
    private val reviewLog = MutableStateFlow<Map<String, List<CardReview>>>(emptyMap())
    private val cards = repository.observeCards().stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    private val levels = repository.observeLevels().stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val gates = gate.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val state: StateFlow<RepertoireState> = combine(indexes, cards, levels, reviewLog, gates) { idx, cardMap, levelMap, log, open ->
        if (idx == null) return@combine RepertoireState(loading = true)
        RepertoireState(
            loading = false,
            books = idx.map { bookUi(it, cardMap, levelMap[it.book.id], log[it.book.id].orEmpty(), open[it.book.id]) },
            dueSamples = ReviewQueue.build(idx, cardMap.values, clock()).take(3).map { it.position.toFen() to it.book.side },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RepertoireState())

    private val mutableRun = MutableStateFlow<RunUi?>(null)
    val run: StateFlow<RunUi?> = mutableRun.asStateFlow()

    private var job: Job? = null
    private var learn: LearnRun? = null
    private var review: ReviewSession? = null
    private var moment: RunMoment = RunMoment.YourMove
    private var frozen: List<String>? = null
    private var hintFrom: String? = null
    private var mastered: RepertoireLevel? = null
    private var moreToLearn = false
    private var ring: Pair<String, Boolean>? = null
    private var lastNote: RepertoireNote? = null
    private var whyOpen = false

    init {
        viewModelScope.launch {
            val books = runCatching { loadBooks() }.getOrDefault(emptyList())
            reviewLog.value = books.associate { it.id to repository.reviews(it.id) }
            indexes.value = books.map(::RepertoireIndex)
        }
    }

    private fun band(book: RepertoireBook): Int? = book.bandFor(rating())

    private fun bookUi(index: RepertoireIndex, cardMap: Map<String, CardState>, level: LevelProgress?, log: List<CardReview>, open: OpenLines?): RepertoireBookUi {
        val book = index.book
        val progress = level ?: LevelProgress(book.id)
        val mine = cardMap.values.filter { it.bookId == book.id }
        val now = clock()
        val introduced = mine.map { it.key }.toSet()
        return RepertoireBookUi(
            book = book,
            index = index,
            levels = progress,
            mastery = RepertoireLevel.entries.associateWith { Mastery.of(book, it, cardMap, log, zone) },
            due = mine.count { it.isDue(now) && book.nodes[it.key]?.own == true },
            tricky = mine.count { it.tricky },
            fresh = book.cards(progress.unlocked).count { it.key !in introduced && CourseGate.isOpen(index.pathTo(it.key).orEmpty(), open) },
            chart = chartOf(index, band(book)),
            introduced = book.cards().count { it.key in introduced },
            total = book.cards().size,
            variations = RepertoireVariations.of(index, band(book), progress.unlocked, mine.associateBy { it.key }, open),
            readiness = RepertoireVariations.readiness(book, progress.unlocked, mine.associateBy { it.key }),
            nextLine = LearnPlanner.next(index, progress.unlocked, introduced, allowed = open)?.variation,
            open = open,
        )
    }

    // ------------------------------------------------------------------ learn

    /**
     * The next line of [bookId] to learn, inside the variation that starts with [within] when given
     * (a row of the opening page), else wherever the learner meets new moves most often.
     */
    fun startLearn(bookId: String, within: List<String>? = null) {
        val index = indexes.value?.firstOrNull { it.book.id == bookId } ?: return
        val open = levels.value[bookId]?.unlocked ?: RepertoireLevel.CORE
        val introduced = cards.value.values.filter { it.bookId == bookId }.map { it.key }.toSet()
        val allowed = gates.value[bookId]
        val segment = within?.let { LearnPlanner.next(index, open, introduced, within = it, allowed = allowed) }
            ?: LearnPlanner.next(index, open, introduced, allowed = allowed) ?: return
        startSegment(index, segment)
        learnWithin = within
    }

    /** The variation the learner chose on the opening page, so "Learn more" stays in it. */
    private var learnWithin: List<String>? = null

    private fun startSegment(index: RepertoireIndex, segment: LearnSegment) {
        clear()
        val run = LearnRun(index, segment, clock)
        learn = run
        val board = RepertoireBoard(
            line = { (frozen ?: segment.moves.take(run.ply)) to (run.isOwnTurn && moment.lets()) },
            submit = ::submitLearn,
        )
        val name = segment.variation ?: index.book.title
        moment = RunMoment.Watch(null, null)
        mutableRun.value = RunUi(RunKind.LEARN, name, index.book.side, board, moment, null, 0, segment.newCards.size, tricky = false)
        follow()
    }

    private fun submitLearn(move: Move): BoardFeedback {
        val run = learn ?: return MoveEffects.ILLEGAL
        val before = run.position
        val line = run.segment.moves.take(run.ply)
        val expected = run.segment.moves.getOrNull(run.ply) ?: return MoveEffects.ILLEGAL
        val result = run.submit(move.uci) ?: return MoveEffects.ILLEGAL
        hintFrom = null
        whyOpen = false
        val san = numbered(before, legal(before, expected), line.size)
        return when (result) {
            LearnMoveResult.RIGHT -> {
                val after = LegalMoveGenerator.makeMove(before, move)
                ring = move.to.algebraic to true
                lastNote = run.book.at(after)?.note
                moment = RunMoment.YourMove
                follow()
                if (run.pass == LearnRun.Pass.DONE) MoveEffects.CORRECT else MoveEffects.feedbackFor(before, move, after)
            }
            LearnMoveResult.ALSO -> {
                // The line was watched, so it stays to be recalled: the move is not named.
                moment = RunMoment.Also(null, null)
                publish()
                MoveEffects.ILLEGAL
            }
            LearnMoveResult.WRONG -> {
                // The wrong move slides back and the right one is drawn; the learner plays it.
                ring = move.to.algebraic to false
                moment = RunMoment.Shown(san, expected)
                publish()
                MoveEffects.WRONG
            }
        }
    }

    /**
     * Moves the run on by itself: in the watch pass every move, the learner's own with an arrow
     * first; then, after a pause, the play-back pass from the top; in a play-back or a review, the
     * opponent's replies, until it is the learner's turn.
     */
    private fun follow() {
        job?.cancel()
        job = viewModelScope.launch {
            val run = learn
            val session = review
            if (run != null) {
                while (run.pass == LearnRun.Pass.WATCH) {
                    val uci = run.nextWatched ?: break
                    if (run.nextIsOwn) {
                        val after = RepertoireIndex.play(run.position, uci)
                        val note = after?.let { run.book.at(it)?.note }
                        moment = RunMoment.Watch(uci, note?.text?.let(::firstSentence))
                        publish()
                        delay(watchArrowMs)
                    } else {
                        moment = (moment as? RunMoment.Watch)?.copy(arrow = null) ?: RunMoment.Watch(null, null)
                        publish()
                        delay(replyDelayMs)
                    }
                    run.watchNext()
                    if (run.pass == LearnRun.Pass.WATCH) {
                        moment = (moment as? RunMoment.Watch)?.copy(arrow = null) ?: moment
                        mutableRun.value?.board?.refresh(animate = true)
                        publish()
                        delay(watchMoveMs)
                    } else {
                        // The last move stays on the board a moment, then the line starts again for the learner.
                        frozen = run.segment.moves
                        mutableRun.value?.board?.refresh(animate = true)
                        publish()
                        delay(PASS_PAUSE_MS)
                        frozen = null
                        moment = RunMoment.FromTheTop
                        mutableRun.value?.board?.refresh(animate = false)
                    }
                }
                while (run.pass == LearnRun.Pass.RECALL && !run.isOwnTurn) {
                    publish()
                    delay(replyDelayMs)
                    ring = null
                    run.playOpponent()
                    mutableRun.value?.board?.refresh(animate = true)
                }
                if (run.pass == LearnRun.Pass.DONE) finishLearn(run)
            } else if (session != null) {
                while (!session.done && !session.finished && !session.isOwnTurn && !session.atCard) {
                    publish()
                    delay(replyDelayMs)
                    ring = null
                    session.playOpponent()
                    mutableRun.value?.board?.refresh(animate = true)
                }
            }
            publish()
        }
    }

    /** Watches the line again from the top. */
    fun watchAgain() {
        val run = learn ?: return
        if (run.pass != LearnRun.Pass.WATCH) return
        run.watchAgain()
        moment = RunMoment.Watch(null, null)
        mutableRun.value?.board?.refresh(animate = false)
        follow()
    }

    /** Skips the rest of the watch: the learner plays the line from the top now. */
    fun skipWatch() {
        val run = learn ?: return
        if (run.pass != LearnRun.Pass.WATCH) return
        run.skipWatch()
        frozen = null
        moment = RunMoment.FromTheTop
        mutableRun.value?.board?.refresh(animate = false)
        follow()
    }

    private suspend fun finishLearn(run: LearnRun) {
        val outcome = run.outcome
        repository.save(outcome.map { it.first }, outcome.map { it.second })
        val book = run.book
        mastered = updateLevels(setOf(book.id))
        val introduced = repository.observeCards().first().values.filter { it.bookId == book.id }.map { it.key }.toSet()
        val open = levels.value[book.id]?.unlocked ?: RepertoireLevel.CORE
        moreToLearn = LearnPlanner.next(run.index, open, introduced, allowed = gates.value[book.id]) != null
        moment = RunMoment.Finished(outcome.count { it.second.firstTryRight }, outcome.size)
        publish()
    }

    // ------------------------------------------------------------------ review

    /** Every due card of [bookId], or of every opening when null, mixed. */
    fun startReview(bookId: String? = null) {
        val all = indexes.value ?: return
        val chosen = if (bookId == null) all else all.filter { it.book.id == bookId }
        val queue = ReviewQueue.build(chosen, cards.value.values, clock())
        if (queue.isEmpty()) return
        startQueue(RunKind.REVIEW, chosen.singleOrNull()?.book?.title ?: queue.first().book.title, queue)
    }

    /**
     * The opening's test (Phase 12): [TEST_SIZE] of its Core positions at random, with no hints.
     * Nothing is saved to the schedule; [TEST_PASS] right first time opens every drill of it.
     */
    fun startTest(bookId: String, random: Random = Random.Default) {
        val index = indexes.value?.firstOrNull { it.book.id == bookId } ?: return
        val now = clock()
        val queue = index.book.cards(RepertoireLevel.CORE)
            .mapNotNull { node -> index.pathTo(node.key)?.let { path -> DueCard(index, node, RepertoireCards.introduce(bookId, node, now), path) } }
            .shuffled(random)
            .take(TEST_SIZE)
        if (queue.isEmpty()) return
        startQueue(RunKind.TEST, "Test", queue)
    }

    /** The tricky lines of [bookId], each from the start of its line (D76). */
    fun startTricky(bookId: String) {
        val index = indexes.value?.firstOrNull { it.book.id == bookId } ?: return
        val queue = ReviewQueue.tricky(listOf(index), cards.value.values)
        if (queue.isEmpty()) return
        startQueue(RunKind.TRICKY, index.book.title, queue)
    }

    private fun startQueue(kind: RunKind, title: String, queue: List<DueCard>) {
        clear()
        val session = ReviewSession(queue, clock)
        review = session
        val board = RepertoireBoard(line = { reviewLine(session) to (session.isOwnTurn && moment.lets()) }, submit = ::submitReview)
        mutableRun.value = RunUi(kind, title, queue.first().book.side, board, moment, null, 0, queue.size, tricky = queue.first().fromStart)
        follow()
    }

    private fun reviewLine(session: ReviewSession): List<String> {
        val card = session.current ?: return frozen.orEmpty()
        val solved = (moment as? RunMoment.CardDone)?.played
        return when {
            solved != null -> card.path + solved
            card.fromStart -> card.path.take(session.ply)
            else -> card.path
        }
    }

    private fun submitReview(move: Move): BoardFeedback {
        val session = review ?: return MoveEffects.ILLEGAL
        val card = session.current ?: return MoveEffects.ILLEGAL
        val before = session.position
        val wasAtCard = session.atCard
        val expected = if (wasAtCard) card.node.move!! else card.path[session.ply]
        val answer = session.submit(move.uci) ?: return MoveEffects.ILLEGAL
        hintFrom = null
        whyOpen = false
        return when (answer) {
            CardAnswer.RIGHT -> {
                ring = move.to.algebraic to true
                lastNote = card.book.at(LegalMoveGenerator.makeMove(before, move))?.note
                if (session.done) {
                    saveLast(session, played = move.uci)
                    MoveEffects.CORRECT
                } else {
                    moment = RunMoment.YourMove
                    follow()
                    MoveEffects.feedbackFor(before, move, LegalMoveGenerator.makeMove(before, move))
                }
            }
            CardAnswer.ALSO -> {
                moment = RunMoment.Also(null, null)
                publish()
                MoveEffects.ILLEGAL
            }
            CardAnswer.WRONG -> {
                ring = move.to.algebraic to false
                moment = RunMoment.Shown(numbered(before, legal(before, expected), session.ply), expected)
                publish()
                MoveEffects.WRONG
            }
        }
    }

    private fun saveLast(session: ReviewSession, played: String?, shown: String? = null) {
        val (card, log) = session.outcome.last()
        moment = RunMoment.CardDone(log.firstTryRight, card.dueAt, played, shown)
        publish()
        mutableRun.value?.board?.refresh(animate = played != null)
        // A test only looks: the schedule stays as it is.
        if (mutableRun.value?.kind != RunKind.TEST) {
            viewModelScope.launch {
                repository.save(listOf(card), listOf(log))
                reviewLog.value = reviewLog.value + (card.bookId to repository.reviews(card.bookId))
            }
        }
        // The next card comes by itself: a right answer at once, a miss after a look at the answer.
        val index = session.index
        job?.cancel()
        job = viewModelScope.launch {
            delay(if (log.firstTryRight) CARD_RIGHT_MS else CARD_MISSED_MS)
            if (review === session && session.index == index) next()
        }
    }

    // ------------------------------------------------------------------ controls

    fun next() {
        when (moment) {
            is RunMoment.CardDone -> {
                val session = review ?: return
                session.next()
                if (session.finished) {
                    if (mutableRun.value?.kind == RunKind.TEST) {
                        if (session.rightFirstTime >= testPassMark(session.cards.size)) tests?.pass(session.cards.first().book.id)
                        moment = RunMoment.Finished(session.rightFirstTime, session.cards.size)
                        publish()
                        return
                    }
                    viewModelScope.launch {
                        mastered = updateLevels(session.cards.map { it.book.id }.toSet())
                        moment = RunMoment.Finished(session.rightFirstTime, session.cards.size)
                        publish()
                    }
                } else {
                    moment = RunMoment.YourMove
                    ring = null
                    lastNote = null
                    whyOpen = false
                    mutableRun.value = mutableRun.value?.copy(tricky = session.current?.fromStart == true, side = session.current!!.book.side, title = titleOf(session))
                    mutableRun.value?.board?.refresh(animate = false)
                    follow()
                }
            }
            else -> Unit
        }
    }

    private fun titleOf(session: ReviewSession): String =
        if (mutableRun.value?.kind == RunKind.REVIEW && session.cards.map { it.book.id }.distinct().size > 1) session.current!!.book.title
        else mutableRun.value?.title.orEmpty()

    /** Shows or hides the note on the learner's last move. */
    fun why() {
        if (lastNote == null) return
        whyOpen = !whyOpen
        publish()
    }

    /** First the piece to move; asked again, the move itself (it then counts as shown). */
    fun hint() {
        if (mutableRun.value?.kind == RunKind.TEST) return
        if (hintFrom != null) {
            hintFrom = null
            showAnswer()
            return
        }
        val square = learn?.hint() ?: review?.hint() ?: return
        hintFrom = square
        publish()
    }

    fun showAnswer() {
        learn?.let { run ->
            val before = run.position
            val uci = run.showAnswer() ?: return
            moment = RunMoment.Shown(numbered(before, legal(before, uci), run.ply), uci)
            publish()
            mutableRun.value?.board?.refresh(animate = false)
            return
        }
        val session = review ?: return
        val before = session.position
        val atCard = session.atCard
        val uci = session.showAnswer() ?: return
        if (atCard) {
            // The card counts as missed; its answer stays drawn on the board until Next.
            saveLast(session, played = null, shown = uci)
        } else {
            moment = RunMoment.Shown(numbered(before, legal(before, uci), session.ply), uci)
            publish()
        }
    }

    fun dismissMastered() {
        mastered = null
        publish()
    }

    /** Another learn segment of the same opening, after a finished one. */
    fun learnMore() {
        val run = learn ?: return
        val within = learnWithin
        val introduced = cards.value.values.filter { it.bookId == run.book.id }.map { it.key }.toSet()
        val open = levels.value[run.book.id]?.unlocked ?: RepertoireLevel.CORE
        // Stay in the chosen variation while it has new moves, then go on with the rest.
        startLearn(run.book.id, within?.takeIf { LearnPlanner.next(run.index, open, introduced, within = it, allowed = gates.value[run.book.id]) != null })
    }

    fun close() {
        clear()
        mutableRun.value = null
    }

    private fun clear() {
        job?.cancel()
        learn = null
        review = null
        moment = RunMoment.YourMove
        frozen = null
        hintFrom = null
        mastered = null
        masteredBook = null
        moreToLearn = false
        learnWithin = null
        ring = null
        lastNote = null
        whyOpen = false
    }

    // ------------------------------------------------------------------ helpers

    /** Opens the next level of each of [bookIds] whose open level is now mastered; returns a level just mastered. */
    private suspend fun updateLevels(bookIds: Set<String>): RepertoireLevel? {
        val books = indexes.value.orEmpty().map { it.book }.filter { it.id in bookIds }
        val cardMap = repository.observeCards().first()
        var won: RepertoireLevel? = null
        for (book in books) {
            val log = repository.reviews(book.id)
            reviewLog.value = reviewLog.value + (book.id to log)
            val before = levels.value[book.id] ?: LevelProgress(book.id)
            val after = LevelUnlocks.update(book, before, cardMap, log, clock(), zone)
            if (after != before) {
                repository.saveLevel(after)
                won = before.unlocked
                masteredBook = book
            }
        }
        return won
    }

    private var masteredBook: RepertoireBook? = null

    private fun publish() {
        val current = mutableRun.value ?: return
        val run = learn
        val session = review
        val (steps, step) = when {
            run != null -> run.segment.newCards.size to stepOf(run)
            session != null -> session.cards.size to session.index
            else -> current.steps to current.step
        }
        mutableRun.value = current.copy(
            moment = moment,
            against = againstOf(currentLine(), current.side),
            step = step,
            steps = steps,
            hintFrom = hintFrom,
            mastered = mastered,
            masteredBook = mastered?.let { masteredBook?.title },
            nextLevelCards = mastered?.let { level ->
                RepertoireLevel.entries.getOrNull(level.ordinal + 1)?.let { next -> masteredBook?.let { book -> book.nodes.values.count { book.isCard(it) && it.level == next } } }
            } ?: 0,
            moreToLearn = moreToLearn,
            ringSquare = ring?.first,
            ringRight = ring?.second ?: true,
            why = lastNote?.text?.takeIf { whyOpen },
            canWhy = lastNote != null,
        )
    }

    /** New cards already met in this pass. */
    private fun stepOf(run: LearnRun): Int {
        if (run.pass == LearnRun.Pass.DONE) return run.segment.newCards.size
        var position = Position.initial()
        var met = 0
        for (uci in (frozen ?: run.segment.moves.take(run.ply))) {
            if (run.book.at(position)?.key in run.segment.newCards && position.sideToMove == run.book.side) met++
            position = RepertoireIndex.play(position, uci) ?: break
        }
        return met
    }

    /** The moves on the board now, from the start. */
    private fun currentLine(): List<String> {
        learn?.let { return frozen ?: it.segment.moves.take(it.ply) }
        return review?.let(::reviewLine).orEmpty()
    }

    /** The opponent's last move, numbered, when the learner is to move after it. */
    private fun againstOf(line: List<String>, side: PieceColor): String? {
        if (line.isEmpty()) return null
        val before = line.dropLast(1).fold(Position.initial()) { p, uci -> RepertoireIndex.play(p, uci) ?: return null }
        val move = LegalMoveGenerator.generateLegalMoves(before).firstOrNull { it.uci == line.last() } ?: return null
        if (LegalMoveGenerator.makeMove(before, move).sideToMove != side) return null
        return numbered(before, move, line.size - 1)
    }

    /** The first position where the opponent has a choice: the replies there, by share at the learner's level. */
    private fun chartOf(index: RepertoireIndex, band: Int?): ReplyChart? {
        var position = Position.initial()
        val path = ArrayList<String>()
        while (path.size < 40) {
            val node = index.book.at(position) ?: return null
            if (!node.own && node.replies.size >= 2) {
                val lastMove = path.lastOrNull() ?: return null
                val before = path.dropLast(1).fold(Position.initial()) { p, uci -> RepertoireIndex.play(p, uci)!! }
                val replies = node.replies.sortedByDescending { it.shareFor(band) }.map {
                    ChartReply(SanFormatter.format(position, legal(position, it.uci)), it.shareFor(band), it.verdict?.isError == true)
                }
                return ReplyChart(numbered(before, legal(before, lastMove), path.size - 1), replies)
            }
            val next = (if (node.own) node.move else node.replies.singleOrNull()?.uci) ?: return null
            position = RepertoireIndex.play(position, next) ?: return null
            path += next
        }
        return null
    }

    companion object {
        /** The opponent's move comes after this pause, so the learner sees it. */
        const val REPLY_DELAY_MS = 500L

        /** In the watch pass: how long the learner's move shows as an arrow, and the pause after each move. */
        const val WATCH_ARROW_MS = 800L
        const val WATCH_MOVE_MS = 700L

        /** How long a finished pass stays on the board before it starts again from memory. */
        const val PASS_PAUSE_MS = 1_600L

        /** An opening's test: how many positions, and how many right first time open its drills. */
        const val TEST_SIZE = 5
        const val TEST_PASS = 4

        /** Four of five, or the same share of a shorter test. */
        fun testPassMark(size: Int): Int = (size * TEST_PASS + TEST_SIZE - 1) / TEST_SIZE

        /** How long a review card stays after a right answer, and after a miss. */
        const val CARD_RIGHT_MS = 700L
        const val CARD_MISSED_MS = 1_400L

        private fun legal(position: Position, uci: String): Move =
            LegalMoveGenerator.generateLegalMoves(position).first { it.uci == uci }

        /** "3.Bc4" or "3...Nf6": [plyIndex] is the move's index from the start. */
        fun numbered(position: Position, move: Move, plyIndex: Int): String {
            val san = SanFormatter.format(position, move)
            val number = plyIndex / 2 + 1
            return if (position.sideToMove == PieceColor.WHITE) "$number.$san" else "$number...$san"
        }
    }

    class Factory(
        private val repository: RepertoireProgressRepository,
        private val loadBooks: suspend () -> List<RepertoireBook>,
        private val rating: () -> Int?,
        private val gate: Flow<Map<String, OpenLines>> = flowOf(emptyMap()),
        private val tests: OpeningTests? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = RepertoireViewModel(repository, loadBooks, rating, gate = gate, tests = tests) as T
    }
}

/** Moments when the learner may move on the board. */
private fun RunMoment.lets(): Boolean =
    this is RunMoment.YourMove || this is RunMoment.FromTheTop || this is RunMoment.Also || this is RunMoment.Shown

/** "4.d3 protects e4." from a longer note. */
internal fun firstSentence(text: String): String = text.split(Regex("(?<=[.!?])\\s+")).first()
