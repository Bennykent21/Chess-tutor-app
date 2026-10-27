package com.chesstutor.app.feature.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.domain.opening.DrillProgress
import com.chesstutor.app.domain.opening.OpeningDrillRepository
import com.chesstutor.app.domain.opening.OpeningTrainer
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.domain.opening.TrainerLine
import com.chesstutor.app.domain.opening.TrainerOpening
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One opening on the trainer's list: its lines and how well they are known. */
data class TrainerOpeningState(
    val opening: TrainerOpening,
    val lines: List<TrainerLine>,
    val learned: Int,
    val due: Int,
    val started: Int,
)

data class TrainerState(val loading: Boolean = true, val openings: List<TrainerOpeningState> = emptyList()) {
    /** Lines due again across every opening (for the Learn hub). */
    val dueTotal: Int get() = openings.sumOf { it.due }
}

/** The line on the board, and how the run went once it is over. */
data class DrillUi(
    val opening: TrainerOpening,
    val line: TrainerLine,
    val session: OpeningDrillSession,
    /** Set when the line is finished: the saved progress (when it comes back). */
    val result: DrillProgress? = null,
    /** Every line of the opening with its progress, to switch lines. */
    val lines: List<TrainerLine> = emptyList(),
)

/**
 * The opening trainer (plan 6C): the 15 openings and their lines from the bundled opening
 * database, the progress of each line, and the drill on the board. Shared by the Learn hub's
 * trainer row, the trainer list and the drill screen (scoped to the Learn graph).
 */
class OpeningTrainerViewModel(
    private val repository: OpeningDrillRepository,
    private val loadTree: suspend () -> OpeningTree,
    private val clock: () -> Long = System::currentTimeMillis,
    private val replyDelayMs: Long = REPLY_DELAY_MS,
) : ViewModel() {
    private val tree = MutableStateFlow<OpeningTree?>(null)
    private val linesByOpening = HashMap<String, List<TrainerLine>>()

    val trainer: StateFlow<TrainerState> = combine(tree, repository.observeDrills()) { t, progress ->
        if (t == null) return@combine TrainerState(loading = true)
        val now = clock()
        TrainerState(
            loading = false,
            openings = OpeningTrainer.openings.map { opening ->
                val lines = linesOf(opening, t)
                TrainerOpeningState(
                    opening = opening,
                    lines = lines,
                    learned = lines.count { progress[it.id]?.learned == true },
                    due = OpeningTrainer.due(lines, progress, now).size,
                    started = lines.count { it.id in progress },
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainerState())

    private val progress: StateFlow<Map<String, DrillProgress>> =
        repository.observeDrills().stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val mutableDrill = MutableStateFlow<DrillUi?>(null)
    val drill: StateFlow<DrillUi?> = mutableDrill.asStateFlow()
    private var follower: Job? = null

    init {
        viewModelScope.launch { tree.value = runCatching { loadTree() }.getOrDefault(OpeningTree.EMPTY) }
    }

    private fun linesOf(opening: TrainerOpening, t: OpeningTree): List<TrainerLine> =
        linesByOpening.getOrPut(opening.id) { OpeningTrainer.linesFor(opening, t) }

    /** Opens [openingId] on the line that needs it most: a due one, else a new one, else the least recent. */
    fun start(openingId: String) {
        viewModelScope.launch {
            val opening = OpeningTrainer.byId(openingId) ?: return@launch
            val lines = linesOf(opening, tree.filterNotNull().first())
            val known = progress.value
            val line = OpeningTrainer.due(lines, known, clock()).firstOrNull()
                ?: OpeningTrainer.nextNew(lines, known)
                ?: lines.minByOrNull { known[it.id]?.lastPlayedAt ?: 0L }
                ?: return@launch
            play(opening, line, lines)
        }
    }

    /** Opens one particular line of [openingId]. */
    fun startLine(openingId: String, lineId: String) {
        viewModelScope.launch {
            val opening = OpeningTrainer.byId(openingId) ?: return@launch
            val lines = linesOf(opening, tree.filterNotNull().first())
            lines.firstOrNull { it.id == lineId }?.let { play(opening, it, lines) }
        }
    }

    /** The same line again from the start. */
    fun again() {
        val current = mutableDrill.value ?: return
        play(current.opening, current.line, current.lines)
    }

    /** The next line of the same opening. */
    fun next() {
        mutableDrill.value?.let { start(it.opening.id) }
    }

    fun showAnswer() {
        mutableDrill.value?.session?.showAnswer()
    }

    fun close() {
        follower?.cancel()
        mutableDrill.value = null
    }

    /** Puts [line] on the board and follows it: book replies after a pause, saving the result at the end. */
    private fun play(opening: TrainerOpening, line: TrainerLine, lines: List<TrainerLine>) {
        follower?.cancel()
        val session = OpeningDrillSession(line)
        mutableDrill.value = DrillUi(opening, line, session, lines = lines)
        follower = viewModelScope.launch {
            session.state.collect {
                if (session.replyDue) {
                    delay(replyDelayMs)
                    session.playReply()
                } else if (session.isFinished && mutableDrill.value?.result == null) {
                    val saved = OpeningTrainer.afterRun(progress.value[line.id], line.id, session.isClean, clock())
                    repository.saveDrill(saved)
                    mutableDrill.value = mutableDrill.value?.copy(result = saved)
                }
            }
        }
    }

    companion object {
        /** The other side's book move comes after this pause, so the player sees it. */
        const val REPLY_DELAY_MS = 500L
    }

    class Factory(
        private val repository: OpeningDrillRepository,
        private val loadTree: suspend () -> OpeningTree,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OpeningTrainerViewModel(repository, loadTree) as T
    }
}
