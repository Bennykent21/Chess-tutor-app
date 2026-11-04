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
import com.chesstutor.app.domain.repertoire.BranchQuestion
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
import com.chesstutor.app.domain.repertoire.RepertoireIndex
import com.chesstutor.app.domain.repertoire.RepertoireLevel
import com.chesstutor.app.domain.repertoire.RepertoireNote
import com.chesstutor.app.domain.repertoire.RepertoireProgressRepository
import com.chesstutor.app.domain.repertoire.ReviewQueue
import com.chesstutor.app.domain.repertoire.ReviewSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
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
) {
    /** The opening database's name where the start line ends, for Opening Battle ("Caro-Kann Defense"). */
    val battleLine: String
        get() = book.start.fold(com.chesstutor.app.core.Position.initial()) { p, uci -> RepertoireIndex.play(p, uci) ?: p }
            .let { book.at(it)?.name } ?: book.title
}

data class RepertoireState(val loading: Boolean = true, val books: List<RepertoireBookUi> = emptyList()) {
    val dueTotal: Int get() = books.sumOf { it.due }

    fun book(id: String): RepertoireBookUi? = books.firstOrNull { it.book.id == id }
}

/** How the opponent answers at the first branch of the tree ("After 3.Bc4"), at the learner's level. */
data class ReplyChart(val after: String, val replies: List<ChartReply>)

data class ChartReply(val san: String, val share: Double, val error: Boolean)

enum class RunKind { LEARN, REVIEW, TRICKY }

/** What the trainer is saying at this moment of a run. */
sealed interface RunMoment {
    /** The learner's turn: the zone asks "what do you play against ...?". */
    data object YourMove : RunMoment

    /** The recall pass of a learn run starts again from the first move. */
    data object FromTheTop : RunMoment

    /** A new move, shown after the learner's guess, with its note and how often it is played. */
    data class Reveal(val san: String, val guessed: Boolean, val note: RepertoireNote?, val share: Double?, val pitfall: String?) : RunMoment

    /**
     * Just as good, but not this repertoire's move. The first time a move is met it is named and
     * drawn ([san], [uci]); in a recall or review it stays hidden (null), so the card is still recalled.
     */
    data class Also(val san: String?, val uci: String?) : RunMoment

    /** The move to play is drawn on the board (after a wrong move or "Show me"). */
    data class Shown(val san: String, val uci: String) : RunMoment

    /** "What do most players play here?" at a Core branch; [picked] once answered. */
    data class Branch(val question: BranchQuestion, val sans: List<String>, val picked: String?) : RunMoment

