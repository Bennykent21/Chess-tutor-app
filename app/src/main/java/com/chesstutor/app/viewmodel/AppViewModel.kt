package com.chesstutor.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.LearningProfile
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.chesstutor.app.data.repository.GameRepository
import com.chesstutor.app.data.repository.InMemoryGameRepository
import com.chesstutor.app.data.repository.InMemoryRatingRepository
import com.chesstutor.app.data.repository.LearningRepository
import com.chesstutor.app.data.repository.RatingRepository
import com.chesstutor.app.data.repository.ReviewRepository
import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.domain.LearnTopic
import com.chesstutor.app.domain.MoveAssessment
import com.chesstutor.app.domain.MoveChoice
import com.chesstutor.app.domain.ReviewItem
import com.chesstutor.app.domain.SearchConfidence
import com.chesstutor.app.domain.TrainDrillsRepository
import com.chesstutor.app.domain.VerifiedConsequence
import com.chesstutor.app.engine.BlunderClassifier
import com.chesstutor.app.engine.ChessEngineManager
import com.chesstutor.app.engine.EngineClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.time.Instant
import java.util.UUID

class AppViewModel(
    private val repository: ReviewRepository,
    private val engine: EngineClient,
    private val ratingRepository: RatingRepository = InMemoryRatingRepository(),
    private val learningRepository: LearningRepository = com.chesstutor.app.data.repository.InMemoryLearningRepository(),
    private val chessEngineManager: ChessEngineManager = ChessEngineManager(engine),
    private val gameRepository: GameRepository = InMemoryGameRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val blunderClassifier = BlunderClassifier(thresholdCentipawns = 150)
    private val learningProfileMutex = Mutex()

    private val placementAssessmentCoordinator = PlacementAssessmentCoordinator()
    private val ratingLinkCoordinator = RatingLinkCoordinator(ratingRepository, viewModelScope)
    private val spacedReviewCoordinator = SpacedReviewCoordinator(
        repository = repository,
        scope = viewModelScope,
        startEvaluation = chessEngineManager::startEvaluation
    )
    private val curriculumCoordinator = CurriculumCoordinator(
        learningRepository = learningRepository,
        scope = viewModelScope,
        profileMutex = learningProfileMutex,
        startEvaluation = chessEngineManager::startEvaluation
    )
    private val arenaGameManager = ArenaGameManager(
        chessEngineManager = chessEngineManager,
        reviewRepository = repository,
        gameRepository = gameRepository,
        blunderClassifier = blunderClassifier,
        scope = viewModelScope
    )

    companion object {
        const val FEN_MATE_IN_ONE = "r1bqkb1r/pppp1ppp/2n5/4p3/2B1n3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 4"
        const val FEN_BACK_RANK_MATE = "6k1/5ppp/8/8/8/8/4QPPP/6K1 w - - 0 1"
        const val FEN_HANGING_PIECE = "r1bqk2r/pppp1ppp/2n5/4p3/1b2P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 6"
        const val FEN_FORK_TACTIC = "r1b1k2r/pppp1ppp/5q2/4n3/2B1P3/8/PPP2PPP/RNBQK2R w KQkq - 0 7"
    }

    init {
        viewModelScope.launch {
            runCatching { chessEngineManager.initialize() }
            applyBotElo()
            loadReviews()
            loadLearningState()
            selectDrill(0)
            observeLinkedProfile()
        }
        viewModelScope.launch {
            chessEngineManager.currentEvaluation.collect { eval ->
                if (eval != null) {
                    _state.update { current ->
                        current.copy(
                            evaluationCp = eval.centipawns,
                            mateIn = eval.mateInMoves
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            learningRepository.observeModuleProgress().collect { progress ->
                _state.update { current ->
                    current.copy(
                        practicedModules = progress.filter { it.practiced }.map { it.moduleId }.toSet(),
                        masteredModules = progress.filter { it.mastered }.map { it.moduleId }.toSet()
                    )
                }
            }
        }
        viewModelScope.launch {
            gameRepository.observeGames().collect { games ->
                _state.update { current ->
                    current.copy(recentGames = games)
                }
            }
        }
    }

    private fun observeLinkedProfile() {
        viewModelScope.launch {
            ratingRepository.getLinkedProfileFlow().collect { profile ->
                _state.update { current ->
                    current.copy(
                        linkedProfile = profile,
                        useLinkedRatingForBot = profile?.activeRating != null
                    )
                }
                applyBotElo()
            }
        }
    }

    private fun applyBotElo() {
        val elo = _state.value.effectiveBotElo
        viewModelScope.launch {
            chessEngineManager.setSkillLevel(elo)
        }
    }

    fun toggleAutoOpponent() {
        _state.update { it.copy(isAutoOpponentEnabled = !it.isAutoOpponentEnabled) }
    }

    suspend fun selectCalibratedBotMove(fen: String, elo: Int, botDifficulty: String): MoveChoice? {
        return chessEngineManager.calculateBotMove(fen, elo, botDifficulty)
    }

    private fun triggerOpponentResponseInCoach(afterFen: String) {
        if (!_state.value.isAutoOpponentEnabled) return
        val pos = ChessPosition(afterFen)
        if (pos.isOver) return

        _state.update { it.copy(busy = true, opponentThinking = true) }
        viewModelScope.launch {
            delay(400)
            val opponentElo = _state.value.effectiveBotElo
            val opponentMove = selectCalibratedBotMove(afterFen, opponentElo, _state.value.arenaDifficulty)
            if (opponentMove != null) {
                val nextPos = ChessPosition(afterFen)
                nextPos.play(opponentMove)
                _state.update {
                    it.copy(
                        fen = nextPos.fen,
                        lastMove = Pair(opponentMove.from, opponentMove.to),
                        busy = false,
                        opponentThinking = false,
                        message = "Opponent responded with ${opponentMove.san}."
                    )
                }
                chessEngineManager.startEvaluation(nextPos.fen)
            } else {
                _state.update { it.copy(busy = false, opponentThinking = false) }
            }
        }
    }

    fun triggerOpponentMoveNow() {
        val currentFen = _state.value.fen
        val pos = ChessPosition(currentFen)
        if (pos.isOver || _state.value.busy) return
        triggerOpponentResponseInCoach(currentFen)
    }

    fun selectTab(tabIndex: Int) {
        _state.update {
            it.copy(
                tab = tabIndex,
                selectedSquare = null,
                legalTargets = emptySet(),
                recommendedArrow = null
            )
        }
        if (tabIndex == 3) {
            viewModelScope.launch { loadReviews() }
        }
    }

    suspend fun loadReviews() {
        spacedReviewCoordinator.loadReviews(_state::update)
    }

    private suspend fun loadLearningState() {
        runCatching {
            val profile = learningRepository.getProfile()
            _state.update {
                it.copy(
                    estimatedRating = profile.estimatedRating,
                    assessmentState = profile.assessmentState,
                    assessmentPositionIndex = profile.assessmentPositionIndex,
                    assessmentCorrect = profile.assessmentCorrect,
                    assessmentTotal = profile.assessmentTotal,
                    assessmentCompletedAt = profile.assessmentCompletedAt,
                    learningGoal = profile.learningGoal,
                    tacticalAttempts = profile.totalTacticalAttempts,
                    tacticalCorrect = profile.totalTacticalCorrect,
                    isSoundEnabled = profile.soundEnabled
                )
            }
        }.onFailure { ex ->
            _state.update { it.copy(storageWarning = "Learning state storage note: ${ex.message}") }
        }
    }

    private fun persistProfile(update: (LearningProfile) -> LearningProfile) {
        curriculumCoordinator.persistProfile(update)
    }

    private fun recordTacticalAttempt(correct: Boolean) {
        curriculumCoordinator.recordTacticalAttempt(correct, _state::update)
    }

    private fun recordCurriculumAttempt(correct: Boolean) {
        curriculumCoordinator.recordCurriculumAttempt(_state.value.curriculumLessonId, correct, _state::update)
    }

    fun startPlacementAssessment() {
        placementAssessmentCoordinator.start(curriculumCoordinator::persistProfile, _state::update)
    }

    fun answerPlacementAssessment(move: MoveChoice) {
        placementAssessmentCoordinator.answer(move, _state.value, curriculumCoordinator::persistProfile, _state::update)
    }

    fun resetPlacementAssessment() {
        startPlacementAssessment()
    }

    fun loadCoachPosition(
        fen: String,
        title: String = "Forced Mate & Consequence Retry",
        subtitle: String = "Every mistake is backed by a concrete, checkable fact.",
        category: String = "TACTICAL COACHING",
        recommendedMoveUci: String? = null
    ) {
        val pos = ChessPosition(fen)
        val mates = pos.matesInOne
        _state.update {
            it.copy(
                fen = fen,
                activeCoachTitle = title,
                activeCoachSubtitle = subtitle,
                activeCoachCategory = category,
                activeCoachRecommendedMove = recommendedMoveUci,
                message = if (mates.isNotEmpty()) "Find the concrete forced mate in 1 move!" else "Evaluate the position and play the best move.",
                hintLevel = 0,
                hintText = "",
                mistakeDetected = false,
                mistakeFen = fen,
                canRetryMistake = false,
                assessment = null,
                selectedSquare = null,
                legalTargets = emptySet(),
                recommendedArrow = recommendedMoveUci?.let { uci ->
                    if (uci.length >= 4) Pair(uci.substring(0, 2), uci.substring(2, 4)) else null
                },
                lastMove = null
            )
        }
        chessEngineManager.startEvaluation(fen)
    }

    fun onSquareTapped(square: String) {
        val currentState = _state.value
        if (currentState.busy || currentState.pendingPromotion != null) return

        val currentSelected = currentState.selectedSquare
        val pos = ChessPosition(currentState.fen)

        if (currentSelected == null) {
            val matchingMoves = pos.legalMoves.filter { it.from == square }
            if (matchingMoves.isNotEmpty()) {
                _state.update {
                    it.copy(
                        selectedSquare = square,
                        legalTargets = matchingMoves.map { m -> m.to }.toSet()
                    )
                }
            }
        } else {
            if (square in currentState.legalTargets) {
                val matchingMoves = pos.legalMoves.filter {
                    it.from == currentSelected && it.to == square
                }
                if (matchingMoves.size > 1 && matchingMoves.all { it.promotion != null }) {
                    _state.update {
                        it.copy(
                            pendingPromotion = PromotionRequest(
                                from = currentSelected,
                                to = square,
                                choices = matchingMoves.mapNotNull { it.promotion?.lowercaseChar() }.distinct()
                            )
                        )
                    }
                    return
                }

                val move = matchingMoves.firstOrNull()
                if (move != null) {
                    playSelectedMove(move)
                }
                _state.update { it.copy(selectedSquare = null, legalTargets = emptySet()) }
            } else {
                val matchingMoves = pos.legalMoves.filter { it.from == square }
                if (matchingMoves.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            selectedSquare = square,
                            legalTargets = matchingMoves.map { m -> m.to }.toSet()
                        )
                    }
                } else {
                    _state.update { it.copy(selectedSquare = null, legalTargets = emptySet()) }
                }
            }
        }
    }

    private fun playSelectedMove(move: MoveChoice) {
        if (_state.value.assessmentState == "IN_PROGRESS") {
            answerPlacementAssessment(move)
            return
        }
        when (_state.value.tab) {
            0 -> playCoachMove(move)
            1 -> playCurriculumMove(move)
            2 -> playArenaMove(move)
            3 -> playReviewMove(move)
        }
    }

    fun choosePromotion(piece: Char) {
        val request = _state.value.pendingPromotion ?: return
        val choice = piece.lowercaseChar()
        if (choice !in request.choices) return

        val pos = ChessPosition(_state.value.fen)
        val move = pos.legalMoves.firstOrNull {
            it.from == request.from &&
                it.to == request.to &&
                it.promotion?.lowercaseChar() == choice
        } ?: return

        _state.update {
            it.copy(
                pendingPromotion = null,
                selectedSquare = null,
                legalTargets = emptySet()
            )
        }
        playSelectedMove(move)
    }

    fun cancelPromotion() {
        _state.update {
            it.copy(
                pendingPromotion = null,
                selectedSquare = null,
                legalTargets = emptySet()
            )
        }
    }

    private fun playCoachMove(move: MoveChoice) {
        val beforeFen = _state.value.fen
        val beforePos = ChessPosition(beforeFen)
        val matesBefore = beforePos.matesInOne

        val afterPos = ChessPosition(beforeFen)
        val played = afterPos.play(move)
        if (!played) return

        val afterFen = afterPos.fen
        val isMatePlayed = afterPos.isCheckmate
        chessEngineManager.startEvaluation(afterFen)

        if (matesBefore.isNotEmpty()) {
            if (isMatePlayed) {
                recordTacticalAttempt(correct = true)
                recordCurriculumAttempt(correct = true)
                _state.update {
                    it.copy(
                        fen = afterFen,
                        message = "Checkmate! Verified: Forced mate in 1 delivered successfully.",
                        mistakeDetected = false,
                        canRetryMistake = false,
                        lastMove = Pair(move.from, move.to),
                        recommendedArrow = null,
                        assessment = MoveAssessment(
                            evaluationBeforeCp = 10000,
                            evaluationAfterCp = 10000,
                            mateInMovesBefore = 1,
                            mateInMovesAfter = 0,
                            verifiedConsequences = emptyList(),
                            confidence = SearchConfidence(depth = 1, nodes = 1),
                            coachingLabel = "Checkmate! Decisive forced victory."
                        )
                    )
                }
            } else {
                val bestMateMove = matesBefore.first()
                val assessment = MoveAssessment(
                    evaluationBeforeCp = 10000,
                    evaluationAfterCp = 0,
                    mateInMovesBefore = 1,
                    mateInMovesAfter = null,
                    verifiedConsequences = listOf(VerifiedConsequence.MISSED_FORCED_MATE),
                    confidence = SearchConfidence(depth = 1, nodes = 1),
                    coachingLabel = "Missed Forced Mate: You had checkmate in 1 on the board."
                )

                recordTacticalAttempt(correct = false)
                recordCurriculumAttempt(correct = false)
                _state.update {
                    it.copy(
                        fen = afterFen,
                        message = "Mistake detected! Verified: Missed forced checkmate in 1.",
                        mistakeDetected = true,
                        mistakeFen = beforeFen,
                        canRetryMistake = true,
                        lastMove = Pair(move.from, move.to),
                        assessment = assessment
                    )
                }

                viewModelScope.launch {
                    val reviewItem = ReviewItem(
                        id = UUID.randomUUID().toString(),
                        fen = beforeFen,
                        dueAt = Instant.now(),
                        stage = -1,
                        attempts = 0,
                        mistakeUci = move.uci,
                        bestMoveUci = bestMateMove.uci,
                        explanation = "Missed forced mate in 1: ${bestMateMove.san} delivered checkmate."
                    )
                    repository.upsert(reviewItem)
                    loadReviews()
                }
            }
        } else {
            val rec = _state.value.activeCoachRecommendedMove
            if (rec != null && rec.length >= 4) {
                if (move.uci == rec || isMatePlayed) {
                    recordCurriculumAttempt(correct = true)
                    _state.update {
                        it.copy(
                            fen = afterFen,
                            message = "Correct! Well done.",
                            mistakeDetected = false,
                            canRetryMistake = false,
                            lastMove = Pair(move.from, move.to),
                            recommendedArrow = null
                        )
                    }
                } else {
                    recordCurriculumAttempt(correct = false)
                    _state.update {
                        it.copy(
                            fen = afterFen,
                            message = "Incorrect move. Try again!",
                            mistakeDetected = true,
                            mistakeFen = beforeFen,
                            canRetryMistake = true,
                            lastMove = Pair(move.from, move.to)
                        )
                    }
                }
            } else {
                _state.update {
                    it.copy(
                        fen = afterFen,
                        message = "Move ${move.san} played.",
                        lastMove = Pair(move.from, move.to)
                    )
                }
                if (!afterPos.isOver) {
                    triggerOpponentResponseInCoach(afterFen)
                } else {
                    _state.update {
                        it.copy(
                            message = if (afterPos.isCheckmate) "Checkmate! Game Over." else "Draw! Game Over."
                        )
                    }
                }
            }
        }
    }

    fun retryMistake() {
        val mistakeFen = _state.value.mistakeFen ?: return
        loadCoachPosition(mistakeFen)
        _state.update {
            it.copy(
                message = "Position reset. Find the winning move!",
                mistakeDetected = false,
                canRetryMistake = false
            )
        }
    }

    fun setDrillSheetVisible(visible: Boolean) {
        _state.update { it.copy(isDrillSheetVisible = visible) }
    }

    fun selectDrill(index: Int) {
        val drills = TrainDrillsRepository.drills
        if (index in drills.indices) {
            val drill = drills[index]
            _state.update {
                it.copy(
                    currentDrillIndex = index,
                    fen = drill.fen,
                    activeCoachTitle = drill.title,
                    activeCoachSubtitle = "Drill ${index + 1} of ${drills.size}",
                    activeCoachCategory = drill.category,
                    activeCoachRecommendedMove = drill.solutionUci,
                    message = drill.prompt,
                    hintLevel = 0,
                    hintText = "",
                    mistakeDetected = false,
                    mistakeFen = drill.fen,
                    canRetryMistake = false,
                    assessment = null,
                    selectedSquare = null,
                    legalTargets = emptySet(),
                    recommendedArrow = null,
                    lastMove = null,
                    isDrillSheetVisible = false
                )
            }
            chessEngineManager.startEvaluation(drill.fen)
        }
    }

    fun nextDrill() {
        val drills = TrainDrillsRepository.drills
        val nextIdx = (_state.value.currentDrillIndex + 1) % drills.size
        selectDrill(nextIdx)
    }

    fun showHint() {
        val currentLevel = _state.value.hintLevel
        val nextLevel = if (currentLevel >= 4) 0 else currentLevel + 1

        val pos = ChessPosition(_state.value.fen)
        val targetUci = _state.value.activeCoachRecommendedMove
        val targetMove = if (targetUci != null && targetUci.length >= 4) {
            pos.legalMoves.firstOrNull { it.uci == targetUci }
        } else null
        val bestMove = targetMove ?: pos.matesInOne.firstOrNull() ?: pos.legalMoves.firstOrNull()

        if (bestMove == null) return

        when (nextLevel) {
            0 -> {
                _state.update {
                    it.copy(
                        hintLevel = 0,
                        hintText = "",
                        selectedSquare = null,
                        legalTargets = emptySet(),
                        recommendedArrow = null
                    )
                }
            }
            1 -> {
                _state.update {
                    it.copy(
                        hintLevel = 1,
                        hintText = "Look for a forcing move: checks, captures, or threats.",
                        selectedSquare = null,
                        legalTargets = emptySet(),
                        recommendedArrow = null
                    )
                }
            }
            2 -> {
                _state.update {
                    it.copy(
                        hintLevel = 2,
                        hintText = "Start with the ${bestMove.from} piece.",
                        selectedSquare = bestMove.from,
                        legalTargets = emptySet(),
                        recommendedArrow = null
                    )
                }
            }
            3 -> {
                _state.update {
                    it.copy(
                        hintLevel = 3,
                        hintText = "The key piece should move to ${bestMove.to}.",
                        selectedSquare = bestMove.from,
                        legalTargets = setOf(bestMove.to),
                        recommendedArrow = null
                    )
                }
            }
            4 -> {
                _state.update {
                    it.copy(
                        hintLevel = 4,
                        hintText = "The move is ${bestMove.san}.",
                        selectedSquare = bestMove.from,
                        legalTargets = setOf(bestMove.to),
                        recommendedArrow = Pair(bestMove.from, bestMove.to)
                    )
                }
            }
        }
    }

    private fun playArenaMove(move: MoveChoice) {
        arenaGameManager.playArenaMove(move, _state.value, { viewModelScope.launch { loadReviews() } }, _state::update)
    }

    fun linkRatingAccount(
        platform: RatingPlatform,
        username: String,
        timeControl: RatingTimeControl = RatingTimeControl.RAPID
    ) {
        ratingLinkCoordinator.linkAccount(platform, username, timeControl, ::applyBotElo, _state::update)
    }

    fun refreshLinkedRating() {
        ratingLinkCoordinator.refreshProfile(_state.value.linkedProfile?.username, ::applyBotElo, _state::update)
    }

    fun setRatingTimeControl(timeControl: RatingTimeControl) {
        ratingLinkCoordinator.setRatingTimeControl(timeControl, ::applyBotElo)
    }

    fun setUseLinkedRatingForBot(useLinked: Boolean) {
        _state.update { it.copy(useLinkedRatingForBot = useLinked) }
        applyBotElo()
    }

    fun unlinkRatingAccount() {
        ratingLinkCoordinator.unlinkAccount(::applyBotElo, _state::update)
    }

    fun clearLinkingStatus() {
        _state.update { it.copy(linkingError = null, linkingSuccessMessage = null) }
    }

    fun selectReviewItem(index: Int) {
        spacedReviewCoordinator.selectReviewItem(index, _state.value.reviews, _state::update)
    }

    private fun playReviewMove(move: MoveChoice) {
        spacedReviewCoordinator.playReviewMove(move, _state.value.activeReviewItem, _state.value.hintLevel, _state::update)
    }

    fun setCurriculumTab(tab: Int) {
        _state.update { it.copy(curriculumTab = tab) }
    }

    fun practiceCurriculumLesson(
        lessonId: String,
        fen: String,
        title: String,
        category: String = "VERIFIABLE CURRICULUM",
        description: String = "Spot the key tactical motif and find the winning move.",
        recommendedMoveUci: String? = null
    ) {
        viewModelScope.launch { learningRepository.markPracticed(lessonId) }
        _state.update {
            it.copy(
                tab = 0,
                curriculumLessonId = lessonId,
                practicedModules = it.practicedModules + lessonId,
                activeCoachTitle = title,
                activeCoachSubtitle = description,
                activeCoachCategory = category,
                activeCoachRecommendedMove = recommendedMoveUci
            )
        }
        loadCoachPosition(
            fen = fen,
            title = title,
            subtitle = description,
            category = category,
            recommendedMoveUci = recommendedMoveUci
        )
        _state.update { it.copy(message = "$title: Find the winning line!") }
    }

    fun exploreLearnTopic(topic: LearnTopic) {
        curriculumCoordinator.exploreLearnTopic(topic, _state::update)
    }

    fun loadLearnTopic(topic: LearnTopic) {
        exploreLearnTopic(topic)
    }

    fun playActivePrincipleMove() {
        curriculumCoordinator.playActivePrincipleMove(
            _state.value.activeCoachRecommendedMove,
            _state.value.fen,
            _state::update
        )
    }

    fun setSoundEnabled(enabled: Boolean) {
        _state.update { it.copy(isSoundEnabled = enabled) }
        persistProfile { it.copy(soundEnabled = enabled) }
    }

    fun setSettingsVisible(visible: Boolean) {
        _state.update { it.copy(isSettingsVisible = visible) }
    }

    fun resetCurriculumProgress() {
        curriculumCoordinator.resetProgress(_state::update)
    }

    fun markModuleMastered(lessonId: String) {
        viewModelScope.launch { learningRepository.markMastered(lessonId) }
        _state.update {
            it.copy(
                masteredModules = it.masteredModules + lessonId,
                practicedModules = it.practicedModules + lessonId
            )
        }
    }

    fun setArenaOpponentSheetVisible(visible: Boolean) {
        _state.update { it.copy(isArenaOpponentSheetVisible = visible) }
    }

    fun setEngineDiagnosticsDialogVisible(visible: Boolean) {
        _state.update { it.copy(isEngineDiagnosticsDialogVisible = visible) }
    }

    fun loadSampleMistakeForReview() {
        spacedReviewCoordinator.loadSampleMistake(FEN_FORK_TACTIC, _state::update)
    }

    private fun playCurriculumMove(move: MoveChoice) {
        playCoachMove(move)
    }

    fun setArenaDifficulty(difficulty: String) {
        _state.update { it.copy(arenaDifficulty = difficulty, useLinkedRatingForBot = false) }
        applyBotElo()
    }

    fun setArenaBot(tierKey: String, name: String, elo: Int) {
        _state.update {
            it.copy(
                arenaDifficulty = tierKey,
                arenaBotName = name,
                customBotElo = elo,
                useLinkedRatingForBot = false
            )
        }
        applyBotElo()
    }

    fun startNewArenaGame() {
        arenaGameManager.startNewGame(_state.value.arenaBotName, _state::update)
    }

    fun showAnalysisArrow(from: String, to: String) {
        _state.update { it.copy(recommendedArrow = Pair(from, to)) }
    }

    fun practiceLesson(topic: LearnTopic) {
        exploreLearnTopic(topic)
    }

    fun showReviewAnswer(item: ReviewItem) {
        if (item.bestMoveUci.length >= 4) {
            val from = item.bestMoveUci.take(2)
            val to = item.bestMoveUci.substring(2, 4)
            _state.update {
                it.copy(
                    recommendedArrow = Pair(from, to),
                    message = "Best move: ${item.bestMoveUci}. ${item.explanation}"
                )
            }
        }
    }

    fun onReviewSquareTapped(square: String, item: ReviewItem) {
        if (_state.value.activeReviewItem?.id != item.id) {
            val index = _state.value.reviews.indexOfFirst { it.id == item.id }
            if (index < 0) return
            selectReviewItem(index)
        }
        onSquareTapped(square)
    }

    fun resetArenaGame() {
        startNewArenaGame()
    }

    fun setGameHistorySheetVisible(visible: Boolean) {
        _state.update { it.copy(isGameHistorySheetOpen = visible) }
    }

    fun selectGameForPgn(game: GameRecord?) {
        _state.update { it.copy(selectedGameForPgn = game) }
    }

    fun deleteSavedGame(gameId: String) {
        viewModelScope.launch {
            gameRepository.deleteGame(gameId)
        }
    }

    fun runEngineDiagnostics() {
        if (_state.value.isRunningDiagnostics) return
        _state.update { it.copy(isRunningDiagnostics = true) }
        viewModelScope.launch {
            val diag = chessEngineManager.runDiagnostics(movetimeMs = 1000)
            android.util.Log.i("StockfishDiagnostics", "Engine diagnostics: $diag")
            _state.update {
                it.copy(
                    isRunningDiagnostics = false,
                    engineDiagnostics = diag
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            runCatching { chessEngineManager.dispose() }
        }
    }
}
