package com.chesstutor.app.feature.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.core.GameStatus
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.SanFormatter
import com.chesstutor.app.domain.train.LearnerScore
import com.chesstutor.app.domain.train.PositionGoal
import com.chesstutor.app.domain.train.PositionJudge
import com.chesstutor.app.domain.train.PositionKind
import com.chesstutor.app.domain.train.PositionOutcome
import com.chesstutor.app.domain.train.PositionQueue
import com.chesstutor.app.domain.train.PositionRecord
import com.chesstutor.app.domain.train.PositionRecords
import com.chesstutor.app.domain.train.PositionSet
import com.chesstutor.app.domain.train.TrainPosition
import com.chesstutor.app.engine.EngineEvaluation
import com.chesstutor.app.feature.play.PlaySession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** A set on the Endgames or Middlegames page, with how many of its positions were won. */
data class PositionSetUi(val set: PositionSet, val won: Int) {
    val total: Int get() = set.positions.size
}

data class PositionsState(val sets: List<PositionSetUi> = emptyList(), val loading: Boolean = true) {
    fun of(kind: PositionKind): List<PositionSetUi> = sets.filter { it.set.kind == kind }
}

/** How a finished position ended, for the words above the board. */
enum class PositionEnd { MATED, PROMOTED, CONVERTED, HELD, DRAWN, LOST, GAVE_UP }

/** One position being played out against Stockfish (Phase 12, step 3, mock-up round 4). */
data class PositionRunUi(
    val set: PositionSet,
    val queue: List<TrainPosition>,
    val index: Int,
    val board: PlaySession,
    val outcome: PositionOutcome = PositionOutcome.PLAYING,
    val end: PositionEnd? = null,
    val learnerMoves: Int = 0,
    val promoted: Boolean = false,
    /** Stockfish is thinking (its reply, or the first look at the position). */
    val thinking: Boolean = true,
    val hintFrom: String? = null,
    val hintTo: String? = null,
    /** The learner's move that let it slip ("12.Kd6"), and the move that kept it ("Kf6"). */
    val slip: String? = null,
    val better: String? = null,
) {
    val position: TrainPosition get() = queue[index]
    val side: PieceColor get() = position.side
    val ended: Boolean get() = outcome != PositionOutcome.PLAYING
    val next: TrainPosition? get() = queue.getOrNull(index + 1)
}

/**
 * Train's "Win this" endgames and "Convert this" middlegames: the learner plays a position out
 * against Stockfish at full strength. Each search of the learner's move gives Stockfish's reply,
 * the score the [PositionJudge] reads, and the learner's best move next (for Hint, and to name
 * the move that kept it when one slips). A slipped position comes back tomorrow.
 */