    /** A review card is over: [played] is the right move now on the board, [shown] the answer drawn after "Show me". */
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
) : ViewModel() {
    private val indexes = MutableStateFlow<List<RepertoireIndex>?>(null)
    private val reviewLog = MutableStateFlow<Map<String, List<CardReview>>>(emptyMap())
    private val cards = repository.observeCards().stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    private val levels = repository.observeLevels().stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val state: StateFlow<RepertoireState> = combine(indexes, cards, levels, reviewLog) { idx, cardMap, levelMap, log ->
        if (idx == null) return@combine RepertoireState(loading = true)
        RepertoireState(loading = false, books = idx.map { bookUi(it, cardMap, levelMap[it.book.id], log[it.book.id].orEmpty()) })
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
    private val asked = HashSet<String>()

    init {
        viewModelScope.launch {
            val books = runCatching { loadBooks() }.getOrDefault(emptyList())
            reviewLog.value = books.associate { it.id to repository.reviews(it.id) }
            indexes.value = books.map(::RepertoireIndex)
        }
    }

    private fun band(book: RepertoireBook): Int? = book.bandFor(rating())

    private fun bookUi(index: RepertoireIndex, cardMap: Map<String, CardState>, level: LevelProgress?, log: List<CardReview>): RepertoireBookUi {
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
            fresh = book.cards(progress.unlocked).count { it.key !in introduced },
            chart = chartOf(index, band(book)),
            introduced = book.cards().count { it.key in introduced },
            total = book.cards().size,
        )
    }

    // ------------------------------------------------------------------ learn

    fun startLearn(bookId: String) {
        val index = indexes.value?.firstOrNull { it.book.id == bookId } ?: return
        val open = levels.value[bookId]?.unlocked ?: RepertoireLevel.CORE
        val introduced = cards.value.values.filter { it.bookId == bookId }.map { it.key }.toSet()
        val segment = LearnPlanner.next(index, open, introduced) ?: return
        startSegment(index, segment)
    }

    private fun startSegment(index: RepertoireIndex, segment: LearnSegment) {
        clear()
        val run = LearnRun(index, segment, clock)
        learn = run
        val board = RepertoireBoard(
            line = { (frozen ?: segment.moves.take(run.ply)) to (run.isOwnTurn && moment.lets()) },
            submit = ::submitLearn,
        )
        mutableRun.value = RunUi(RunKind.LEARN, index.book.title, index.book.side, board, moment, null, 0, segment.newCards.size, tricky = false)
        follow()
    }

    private fun submitLearn(move: Move): BoardFeedback {
        val run = learn ?: return MoveEffects.ILLEGAL
        val before = run.position
        val line = run.segment.moves.take(run.ply)
        val expected = run.segment.moves.getOrNull(run.ply) ?: return MoveEffects.ILLEGAL
        val card = run.node
        // A new move in the guess pass is followed by its note once it is on the board.
        val firstMeeting = run.pass == LearnRun.Pass.GUESS && run.isNewCard
        val result = run.submit(move.uci) ?: return MoveEffects.ILLEGAL
        hintFrom = null
        val san = numbered(before, legal(before, expected), line.size)
        return when (result) {
            LearnMoveResult.GUESSED, LearnMoveResult.RIGHT -> {
                val after = LegalMoveGenerator.makeMove(before, move)
                if (firstMeeting) {
                    // The board stays on this move while the note shows, even when the pass starts again.
                    frozen = line + expected
                    moment = RunMoment.Reveal(
                        san = san,
                        guessed = result == LearnMoveResult.GUESSED,
                        note = run.book.at(after)?.note,
                        share = card?.moveShare,
                        pitfall = card?.pitfall?.let { p -> "Many players here play ${numbered(before, legal(before, p.uci), line.size)}, a ${p.verdict.name.lowercase()}." },
                    )
                    publish()
                    if (result == LearnMoveResult.GUESSED) MoveEffects.CORRECT else MoveEffects.feedbackFor(before, move, after)
                } else {
                    moment = RunMoment.YourMove
                    follow()
                    if (run.pass == LearnRun.Pass.DONE) MoveEffects.CORRECT else MoveEffects.feedbackFor(before, move, after)
                }
            }
            LearnMoveResult.ALSO -> {
                // Met for the first time, the arrow shows the repertoire move; in a recall it stays hidden.
                moment = if (firstMeeting) RunMoment.Also(san, expected) else RunMoment.Also(null, null)
                publish()
                MoveEffects.ILLEGAL
            }
            LearnMoveResult.REVEALED, LearnMoveResult.WRONG -> {
                // The wrong move slides back and the right one is drawn; the learner plays it.
                moment = RunMoment.Shown(san, expected)
                publish()
                MoveEffects.WRONG
            }
        }
    }

    /** Plays the opponent's moves after a pause, stopping for a branch question or the learner's turn. */
    private fun follow() {
        job?.cancel()
        job = viewModelScope.launch {
            val run = learn
            val session = review
            if (run != null) {
                while (run.pass != LearnRun.Pass.DONE && !run.isOwnTurn) {
                    val node = run.node
                    if (run.pass == LearnRun.Pass.GUESS && node != null && node.key !in asked) {
                        BranchQuestion.at(node, band(run.book))?.let { question ->
                            asked += node.key
                            moment = RunMoment.Branch(question, question.choices.map { numbered(run.position, legal(run.position, it.uci), run.ply) }, null)
                            publish()
                            return@launch
                        }
                    }
                    publish()
                    delay(replyDelayMs)
                    run.playOpponent()
                    mutableRun.value?.board?.refresh(animate = true)
                }
                if (run.pass == LearnRun.Pass.DONE) finishLearn(run)
            } else if (session != null) {
                while (!session.done && !session.finished && !session.isOwnTurn && !session.atCard) {
                    publish()
                    delay(replyDelayMs)
                    session.playOpponent()
                    mutableRun.value?.board?.refresh(animate = true)
                }
            }
            publish()
        }
    }

    private suspend fun finishLearn(run: LearnRun) {
        val outcome = run.outcome
        repository.save(outcome.map { it.first }, outcome.map { it.second })
        val book = run.book
        mastered = updateLevels(setOf(book.id))
        val introduced = repository.observeCards().first().values.filter { it.bookId == book.id }.map { it.key }.toSet()
        val open = levels.value[book.id]?.unlocked ?: RepertoireLevel.CORE
        moreToLearn = LearnPlanner.next(run.index, open, introduced) != null
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
        return when (answer) {
            CardAnswer.RIGHT -> {
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
        viewModelScope.launch {
            repository.save(listOf(card), listOf(log))
            reviewLog.value = reviewLog.value + (card.bookId to repository.reviews(card.bookId))
        }
    }

    // ------------------------------------------------------------------ controls

    fun next() {
        when (val now = moment) {
            is RunMoment.Reveal -> {
                frozen = null
                val run = learn ?: return
                moment = if (run.pass == LearnRun.Pass.RECALL && run.ply == 0) RunMoment.FromTheTop else RunMoment.YourMove
                mutableRun.value?.board?.refresh(animate = false)
                follow()
            }
            is RunMoment.Branch -> if (now.picked != null) {
                moment = RunMoment.YourMove
                follow()
            }
            is RunMoment.CardDone -> {
                val session = review ?: return
                session.next()
                if (session.finished) {
                    viewModelScope.launch {
                        mastered = updateLevels(session.cards.map { it.book.id }.toSet())
                        moment = RunMoment.Finished(session.rightFirstTime, session.cards.size)
                        publish()
                    }
                } else {
                    moment = RunMoment.YourMove
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

    /** Answers the branch question with the reply [uci]. */
    fun pickBranch(uci: String) {
        val now = moment as? RunMoment.Branch ?: return
        if (now.picked != null) return
        moment = now.copy(picked = uci)
        publish()
    }

    fun hint() {
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
        val bookId = learn?.book?.id ?: return
        startLearn(bookId)
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
        asked.clear()
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
                RepertoireLevel.entries.getOrNull(level.ordinal + 1)?.let { next -> masteredBook?.nodes?.values?.count { it.own && it.level == next } }
            } ?: 0,
            moreToLearn = moreToLearn,
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = RepertoireViewModel(repository, loadBooks, rating) as T
    }
}

/** Moments when the learner may move on the board. */
private fun RunMoment.lets(): Boolean = this is RunMoment.YourMove || this is RunMoment.FromTheTop || this is RunMoment.Also || this is RunMoment.Shown
