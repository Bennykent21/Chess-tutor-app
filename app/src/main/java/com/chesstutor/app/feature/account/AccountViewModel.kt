package com.chesstutor.app.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.account.InMemoryGameAnalysisRepository
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.repository.GameRepository
import com.chesstutor.app.data.repository.ReviewRepository
import com.chesstutor.app.domain.ReviewItem
import com.chesstutor.app.domain.account.AnalysisSummary
import com.chesstutor.app.domain.account.GameAnalysisRepository
import com.chesstutor.app.domain.account.GameAnalyzer
import com.chesstutor.app.domain.account.GameLibrary
import com.chesstutor.app.domain.account.GameReview
import com.chesstutor.app.domain.account.GameReviewer
import com.chesstutor.app.domain.account.GameSummary
import com.chesstutor.app.domain.account.PlyEval
import com.chesstutor.app.domain.account.ReviewedMove
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.domain.puzzle.PuzzleStats
import com.chesstutor.app.domain.puzzle.PuzzleHistoryEntry
import com.chesstutor.app.domain.puzzle.PuzzlePack
import com.chesstutor.app.domain.learn.LessonProgress
import com.chesstutor.app.domain.account.TrainingCalculator
import com.chesstutor.app.domain.account.TrainingReport
import kotlinx.coroutines.flow.flow
import com.chesstutor.app.engine.EngineService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/** Where the Training page's numbers come from. */
class TrainingSources(
    val puzzleHistory: Flow<List<PuzzleHistoryEntry>> = flowOf(emptyList()),
    val loadPack: suspend () -> PuzzlePack = { PuzzlePack.EMPTY },
    val lessonProgress: Flow<Map<String, LessonProgress>> = flowOf(emptyMap()),
    val loadLessonCount: suspend () -> Int = { 0 },
)

/** The player's saved games, newest first, with openings named (plan section 6.4). */
data class GamesState(
    val loading: Boolean = true,
    val games: List<GameSummary> = emptyList(),
    /** Finished analyses by game id (accuracy badges). */
    val analysed: Map<String, AnalysisSummary> = emptyMap(),
) {
    fun game(id: String): GameSummary? = games.firstOrNull { it.id == id }
}

/** One game's analysis while it runs: [done] of [total] positions, or why it stopped. */
data class AnalysisJob(val done: Int = 0, val total: Int = 0, val waiting: Boolean = true, val error: String? = null) {
    val running: Boolean get() = error == null
    val progress: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/** What the review screen shows for one game. */
data class ReviewState(
    val game: GameSummary? = null,
    val review: GameReview? = null,
    val summary: AnalysisSummary? = null,
    val job: AnalysisJob? = null,
    /** The cached engine verdicts by ply, for the eval bar and best-move arrows. */
    val evals: Map<Int, PlyEval> = emptyMap(),
)

/**
 * The Account tab's view model (plan section 7.3), shared by every screen in the tab: the
 * games list, game pages and reviews, with analysis running in the background (it carries
 * on while the player browses the tab) and the dashboards as Phase 5 adds them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModel(
    private val repository: GameRepository,
    loadOpenings: suspend () -> OpeningTree,
    puzzleStats: Flow<PuzzleStats>,
    private val analysis: GameAnalysisRepository = InMemoryGameAnalysisRepository(),
    private val engine: EngineService? = null,
    private val mistakeReviews: ReviewRepository? = null,
    trainingSources: TrainingSources = TrainingSources(),
    worker: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val tree = MutableStateFlow<OpeningTree?>(null)

    /** Summaries by game, so an import that adds games does not re-name every old one. */
    private val cache = HashMap<String, GameSummary>()

    val games: StateFlow<GamesState> = combine(
        combine(repository.observeGames(), tree.filterNotNull()) { records, openings -> summarise(records, openings) }.flowOn(worker),
        analysis.observeSummaries(),
    ) { list, analysed -> GamesState(loading = false, games = list, analysed = analysed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GamesState())

    val stats: StateFlow<PuzzleStats> = puzzleStats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PuzzleStats())

    private val mutableJobs = MutableStateFlow<Map<String, AnalysisJob>>(emptyMap())
    /** Analyses running or waiting, and ones that failed, by game id. */
    val jobs: StateFlow<Map<String, AnalysisJob>> = mutableJobs.asStateFlow()
    private val running = HashMap<String, Job>()
    /** One analysis at a time: they share the one engine. */
    private val engineLock = Mutex()

    private val reviewedId = MutableStateFlow<String?>(null)

    /** The review of the game opened with [openReview], updating as its analysis comes in. */
    val review: StateFlow<ReviewState> = reviewedId.flatMapLatest { id ->
        if (id == null) return@flatMapLatest flowOf(ReviewState())
        val game = games.map { it.game(id) }.distinctUntilChanged()
        combine(game, analysis.observeEvals(id), games.map { it.analysed[id] }.distinctUntilChanged(), jobs.map { it[id] }.distinctUntilChanged()) { g, evals, summary, job ->
            ReviewState(
                game = g,
                review = g?.let { GameReviewer.review(it.startFen ?: Position.STARTING_FEN, it.moves, evals, it.bookPlies) },
                summary = summary,
                job = job,
                evals = evals.associateBy { it.ply },
            )
        }.flowOn(worker)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewState())

