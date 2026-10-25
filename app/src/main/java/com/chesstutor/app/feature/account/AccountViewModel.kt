package com.chesstutor.app.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.repository.GameRepository
import com.chesstutor.app.domain.account.GameLibrary
import com.chesstutor.app.domain.account.GameSummary
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.domain.puzzle.PuzzleStats
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The player's saved games, newest first, with openings named (plan section 6.4). */
data class GamesState(
    val loading: Boolean = true,
    val games: List<GameSummary> = emptyList(),
) {
    fun game(id: String): GameSummary? = games.firstOrNull { it.id == id }
}

/**
 * The Account tab's view model (plan section 7.3), shared by every screen in the tab: the
 * games list and game pages now, analysis and the dashboards as Phase 5 adds them.
 */
class AccountViewModel(
    private val repository: GameRepository,
    loadOpenings: suspend () -> OpeningTree,
    puzzleStats: Flow<PuzzleStats>,
    worker: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val tree = MutableStateFlow<OpeningTree?>(null)

    /** Summaries by game, so an import that adds games does not re-name every old one. */
    private val cache = HashMap<String, GameSummary>()

    val games: StateFlow<GamesState> = combine(repository.observeGames(), tree.filterNotNull()) { records, openings ->
        GamesState(loading = false, games = summarise(records, openings))
    }.flowOn(worker).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GamesState())

    val stats: StateFlow<PuzzleStats> = puzzleStats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PuzzleStats())

    init {
        viewModelScope.launch { tree.value = runCatching { loadOpenings() }.getOrDefault(OpeningTree.EMPTY) }
    }

    fun deleteGame(id: String) {
        viewModelScope.launch { repository.deleteGame(id) }
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AccountViewModel(repository, loadOpenings, puzzleStats) as T
    }
}