class PositionTrainerViewModel(
    private val records: PositionRecords,
    private val loadSets: suspend () -> List<PositionSet>,
    private val engine: suspend (fen: String) -> EngineEvaluation?,
    private val clock: () -> Instant = Instant::now,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val replyDelayMs: Long = REPLY_DELAY_MS,
) : ViewModel() {

    private val sets = MutableStateFlow<List<PositionSet>?>(null)

    val state: StateFlow<PositionsState> = combine(sets, records.all()) { loaded, results ->
        PositionsState(
            sets = loaded.orEmpty().map { set -> PositionSetUi(set, set.positions.count { results[it.id]?.won == true }) },
            loading = loaded == null,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PositionsState())

    private val mutableRun = MutableStateFlow<PositionRunUi?>(null)
    val run: StateFlow<PositionRunUi?> = mutableRun.asStateFlow()

    private var job: Job? = null

    /** The learner's best move in the position on the board, from the last search. */
    private var best: String? = null

    init {
        viewModelScope.launch { sets.value = runCatching { loadSets() }.getOrDefault(emptyList()) }
    }

    /** Plays [setId]'s positions not yet won, in order (all of them again once every one is won). */
    fun start(setId: String) {
        val set = sets.value?.firstOrNull { it.id == setId } ?: return
        open(set, PositionQueue.of(set, records.all().value, today()), 0)
    }

    fun next() {
        val run = mutableRun.value ?: return
        if (run.next != null) open(run.set, run.queue, run.index + 1)
    }

    /** The same position from its start ("Restart" while playing, "Again" at the end). */
    fun again() {
        val run = mutableRun.value ?: return
        open(run.set, run.queue, run.index)
    }

    fun giveUp() {
        val run = mutableRun.value ?: return
        if (run.ended) return
        job?.cancel()
        finish(PositionOutcome.SLIPPED, PositionEnd.GAVE_UP)
    }

    /** First the piece to move, then where it goes. */
    fun hint() {
        val run = mutableRun.value ?: return
        val move = best ?: return
        if (run.ended || run.thinking) return
        mutableRun.value = if (run.hintFrom == null) run.copy(hintFrom = move.take(2)) else run.copy(hintTo = move.substring(2, 4))
    }

    fun close() {
        job?.cancel()
        mutableRun.value = null
    }

    private fun open(set: PositionSet, queue: List<TrainPosition>, index: Int) {
        job?.cancel()
        val position = queue[index]
        val board = PlaySession(Position.fromFen(position.fen), setOf(position.side), ::onLearnerMove)
        best = null
        mutableRun.value = PositionRunUi(set, queue, index, board)
        job = viewModelScope.launch {
            best = runCatching { engine(position.fen) }.getOrNull()?.bestMoveUci?.takeIf { it.isNotBlank() }
            update { it.copy(thinking = false) }
            board.open = true
        }
    }

    private fun onLearnerMove(move: Move) {
        val run = mutableRun.value ?: return
        val board = run.board
        board.open = false
        val before = board.positions[board.positions.size - 2]
        val goal = run.position.goal
        val promoted = run.promoted || move.promotion != null
        val moves = run.learnerMoves + 1
        val slip = numbered(before, move)
        val better = best?.takeIf { it != move.uci }?.let { sanOf(before, it) }
        update { it.copy(learnerMoves = moves, promoted = promoted, hintFrom = null, hintTo = null, thinking = true, slip = slip, better = better) }
        PositionJudge.ended(goal, board.game, run.side)?.let { return finish(it, endOf(it, goal, promoted, mated = it == PositionOutcome.WON)) }
        job = viewModelScope.launch {
            val search = runCatching { engine(board.game.position.toFen()) }.getOrNull()
            val score = search?.let { LearnerScore.of(it.centipawns, it.mateInMoves, run.side) }
            val outcome = score?.let { PositionJudge.scored(goal, it, promoted, moves) } ?: PositionOutcome.PLAYING
            if (outcome != PositionOutcome.PLAYING) {
                val lost = score!!.losing || (score.mate == null && (score.cp ?: 0) <= PositionJudge.HOLD_LOST_CP)
                finish(outcome, endOf(outcome, goal, promoted, mated = false, lost = lost))
                return@launch
            }
            val reply = search?.bestMoveUci?.let { legal(board.game.position, it) }
                ?: LegalMoveGenerator.generateLegalMoves(board.game.position).firstOrNull()
                ?: return@launch
            delay(replyDelayMs)
            board.play(reply)
            PositionJudge.ended(goal, board.game, run.side)?.let { ended ->
                finish(ended, endOf(ended, goal, promoted, mated = false, lost = ended == PositionOutcome.SLIPPED && board.game.status == GameStatus.CHECKMATE))
                return@launch
            }
            best = search?.principalVariation?.getOrNull(1)
            update { it.copy(thinking = false) }
            board.open = true
        }
    }

    private fun finish(outcome: PositionOutcome, end: PositionEnd) {
        val run = mutableRun.value ?: return
        run.board.open = false
        val keep = outcome == PositionOutcome.SLIPPED
        mutableRun.value = run.copy(
            outcome = outcome,
            end = end,
            thinking = false,
            hintFrom = null,
            hintTo = null,
            slip = run.slip.takeIf { keep && end != PositionEnd.GAVE_UP },
            better = run.better.takeIf { keep && end != PositionEnd.GAVE_UP },
        )
        val id = run.position.id
        val before = records.all().value[id] ?: PositionRecord()
        records.save(id, if (keep) before.copy(dueDay = today() + 1) else PositionRecord(won = true))
    }

    private fun endOf(outcome: PositionOutcome, goal: PositionGoal, promoted: Boolean, mated: Boolean, lost: Boolean = false): PositionEnd = when (outcome) {
        PositionOutcome.WON -> when {
            mated -> PositionEnd.MATED
            goal == PositionGoal.CONVERT -> PositionEnd.CONVERTED
            promoted -> PositionEnd.PROMOTED
            else -> PositionEnd.MATED
        }
        PositionOutcome.HELD -> PositionEnd.HELD
        else -> if (lost || goal == PositionGoal.HOLD) PositionEnd.LOST else PositionEnd.DRAWN
    }

    private fun update(change: (PositionRunUi) -> PositionRunUi) {
        mutableRun.value?.let { mutableRun.value = change(it) }
    }

    private fun today(): Long = clock().atZone(zone).toLocalDate().toEpochDay()

    private fun legal(position: Position, uci: String): Move? = LegalMoveGenerator.generateLegalMoves(position).firstOrNull { it.uci == uci }

    private fun sanOf(position: Position, uci: String): String? = legal(position, uci)?.let { SanFormatter.format(position, it) }

    /** "12.Kd6" or "12...Kd7". */
    private fun numbered(position: Position, move: Move): String =
        "${position.fullmoveNumber}${if (position.sideToMove == PieceColor.WHITE) "." else "..."}${SanFormatter.format(position, move)}"

    class Factory(
        private val records: PositionRecords,
        private val loadSets: suspend () -> List<PositionSet>,
        private val engine: suspend (fen: String) -> EngineEvaluation?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PositionTrainerViewModel(records, loadSets, engine) as T
    }

    companion object {
        /** A short pause before Stockfish's reply, so the learner sees their own move land. */
        const val REPLY_DELAY_MS = 350L

        /** Thinking time per search: near full strength on a phone, and quick enough to keep the game moving. */
        const val THINK_MS = 800
    }
}