    /** The Training page: puzzle rating history, theme strengths and lessons done (plan 6.4). */
    val training: StateFlow<TrainingReport> = combine(
        trainingSources.puzzleHistory,
        trainingSources.lessonProgress,
        flow { emit(runCatching { trainingSources.loadPack() }.getOrDefault(PuzzlePack.EMPTY)) },
        flow { emit(runCatching { trainingSources.loadLessonCount() }.getOrDefault(0)) },
    ) { history, lessons, pack, lessonTotal ->
        TrainingCalculator.report(history, { id -> pack.get(id)?.themes }, lessons.values.count { it.completed }, lessonTotal)
    }.flowOn(worker).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainingCalculator.report(emptyList(), { null }, 0, 0))

    /** Reviews of every analysed game, kept until the game is analysed again. */
    private val reviewCache = HashMap<String, Pair<Long, GameReview>>()

    /** The review of every analysed game by id, for the Performance page (accuracy by phase, mistake types). */
    val reviews: StateFlow<Map<String, GameReview>> = games.map { state -> reviewsOf(state) }
        .flowOn(worker)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch { tree.value = runCatching { loadOpenings() }.getOrDefault(OpeningTree.EMPTY) }
    }

    private suspend fun reviewsOf(state: GamesState): Map<String, GameReview> {
        val out = HashMap<String, GameReview>()
        for ((id, summary) in state.analysed) {
            val game = state.game(id) ?: continue
            val cached = reviewCache[id]?.takeIf { it.first == summary.analysedAt }?.second
            out[id] = cached ?: GameReviewer.review(game.startFen ?: Position.STARTING_FEN, game.moves, analysis.evals(id), game.bookPlies)
                .also { reviewCache[id] = summary.analysedAt to it }
        }
        reviewCache.keys.retainAll(out.keys)
        return out
    }

    fun openReview(gameId: String) {
        reviewedId.value = gameId
    }

    /** Analyses [gameId] in the background unless it is already running or finished. */
    fun analyse(gameId: String, again: Boolean = false) {
        val engine = engine ?: return
        if (running[gameId]?.isActive == true) return
        mutableJobs.update { it + (gameId to AnalysisJob()) }
        running[gameId] = viewModelScope.launch {
            try {
                engineLock.withLock {
                    val game = games.first { !it.loading }.game(gameId) ?: return@withLock
                    if (again) analysis.delete(gameId)
                    GameAnalyzer(engine, analysis, clock = clock).analyse(game) { done, total ->
                        mutableJobs.update { it + (gameId to AnalysisJob(done, total, waiting = false)) }
                    }
                }
                mutableJobs.update { it - gameId }
            } catch (e: CancellationException) {
                mutableJobs.update { it - gameId }
                throw e
            } catch (e: Exception) {
                mutableJobs.update { it + (gameId to AnalysisJob(waiting = false, error = "The engine stopped answering. Try again.")) }
            } finally {
                running.remove(gameId)
            }
        }
    }

    /** Stops [gameId]'s analysis; what is done so far stays saved, so it resumes later. */
    fun stopAnalysis(gameId: String) {
        running.remove(gameId)?.cancel()
        mutableJobs.update { it - gameId }
    }

    /**
     * "Practise this mistake" (plan 6.4): the position before [move] goes into the mistakes
     * review, due now, with the engine's move as the answer.
     */
    fun practise(game: GameSummary, move: ReviewedMove, onSaved: () -> Unit = {}) {
        val store = mistakeReviews ?: return
        viewModelScope.launch {
            store.upsert(
                ReviewItem(
                    id = "game_${game.id}_${move.ply}",
                    fen = move.fenBefore,
                    dueAt = Instant.ofEpochMilli(clock()),
                    mistakeUci = move.uci,
                    bestMoveUci = move.bestMove,
                    explanation = "You played ${move.san} here against ${game.opponent}. ${move.bestSan ?: "The engine's move"} was stronger.",
                    sourceDescription = "Your game against ${game.opponent}",
                    acceptedMovesUci = listOf(move.bestMove),
                ),
            )
            onSaved()
        }
    }

    fun deleteGame(id: String) {
        stopAnalysis(id)
        viewModelScope.launch {
            repository.deleteGame(id)
            analysis.delete(id)
        }
    }

    private fun summarise(records: List<GameRecord>, openings: OpeningTree): List<GameSummary> = synchronized(cache) {
        val fresh = records.map { record ->
            cache[record.id]?.takeIf { it.record == record } ?: GameLibrary.summarise(record, openings)
        }
        cache.clear()
        fresh.associateByTo(cache) { it.id }
        fresh.sortedByDescending { it.dateMillis }
    }

    class Factory(
        private val repository: GameRepository,
        private val loadOpenings: suspend () -> OpeningTree,
        private val puzzleStats: Flow<PuzzleStats>,
        private val analysis: GameAnalysisRepository,
        private val engine: EngineService,
        private val reviews: ReviewRepository,
        private val trainingSources: TrainingSources,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AccountViewModel(repository, loadOpenings, puzzleStats, analysis, engine, reviews, trainingSources) as T
    }
}
