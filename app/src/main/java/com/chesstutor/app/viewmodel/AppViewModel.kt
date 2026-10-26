package com.chesstutor.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.audio.ChessSoundManager
import com.chesstutor.app.board.BoardColors
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.PieceSet
import com.chesstutor.app.data.model.GameSource
import com.chesstutor.app.data.model.ImportDepth
import com.chesstutor.app.data.model.LearningProfile
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.chesstutor.app.data.repository.GameRepository
import com.chesstutor.app.data.repository.InMemoryGameRepository
import com.chesstutor.app.data.repository.InMemoryLearningRepository
import com.chesstutor.app.data.repository.InMemoryRatingRepository
import com.chesstutor.app.data.repository.LearningRepository
import com.chesstutor.app.data.repository.RatingRepository
import com.chesstutor.app.data.repository.ReviewRepository
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.data.settings.BoardOrientation
import com.chesstutor.app.data.settings.InMemorySettingsStore
import com.chesstutor.app.data.settings.ReviewDepth
import com.chesstutor.app.data.settings.SettingsStore
import com.chesstutor.app.domain.Mascot
import com.chesstutor.app.domain.bot.ChatLevel
import com.chesstutor.app.engine.ChessEngineManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The app-wide view model (plan 7.1): what every tab shares. That is the player's profile
 * (Home's welcome card), settings, the linked Chess.com or Lichess account and its imports,
 * the mistakes due for review, and the engine's health. Each tab's screens have their own
 * feature view models (Home, Learn, Puzzles, Account, Mistakes review).
 *
 * The engine manager is the app's single shared engine (AppContainer), so it is not
 * disposed here: the engine process lives as long as the app process.
 */
class AppViewModel(
    private val reviewRepository: ReviewRepository,
    private val engineManager: ChessEngineManager,
    private val ratingRepository: RatingRepository = InMemoryRatingRepository(),
    private val learningRepository: LearningRepository = InMemoryLearningRepository(),
    private val gameRepository: GameRepository = InMemoryGameRepository(),
    private val soundManager: ChessSoundManager? = null,
    private val settingsStore: SettingsStore = InMemorySettingsStore(),
) : ViewModel() {

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val profileMutex = Mutex()
    private val ratingLinkCoordinator = RatingLinkCoordinator(ratingRepository, viewModelScope, gameRepository)
    private var lastAutoSyncAt = 0L

    companion object {
        /** The automatic sync runs at most this often (when the app opens or Account is shown). */
        const val AUTO_SYNC_INTERVAL_MS = 30 * 60_000L
    }

    init {
        // Starting Stockfish takes a moment; the app must not wait for it before showing Home.
        viewModelScope.launch { runCatching { engineManager.initialize() } }
        viewModelScope.launch {
            loadReviews()
            loadProfile()
            observeLinkedProfile()
        }
        viewModelScope.launch {
            // DataStore is the single source of truth for player settings.
            settingsStore.settings.collect { settings ->
                soundManager?.hapticsEnabled = settings.hapticsEnabled
                _state.update { it.copy(appSettings = settings) }
            }
        }
        viewModelScope.launch {
            engineManager.engineStatus.collect { status -> _state.update { it.copy(engineStatus = status) } }
        }
    }

    private suspend fun loadReviews() {
        val all = runCatching { reviewRepository.loadAll() }.getOrDefault(_state.value.reviews)
        _state.update { it.copy(reviews = all) }
    }

    /** Reloads the mistakes due for review after another screen added one ("Practise this mistake"). */
    fun refreshReviews() {
        viewModelScope.launch { loadReviews() }
    }

    private suspend fun loadProfile() {
        runCatching {
            val profile = learningRepository.getProfile()
            // Sound used to live in the learning profile; copy it over once.
            settingsStore.importLegacyOnce(profile.soundEnabled)
            _state.update {
                it.copy(profileLoaded = true, estimatedRating = profile.estimatedRating, assessmentState = profile.assessmentState)
            }
        }.onFailure { ex ->
            // Never leave the app stuck on the loading background if the profile cannot be read.
            _state.update { it.copy(profileLoaded = true, storageWarning = "Could not read your profile: ${ex.message}") }
        }
    }

    private fun persistProfile(transform: (LearningProfile) -> LearningProfile) {
        viewModelScope.launch {
            profileMutex.withLock {
                val current = learningRepository.getProfile()
                learningRepository.saveProfile(transform(current).copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }

    /**
     * The welcome card on Home (it replaced onboarding, D30): the player says roughly how
     * strong they are, which sets the starting rating until a linked account gives a real one.
     */
    fun chooseStartingLevel(rating: Int) {
        val completedAt = System.currentTimeMillis()
        persistProfile { it.copy(estimatedRating = rating, assessmentState = "COMPLETE", assessmentCompletedAt = completedAt) }
        _state.update { it.copy(estimatedRating = rating, assessmentState = "COMPLETE") }
    }

    private fun observeLinkedProfile() {
        viewModelScope.launch {
            var first = true
            ratingRepository.getLinkedProfileFlow().collect { profile ->
                _state.update { it.copy(linkedProfile = profile) }
                // Sync once when the app opens with an account linked.
                if (first && profile != null) autoSyncLinkedAccount()
                first = false
            }
        }
    }

    /**
     * Brings in games played since the newest imported one, at most every
     * [AUTO_SYNC_INTERVAL_MS], so the player never has to press "Sync" (review of Phase 5).
     */
    fun autoSyncLinkedAccount(now: Long = System.currentTimeMillis()) {
        val current = _state.value
        val profile = current.linkedProfile ?: return
        if (current.isLinkingLoading || now - lastAutoSyncAt < AUTO_SYNC_INTERVAL_MS) return
        lastAutoSyncAt = now
        viewModelScope.launch {
            // Read the saved games and settings themselves: at start-up the screen state may not have them yet.
            val source = if (profile.platform == RatingPlatform.LICHESS) GameSource.LICHESS else GameSource.CHESS_COM
            val mine = gameRepository.observeGames().first().filter { it.source == source }
            val newest = mine.maxOfOrNull { it.dateMillis }
            // An import the app was closed during carries on from the oldest game it reached.
            val target = settingsStore.settings.first().importTarget
            val oldest = mine.minOfOrNull { it.dateMillis }
            val backfill = if (target != null && oldest != null && mine.size < target.maxGames) {
                RatingLinkCoordinator.Backfill(oldest, target.maxGames - mine.size) { complete -> if (complete) setImportTarget(null) }
            } else {
                if (target != null && mine.size >= target.maxGames) setImportTarget(null)
                null
            }
            ratingLinkCoordinator.autoSync(newest?.plus(1), _state::update, backfill)
        }
    }

    fun setImportDepth(depth: ImportDepth) {
        _state.update { it.copy(selectedImportDepth = depth) }
    }

    fun cancelAccountImport() {
        ratingLinkCoordinator.cancelImport(_state::update)
        setImportTarget(null)
    }

    /** Remembers how many games the player asked for, so an import cut short carries on later. */
    private fun setImportTarget(target: ImportDepth?) = updateSettings { it.copy(importTarget = target) }

    fun linkRatingAccount(
        platform: RatingPlatform,
        username: String,
        timeControl: RatingTimeControl = RatingTimeControl.RAPID,
        importDepth: ImportDepth = _state.value.selectedImportDepth,
    ) {
        setImportTarget(importDepth)
        ratingLinkCoordinator.linkAccount(platform, username, timeControl, _state::update, importDepth) { complete ->
            if (complete) setImportTarget(null)
        }
    }

    fun refreshLinkedRating(importDepth: ImportDepth = _state.value.selectedImportDepth) {
        setImportTarget(importDepth)
        ratingLinkCoordinator.refreshProfile(_state.value.linkedProfile?.username, _state::update, importDepth) { complete ->
            if (complete) setImportTarget(null)
        }
    }

    fun setRatingTimeControl(timeControl: RatingTimeControl) {
        ratingLinkCoordinator.setRatingTimeControl(timeControl)
    }

    fun unlinkRatingAccount() {
        ratingLinkCoordinator.unlinkAccount(_state::update)
    }

    fun clearLinkingStatus() {
        _state.update { it.copy(linkingError = null, linkingSuccessMessage = null) }
    }

    fun runEngineSelfTest() {
        if (_state.value.isRunningEngineSelfTest) return
        _state.update { it.copy(isRunningEngineSelfTest = true, engineSelfTestReport = "Running UCI self-test...") }
        viewModelScope.launch {
            val report = engineManager.runSelfTest()
            _state.update { it.copy(isRunningEngineSelfTest = false, engineSelfTestReport = report) }
        }
    }

    fun setSoundEnabled(enabled: Boolean) = updateSettings { it.copy(soundEnabled = enabled) }

    fun setShowCoordinates(enabled: Boolean) = updateSettings { it.copy(showCoordinates = enabled) }

    fun setShowLegalDots(enabled: Boolean) = updateSettings { it.copy(showLegalDots = enabled) }

    fun setHapticsEnabled(enabled: Boolean) = updateSettings { it.copy(hapticsEnabled = enabled) }

    fun setReduceMotion(enabled: Boolean) = updateSettings { it.copy(reduceMotion = enabled) }

    fun setChatLevel(level: ChatLevel) = updateSettings { it.copy(chatLevel = level) }

    fun setAutoQueen(enabled: Boolean) = updateSettings { it.copy(autoQueen = enabled) }

    fun setMascot(mascot: Mascot) = updateSettings { it.copy(mascotId = mascot.id) }

    fun setReviewDepth(depth: ReviewDepth) = updateSettings { it.copy(reviewDepth = depth) }

    fun setOnlineEngine(enabled: Boolean) = updateSettings { it.copy(onlineEngine = enabled) }

    fun setBoardColors(colors: BoardColors) = updateSettings { it.copy(boardThemeId = colors.id) }

    fun setPieceSet(set: PieceSet) = updateSettings { it.copy(pieceSetId = set.id) }

    fun setBoardOrientation(orientation: BoardOrientation) = updateSettings { it.copy(boardOrientation = orientation) }

    /** Plays a board event's sound and vibration, following the sound and haptics settings. */
    fun playBoardFeedback(feedback: BoardFeedback) {
        soundManager?.play(feedback, _state.value.appSettings.soundEnabled)
    }

    /** Saves a settings change. The state follows through the settings flow collected in init. */
    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        // Update the screen at once; the store confirms the same value a moment later.
        _state.update { it.copy(appSettings = transform(it.appSettings)) }
        viewModelScope.launch {
            runCatching { settingsStore.update(transform) }.onFailure { ex ->
                _state.update { it.copy(storageWarning = "Could not save settings: ${ex.message}") }
            }
        }
    }
}

class AppViewModelFactory(
    private val reviewRepository: ReviewRepository,
    private val engineManager: ChessEngineManager,
    private val ratingRepository: RatingRepository,
    private val learningRepository: LearningRepository,
    private val gameRepository: GameRepository,
    private val soundManager: ChessSoundManager?,
    private val settingsStore: SettingsStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AppViewModel::class.java)) { "Unknown ViewModel class: ${modelClass.name}" }
        return AppViewModel(reviewRepository, engineManager, ratingRepository, learningRepository, gameRepository, soundManager, settingsStore) as T
    }
}
