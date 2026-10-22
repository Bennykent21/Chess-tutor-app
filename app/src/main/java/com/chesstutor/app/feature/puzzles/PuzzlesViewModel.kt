package com.chesstutor.app.feature.puzzles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.domain.puzzle.LevelProgress
import com.chesstutor.app.domain.puzzle.PuzzleLevels
import com.chesstutor.app.domain.puzzle.PuzzlePack
import com.chesstutor.app.domain.puzzle.PuzzleRepository
import com.chesstutor.app.domain.puzzle.PuzzleStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the Puzzles home draws (plan section 6.3.1). */
data class PuzzlesHomeState(
    val loading: Boolean = true,
    /** False when the bundled pack is missing or empty. */
    val packAvailable: Boolean = true,
    val stats: PuzzleStats = PuzzleStats(),
    val levels: Map<Int, LevelProgress> = emptyMap(),
) {
    val passed: Set<Int> get() = levels.filterValues { it.passed }.keys
    val currentLevel: Int get() = PuzzleLevels.current(passed)
}

/**
 * The Puzzles tab's view model (plan section 7.3): loads the pack once and follows the
 * player's stats and level path from [PuzzleRepository]. Shared by the home screen and the
 * puzzle player.
 */
class PuzzlesViewModel(
    private val repository: PuzzleRepository,
    private val loadPack: suspend () -> PuzzlePack,
) : ViewModel() {
    private val pack = MutableStateFlow<PuzzlePack?>(null)

    val home: StateFlow<PuzzlesHomeState> =
        combine(pack, repository.stats(), repository.levelProgress()) { p, stats, levels ->
            PuzzlesHomeState(loading = p == null, packAvailable = p?.isEmpty != true, stats = stats, levels = levels)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PuzzlesHomeState())

    init {
        viewModelScope.launch {
            // Copies the old progress in on first use, so stats show the player's real rating.
            repository.loadProgress(seedRating = PuzzleLevels.FIRST_RATING)
            pack.value = loadPack()
        }
    }

    /** The loaded pack, or null while it is still loading. */
    val loadedPack: PuzzlePack? get() = pack.value

    class Factory(
        private val repository: PuzzleRepository,
        private val loadPack: suspend () -> PuzzlePack,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PuzzlesViewModel(repository, loadPack) as T
    }
}
