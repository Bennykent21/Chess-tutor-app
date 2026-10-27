package com.chesstutor.app.feature.puzzles

import com.chesstutor.app.domain.learn.Course
import com.chesstutor.app.domain.learn.PracticeRepository
import com.chesstutor.app.domain.learn.PracticeSets
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.domain.puzzle.Difficulty
import com.chesstutor.app.domain.puzzle.LevelProgress
import com.chesstutor.app.domain.puzzle.PuzzleGoal
import com.chesstutor.app.domain.puzzle.PuzzleLevels
import com.chesstutor.app.domain.puzzle.PuzzleMode
import com.chesstutor.app.domain.puzzle.PuzzleModes
import com.chesstutor.app.domain.puzzle.PuzzlePack
import com.chesstutor.app.domain.puzzle.PuzzleRepository
import com.chesstutor.app.domain.puzzle.PuzzleStats
import com.chesstutor.app.domain.puzzle.PuzzleThemes
import com.chesstutor.app.domain.puzzle.RatingBand
import com.chesstutor.app.domain.puzzle.RushSelector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

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
    /** Where practice-set scores go (plan 6C). */
    private val practice: PracticeRepository? = null,
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

    private val mutablePlayer = MutableStateFlow<PuzzlePlayer?>(null)

    /** The run being played, if any (plan section 6.3.3). */
    val player: StateFlow<PuzzlePlayer?> = mutablePlayer.asStateFlow()

    /**
     * Starts a play of [level]: five puzzles near its rating, new ones on each play, puzzles
     * already seen avoided while enough remain.
     */
    fun startLevel(level: Int) {
        closePlayer()
        viewModelScope.launch {
            val loaded = pack.filterNotNull().first()
            val seen = repository.loadProgress(seedRating = PuzzleLevels.FIRST_RATING).seenIds
            val plays = home.value.levels[level]?.plays ?: 0
            val puzzles = PuzzleLevels.puzzlesFor(level, plays, loaded, avoid = seen)
            val spec = PuzzleRunSpec(
                mode = PuzzleMode.LEVEL,
                title = "Level $level",
                level = level,
                plannedCount = puzzles.size,
                nextPuzzle = { index, _ -> puzzles.getOrNull(index) },
            )
            mutablePlayer.value = PuzzlePlayer(spec, repository, viewModelScope).also { it.start() }
        }
    }

    /** Timed rush: [minutes] on the clock, or Survival (three strikes) when null. */
    fun startRush(minutes: Int?) = startRun { pack ->
        val rush = RushSelector(pack, RushSelector.startFor(home.value.stats.rating), seed = System.currentTimeMillis())
        PuzzleRunSpec(
            mode = PuzzleMode.RUSH,
            title = if (minutes != null) "Rush · $minutes min" else "Survival",
            timeLimitMs = minutes?.let { it * 60_000L },
            maxStrikes = if (minutes == null) 3 else null,
            oneTry = true,
            nextPuzzle = { _, solved -> rush.next(solved) },
        )
    }

    /** Today's puzzle, the same for everyone. */
    fun startDaily() = startRun { pack ->
        val puzzle = PuzzleModes.daily(pack, LocalDate.now())
        PuzzleRunSpec(PuzzleMode.DAILY, "Daily puzzle", plannedCount = 1, nextPuzzle = { i, _ -> puzzle.takeIf { i == 0 } })
    }

    fun startThemed(goal: PuzzleGoal, pattern: String?, difficulty: Difficulty) = startRun { pack ->
        val seen = repository.loadProgress(PuzzleLevels.FIRST_RATING).seenIds
        val rating = home.value.stats.rating + difficulty.offset
        val puzzles = PuzzleModes.themed(pack, goal, pattern, rating, THEMED_COUNT, System.currentTimeMillis(), seen)
        val title = pattern?.let(PuzzleThemes::displayName) ?: goal.title
        PuzzleRunSpec(PuzzleMode.THEME, title, plannedCount = puzzles.size, nextPuzzle = { i, _ -> puzzles.getOrNull(i) })
    }

    /** A lesson's "Related puzzles": puzzles with any of [themes], near the player's rating. */
    fun startRelated(themes: List<String>) = startRun { pack ->
        val seen = repository.loadProgress(PuzzleLevels.FIRST_RATING).seenIds
        val puzzles = PuzzleModes.related(pack, themes, home.value.stats.rating, THEMED_COUNT, System.currentTimeMillis(), seen)
        val title = themes.joinToString(" and ") { PuzzleThemes.displayName(it) }
        PuzzleRunSpec(PuzzleMode.THEME, title, plannedCount = puzzles.size, nextPuzzle = { i, _ -> puzzles.getOrNull(i) })
    }

    /** A course's practice set (plan 6C): its fixed puzzles; the score is kept as the course's best. */
    fun startPractice(course: Course) = startRun { pack ->
        val puzzles = PracticeSets.puzzlesFor(course, pack)
        PuzzleRunSpec(
            mode = PuzzleMode.THEME,
            title = "${course.title}: practice set",
            plannedCount = puzzles.size,
            nextPuzzle = { i, _ -> puzzles.getOrNull(i) },
            onFinished = { solved, total -> practice?.recordPractice(course.id, solved, total, System.currentTimeMillis()) },
        )
    }

    fun startCustom(band: RatingBand, pattern: String?, count: Int, timed: Boolean) = startRun { pack ->
        val seen = repository.loadProgress(PuzzleLevels.FIRST_RATING).seenIds
        val puzzles = PuzzleModes.custom(pack, band, pattern, count, System.currentTimeMillis(), seen)
        PuzzleRunSpec(
            mode = PuzzleMode.CUSTOM,
            title = "Custom",
            plannedCount = puzzles.size,
            // "Timer on": a minute a puzzle for the whole run.
            timeLimitMs = if (timed) puzzles.size * 60_000L else null,
            nextPuzzle = { i, _ -> puzzles.getOrNull(i) },
        )
    }

    /** Puzzles whose last attempt was not clean, most recent first. */
    fun startMistakes() = startRun { pack ->
        val puzzles = PuzzleModes.mistakes(pack, repository.mistakePuzzleIds())
        PuzzleRunSpec(PuzzleMode.REVIEW, "Mistakes review", plannedCount = puzzles.size, nextPuzzle = { i, _ -> puzzles.getOrNull(i) })
    }

    private val patternCache = HashMap<PuzzleGoal, List<String>>()

    /** Pattern tags to offer for [goal] (scanned once per goal); empty until the pack has loaded. */
    fun patternsFor(goal: PuzzleGoal): List<String> {
        val loaded = pack.value ?: return emptyList()
        return patternCache.getOrPut(goal) { PuzzleModes.patternsFor(goal, loaded) }
    }

    private fun startRun(build: suspend (PuzzlePack) -> PuzzleRunSpec) {
        closePlayer()
        viewModelScope.launch {
            val spec = build(pack.filterNotNull().first())
            mutablePlayer.value = PuzzlePlayer(spec, repository, viewModelScope).also { it.start() }
        }
    }

    fun closePlayer() {
        mutablePlayer.value?.close()
        mutablePlayer.value = null
    }

    override fun onCleared() {
        closePlayer()
    }

    private companion object {
        const val THEMED_COUNT = 10
    }

    class Factory(
        private val repository: PuzzleRepository,
        private val loadPack: suspend () -> PuzzlePack,
        private val practice: PracticeRepository? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PuzzlesViewModel(repository, loadPack, practice) as T
    }
}
