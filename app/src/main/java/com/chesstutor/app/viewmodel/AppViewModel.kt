package com.chesstutor.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.ImportDepth
import com.chesstutor.app.data.model.LearningProfile
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.chesstutor.app.data.repository.GameRepository
import com.chesstutor.app.data.repository.InMemoryGameRepository
import com.chesstutor.app.data.repository.InMemoryRatingRepository
import com.chesstutor.app.data.repository.LearningRepository
import com.chesstutor.app.data.repository.RatingRepository
import com.chesstutor.app.data.repository.ReviewRepository
import com.chesstutor.app.domain.AdaptiveTrainingPlanner
import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.domain.LearnCurriculumRepository
import com.chesstutor.app.domain.LearnTopic
import com.chesstutor.app.domain.LessonSessionFactory
import com.chesstutor.app.domain.MoveAssessment
import com.chesstutor.app.domain.MoveChoice
import com.chesstutor.app.domain.OpeningBook
import com.chesstutor.app.domain.ReviewItem
import com.chesstutor.app.domain.SearchConfidence
import com.chesstutor.app.domain.SessionStepKind
import com.chesstutor.app.domain.TrainDrillsRepository
import com.chesstutor.app.domain.VerifiedConsequence
import com.chesstutor.app.engine.BlunderClassifier
import com.chesstutor.app.engine.GameAnalysisService
import com.chesstutor.app.engine.ChessEngineManager
import com.chesstutor.app.engine.EngineClient
import com.chesstutor.app.navigation.AppNavigator
import com.chesstutor.app.navigation.NavCommand
import com.chesstutor.app.navigation.OpeningMode
import com.chesstutor.app.navigation.PlayRequest
import com.chesstutor.app.navigation.Side
import com.chesstutor.app.navigation.TrainRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID

class AppViewModel(
    private val repository: ReviewRepository,
    private val engine: EngineClient,
    private val ratingRepository: RatingRepository = InMemoryRatingRepository(),
    private val learningRepository: LearningRepository = com.chesstutor.app.data.repository.InMemoryLearningRepository(),
    private val chessEngineManager: ChessEngineManager = ChessEngineManager(engine),
    private val gameRepository: GameRepository = InMemoryGameRepository(),
    private val soundManager: com.chesstutor.app.audio.ChessSoundManager? = null,
    val navigator: AppNavigator = AppNavigator()
) : ViewModel() {

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val blunderClassifier = BlunderClassifier(thresholdCentipawns = 150)
    private val gameAnalysisService = GameAnalysisService(engine, blunderClassifier)
    private val learningProfileMutex = Mutex()

    private val placementAssessmentCoordinator = PlacementAssessmentCoordinator()
    private val ratingLinkCoordinator = RatingLinkCoordinator(ratingRepository, viewModelScope, gameRepository)
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
        learningRepository = learningRepository,
        scope = viewModelScope,
        soundManager = soundManager,
        analysisEngineClient = engine
    )

    companion object {
        const val FEN_MATE_IN_ONE = "r1bqkb1r/pppp1ppp/2n5/4p3/2B1n3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 4"
        const val FEN_BACK_RANK_MATE = "6k1/5ppp/8/8/8/8/4QPPP/6K1 w - - 0 1"
        const val FEN_HANGING_PIECE = "r1bqk2r/pppp1ppp/2n5/4p3/1b2P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 6"
        const val FEN_FORK_TACTIC = "r1b1k2r/pppp1ppp/5q2/4n3/2B1P3/8/PPP2PPP/RNBQK2R w KQkq - 0 7"

        fun isSolutionLegalInFen(fen: String, uci: String?): Boolean {
            if (uci.isNullOrBlank()) return false
            val pos = runCatching { ChessPosition(fen) }.getOrNull() ?: return false
            return pos.legalMoves.any { it.uci.equals(uci.trim(), ignoreCase = true) }
        }
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
                val profile = learningRepository.getProfile()
                val recommendation = AdaptiveTrainingPlanner.recommend(profile, progress)
                _state.update { current ->
                    current.copy(
                        practicedModules = progress.filter { it.practiced }.map { it.moduleId }.toSet(),
                        masteredModules = progress.filter { it.mastered }.map { it.moduleId }.toSet(),
                        trainingRecommendation = recommendation.topic.title,
                        trainingRecommendationReason = recommendation.reason
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
        viewModelScope.launch {
            navigator.commands.collect { cmd ->
                handleNavCommand(cmd)
            }
        }
    }

    fun navigate(command: NavCommand) {
        if (!navigator.navigate(command)) {
            handleNavCommand(command)
        }
    }

    fun startTrain(request: TrainRequest) {
        navigate(NavCommand.StartTrain(request))
    }

    fun startPlay(request: PlayRequest) {
        navigate(NavCommand.StartPlay(request))
    }

    private fun handleNavCommand(command: NavCommand) {
        when (command) {
            is NavCommand.OpenLearn -> {
                val topic = command.topicId?.let { tid ->
                    LearnCurriculumRepository.topics.firstOrNull { it.id == tid }
                } ?: LearnCurriculumRepository.topics.first()
                _state.update {
                    it.copy(
                        tab = 1,
                        selectedLearnTopicId = topic.id
                    )
                }
            }
            is NavCommand.OpenReview -> {
                _state.update { it.copy(tab = 3) }
            }
            is NavCommand.StartTrain -> {
                when (val req = command.request) {
                    is TrainRequest.Lesson -> {
                        val topic = LearnCurriculumRepository.topics.firstOrNull { it.id == req.topicId }
                            ?: LearnCurriculumRepository.topics.first()
                        curriculumCoordinator.exploreLearnTopic(topic, _state::update)
                    }
                    is TrainRequest.Theme -> {
                        val drills = TrainDrillsRepository.drills
                        val idx = drills.indexOfFirst {
                            it.category.contains(req.theme, ignoreCase = true) ||
                                it.title.contains(req.theme, ignoreCase = true)
                        }.takeIf { it >= 0 } ?: 0
                        _state.update { it.copy(tab = 0) }
                        selectDrill(idx)
                    }
                    is TrainRequest.ReviewDue -> {
                        val dueIdx = _state.value.reviews.indexOfFirst {
                            com.chesstutor.app.domain.ReviewScheduler.isDue(it, Instant.now())
                        }.coerceAtLeast(0)
                        if (_state.value.reviews.isNotEmpty()) {
                            _state.update { it.copy(tab = 3) }
                            selectReviewItem(dueIdx)
                        } else {
                            _state.update { it.copy(tab = 0) }
                            selectDrill(0)
                        }
                    }
                    is TrainRequest.Daily -> {
                        _state.update { it.copy(tab = 0) }
                        selectDrill(0)
                    }
                    is TrainRequest.FromGame -> {
                        val game = _state.value.recentGames.firstOrNull { it.id == req.gameId }
                        val pos = ChessPosition()
                        if (game != null && game.uciMoves.isNotBlank()) {
                            val uciList = game.uciMoves.split(" ").filter { it.isNotBlank() }
                            for (u in uciList.take(req.ply.coerceAtLeast(0))) {
                                if (!pos.play(u)) break
                            }
                        }
                        _state.update { it.copy(tab = 0) }
                        loadCoachPosition(
                            fen = pos.fen,
                            title = "Game Position Drill",
                            subtitle = "From your game vs ${game?.botName ?: "Opponent"}",
                            category = "GAME REVIEW DRILL"
                        )
                    }
                    is TrainRequest.Opening -> {
                        val line = OpeningBook.byId(req.lineId) ?: OpeningBook.lines.first()
                        val topic = line.relatedTopicId?.let { tid ->
                            LearnCurriculumRepository.topics.firstOrNull { it.id == tid }
                        }
                        if (topic != null) {
                            curriculumCoordinator.exploreLearnTopic(topic, _state::update)
                        } else {
                            _state.update { it.copy(tab = 0) }
                            loadCoachPosition(
                                fen = ChessPosition.STARTING_FEN,
                                title = line.name,
                                subtitle = "${line.eco} · ${line.formattedMoveLine}",
                                category = "OPENING DRILL",
                                recommendedMoveUci = line.uciMoves.firstOrNull()
                            )
                        }
                    }
                }
            }
            is NavCommand.StartPlay -> {
                when (val req = command.request) {
                    is PlayRequest.Free -> {
                        _state.update { it.copy(tab = 2) }
                        arenaGameManager.startNewGame(
                            botName = _state.value.arenaBotName,
                            updateState = _state::update,
                            openingMode = OpeningMode.FREE,
                            openingLineId = null
                        )
                    }
                    is PlayRequest.Opening -> {
                        val sideChar = req.userSide.resolveChar()
                        _state.update { it.copy(tab = 2) }
                        arenaGameManager.startNewGame(
                            botName = _state.value.arenaBotName,
                            updateState = _state::update,
                            playerSide = sideChar,
                            openingMode = req.mode,
                            openingLineId = req.lineId
                        )
                    }
                    is PlayRequest.FromPosition -> {
                        val sideChar = req.userSide.resolveChar()
                        _state.update {
                            it.copy(
                                tab = 2,
                                fen = req.fen,
                                arenaPlayerSide = sideChar,
                                moveHistory = emptyList(),
                                arenaUciHistory = emptyList(),
                                message = "Playing from custom position."
                            )
                        }
                        chessEngineManager.startEvaluation(req.fen)
                    }
                    is PlayRequest.Rematch -> {
                        val sideChar = req.bot.userSide.resolveChar()
                        setArenaBot(req.bot.tierKey, req.bot.botName, req.bot.elo)
                        _state.update { it.copy(tab = 2) }
                        arenaGameManager.startNewGame(
                            botName = req.bot.botName,
                            updateState = _state::update,
                            playerSide = sideChar
                        )
                    }
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
                soundManager?.playMove(_state.value.isSoundEnabled, isCapture = opponentMove.isCapture, isCheck = nextPos.isCheck)
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
        if (tabIndex == 2 && !_state.value.arenaInitialized) {
            _state.update {
                it.copy(
                    tab = 2,
                    selectedSquare = null,
                    legalTargets = emptySet()
                )
            }
            arenaGameManager.startNewGame(_state.value.arenaBotName, _state::update)
            return
        }

        _state.update { current ->
            when (tabIndex) {
                0 -> current.copy(
                    tab = 0,
                    fen = current.trainFen,
                    message = current.trainMessage,
                    lastMove = current.trainLastMove,
                    recommendedArrow = current.trainRecommendedArrow,
                    selectedSquare = null,
                    legalTargets = emptySet()
                )
                2 -> current.copy(
                    tab = 2,
                    fen = current.arenaFen,
                    message = current.arenaMessage,
                    lastMove = current.arenaLastMove,
                    recommendedArrow = current.arenaRecommendedArrow,
                    selectedSquare = null,
                    legalTargets = emptySet()
                )
                3 -> {
                    val activeRev = current.activeReviewItem
                    current.copy(
                        tab = 3,
                        fen = if (current.isReviewMistakeSolverOpen && activeRev != null) activeRev.fen else current.fen,
                        message = if (current.isReviewMistakeSolverOpen && activeRev != null) {
                            "Spaced Repetition Review: Find the best move for this position!"
                        } else "",
                        selectedSquare = null,
                        legalTargets = emptySet(),
                        recommendedArrow = null
                    )
                }
                else -> current.copy(
                    tab = tabIndex,
                    selectedSquare = null,
                    legalTargets = emptySet(),
                    recommendedArrow = null
                )
            }
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
            val progress = learningRepository.getModuleProgress()
            val recommendation = AdaptiveTrainingPlanner.recommend(profile, progress)
            val assessmentFen = if (profile.assessmentState == "IN_PROGRESS") {
                com.chesstutor.app.data.model.PlacementAssessment.questions
                    .getOrNull(profile.assessmentPositionIndex)
                    ?.fen
            } else null
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
                    isSoundEnabled = profile.soundEnabled,
                    trainingRecommendation = recommendation.topic.title,
                    trainingRecommendationReason = recommendation.reason,
                    fen = assessmentFen ?: it.fen
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

    private fun recordCurriculumAttempt(correct: Boolean, markCompleted: Boolean = false) {
        curriculumCoordinator.recordCurriculumAttempt(
            lessonId = _state.value.curriculumLessonId,
            correct = correct,
            updateState = _state::update,
            onRefreshRecommendation = ::refreshTrainingRecommendation,
            markCompleted = markCompleted
        )
    }

    // Refreshes the next lesson after persisted learning-profile changes.
    private fun refreshTrainingRecommendation() {
        viewModelScope.launch {
            val profile = learningRepository.getProfile()
            val progress = learningRepository.getModuleProgress()
            val recommendation = AdaptiveTrainingPlanner.recommend(profile, progress)
            _state.update {
                it.copy(
                    trainingRecommendation = recommendation.topic.title,
                    trainingRecommendationReason = recommendation.reason
                )
            }
        }
    }

    fun setLearningGoal(goal: String) {
        _state.update { it.copy(learningGoal = goal) }
        viewModelScope.launch {
            learningProfileMutex.withLock {
                val current = learningRepository.getProfile()
                learningRepository.saveProfile(current.copy(
                    learningGoal = goal,
                    updatedAt = System.currentTimeMillis()
                ))
            }
            refreshTrainingRecommendation()
        }
    }

    fun practiceRecommendedTraining() {
        viewModelScope.launch {
            val profile = learningRepository.getProfile()
            val progress = learningRepository.getModuleProgress()
            val recommendation = AdaptiveTrainingPlanner.recommend(profile, progress)
            curriculumCoordinator.exploreLearnTopic(recommendation.topic, _state::update)
        }
    }

    fun startPlacementAssessment() {
        placementAssessmentCoordinator.start(curriculumCoordinator::persistProfile, _state::update)
    }

    fun answerPlacementAssessment(move: MoveChoice) {
        placementAssessmentCoordinator.answer(move, _state.value, curriculumCoordinator::persistProfile, _state::update)
        if (_state.value.assessmentState == "COMPLETE") {
            refreshTrainingRecommendation()
        }
    }

    fun resetPlacementAssessment() {
        startPlacementAssessment()
    }

    fun skipPlacementAsBeginner() {
        placementAssessmentCoordinator.skipAsBeginner(curriculumCoordinator::persistProfile, _state::update)
        refreshTrainingRecommendation()
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
        val promptMsg = if (mates.isNotEmpty()) {
            "Find the concrete forced mate in 1 move!"
        } else {
            "Evaluate the position and play the best move."
        }
        _state.update {
            it.copy(
                fen = fen,
                trainFen = fen,
                activeLessonSession = null,
                puzzlePhase = PuzzlePhase.SOLVING,
                activeCoachTitle = title,
                activeCoachSubtitle = subtitle,
                activeCoachCategory = category,
                activeCoachRecommendedMove = recommendedMoveUci,
                message = promptMsg,
                trainMessage = promptMsg,
                hintLevel = 0,
                hintText = "",
                mistakeDetected = false,
                mistakeFen = fen,
                canRetryMistake = false,
                assessment = null,
                selectedSquare = null,
                legalTargets = emptySet(),
                recommendedArrow = null,
                trainRecommendedArrow = null,
                lastMove = null,
                trainLastMove = null
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
        soundManager?.playMove(_state.value.isSoundEnabled, isCapture = move.isCapture, isCheck = afterPos.isCheck)
        chessEngineManager.startEvaluation(afterFen)

        val activeSession = _state.value.activeLessonSession
        if (activeSession != null) {
            val step = activeSession.currentStep
            val expectedUci = step.solutionUci.trim().lowercase()
            val isCorrect = move.uci.equals(expectedUci, ignoreCase = true) ||
                step.allSolutionsUci.any { it.equals(move.uci, ignoreCase = true) } ||
                isMatePlayed

            if (isCorrect) {
                soundManager?.playSuccess(_state.value.isSoundEnabled)
                recordTacticalAttempt(correct = true)
                val firstTry = activeSession.currentStepFailedAttempts == 0 && !activeSession.currentStepHintUsed
                val isInteractive = step.kind == SessionStepKind.GUIDED_TRY || step.kind == SessionStepKind.PRACTICE
                val updatedSession = activeSession.copy(
                    interactiveAttempts = activeSession.interactiveAttempts + if (isInteractive) 1 else 0,
                    interactiveFirstTryCorrect = activeSession.interactiveFirstTryCorrect + if (isInteractive && firstTry) 1 else 0
                )
                val successMsg = step.whyItWorks.ifBlank {
                    if (isMatePlayed) "Checkmate! Well done." else "Strong move! Principle demonstrated."
                }
                _state.update {
                    it.copy(
                        fen = afterFen,
                        trainFen = afterFen,
                        activeLessonSession = updatedSession,
                        puzzlePhase = PuzzlePhase.CORRECT,
                        message = successMsg,
                        trainMessage = successMsg,
                        mistakeDetected = false,
                        canRetryMistake = false,
                        lastMove = Pair(move.from, move.to),
                        trainLastMove = Pair(move.from, move.to),
                        recommendedArrow = null,
                        trainRecommendedArrow = null
                    )
                }
            } else {
                soundManager?.playBlunder(_state.value.isSoundEnabled)
                recordTacticalAttempt(correct = false)
                val specificFeedback = LessonSessionFactory.buildSpecificWrongMoveFeedback(
                    fen = beforeFen,
                    playedUci = move.uci,
                    solutionUci = expectedUci,
                    conceptHint = step.conceptHint
                )
                val updatedSession = activeSession.copy(
                    currentStepFailedAttempts = activeSession.currentStepFailedAttempts + 1
                )
                _state.update {
                    it.copy(
                        fen = afterFen,
                        trainFen = afterFen,
                        activeLessonSession = updatedSession,
                        puzzlePhase = PuzzlePhase.WRONG,
                        message = specificFeedback,
                        trainMessage = specificFeedback,
                        mistakeDetected = true,
                        mistakeFen = beforeFen,
                        canRetryMistake = true,
                        lastMove = Pair(move.from, move.to),
                        trainLastMove = Pair(move.from, move.to)
                    )
                }
            }
            return
        }

        if (matesBefore.isNotEmpty()) {
            if (isMatePlayed) {
                soundManager?.playSuccess(_state.value.isSoundEnabled)
                recordTacticalAttempt(correct = true)
                recordCurriculumAttempt(correct = true)
                val msg = "Checkmate! Verified: Forced mate in 1 delivered successfully."
                _state.update {
                    it.copy(
                        fen = afterFen,
                        trainFen = afterFen,
                        puzzlePhase = PuzzlePhase.CORRECT,
                        message = msg,
                        trainMessage = msg,
                        mistakeDetected = false,
                        canRetryMistake = false,
                        lastMove = Pair(move.from, move.to),
                        trainLastMove = Pair(move.from, move.to),
                        recommendedArrow = null,
                        trainRecommendedArrow = null,
                        assessment = MoveAssessment(
                            evaluationBeforeCp = 10000,
                            evaluationAfterCp = 10000,
                            mateInMovesBefore = 1,
                            mateInMovesAfter = 1,
                            verifiedConsequences = emptyList(),
                            confidence = SearchConfidence(depth = 1, nodes = 1),
                            coachingLabel = "Checkmate! Decisive forced victory."
                        )
                    )
                }
            } else {
                soundManager?.playBlunder(_state.value.isSoundEnabled)
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
                val msg = "Mistake detected! Verified: Missed forced checkmate in 1 (${move.san} does not mate)."
                _state.update {
                    it.copy(
                        fen = afterFen,
                        trainFen = afterFen,
                        puzzlePhase = PuzzlePhase.WRONG,
                        message = msg,
                        trainMessage = msg,
                        mistakeDetected = true,
                        mistakeFen = beforeFen,
                        canRetryMistake = true,
                        lastMove = Pair(move.from, move.to),
                        trainLastMove = Pair(move.from, move.to),
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
                    soundManager?.playSuccess(_state.value.isSoundEnabled)
                    recordTacticalAttempt(correct = true)
                    recordCurriculumAttempt(correct = true)
                    val msg = if (isMatePlayed) "Checkmate! Well done." else "Strong move! Principle demonstrated."
                    _state.update {
                        it.copy(
                            fen = afterFen,
                            trainFen = afterFen,
                            puzzlePhase = PuzzlePhase.CORRECT,
                            message = msg,
                            trainMessage = msg,
                            mistakeDetected = false,
                            canRetryMistake = false,
                            lastMove = Pair(move.from, move.to),
                            trainLastMove = Pair(move.from, move.to),
                            recommendedArrow = null,
                            trainRecommendedArrow = null
                        )
                    }
                } else {
                    soundManager?.playBlunder(_state.value.isSoundEnabled)
                    recordTacticalAttempt(correct = false)
                    recordCurriculumAttempt(correct = false)
                    val specificMsg = LessonSessionFactory.buildSpecificWrongMoveFeedback(
                        fen = beforeFen,
                        playedUci = move.uci,
                        solutionUci = rec,
                        conceptHint = _state.value.activeCoachCategory
                    )
                    _state.update {
                        it.copy(
                            fen = afterFen,
                            trainFen = afterFen,
                            puzzlePhase = PuzzlePhase.WRONG,
                            message = specificMsg,
                            trainMessage = specificMsg,
                            mistakeDetected = true,
                            mistakeFen = beforeFen,
                            canRetryMistake = true,
                            lastMove = Pair(move.from, move.to),
                            trainLastMove = Pair(move.from, move.to)
                        )
                    }
                }
            } else {
                val msg = "Move ${move.san} played."
                _state.update {
                    it.copy(
                        fen = afterFen,
                        trainFen = afterFen,
                        puzzlePhase = if (afterPos.isCheckmate) PuzzlePhase.CORRECT else PuzzlePhase.SOLVING,
                        message = msg,
                        trainMessage = msg,
                        lastMove = Pair(move.from, move.to),
                        trainLastMove = Pair(move.from, move.to)
                    )
                }
                if (!afterPos.isOver) {
                    triggerOpponentResponseInCoach(afterFen)
                } else {
                    val endMsg = if (afterPos.isCheckmate) "Checkmate! Game Over." else "Draw! Game Over."
                    _state.update {
                        it.copy(
                            message = endMsg,
                            trainMessage = endMsg
                        )
                    }
                }
            }
        }
    }

    /**
     * Advances to the next step in the active [com.chesstutor.app.domain.LessonTrainSession] (S4).
     * Never falls into the global 12-drill list! When reaching the Summary step, marks the
     * lesson as completed in [LearningRepository].
     */
    fun advanceLessonStep() {
        val session = _state.value.activeLessonSession ?: return
        val nextIdx = (session.currentStepIndex + 1).coerceAtMost(session.steps.lastIndex)
        val nextSession = session.copy(
            currentStepIndex = nextIdx,
            currentStepFailedAttempts = 0,
            currentStepHintUsed = false
        )
        val step = nextSession.currentStep
        val isSummary = step.kind == SessionStepKind.SUMMARY

        if (isSummary) {
            recordCurriculumAttempt(correct = true, markCompleted = true)
        }

        _state.update {
            it.copy(
                activeLessonSession = nextSession,
                fen = step.fen,
                trainFen = step.fen,
                activeCoachTitle = step.title,
                activeCoachSubtitle = "${session.topicTitle} · Step ${nextIdx + 1} of ${session.totalSteps}",
                activeCoachCategory = "LESSON · STEP ${nextIdx + 1} OF ${session.totalSteps}",
                activeCoachRecommendedMove = step.solutionUci,
                puzzlePhase = if (isSummary) PuzzlePhase.CORRECT else PuzzlePhase.SOLVING,
                message = step.prompt,
                trainMessage = step.prompt,
                hintLevel = 0,
                hintText = "",
                mistakeDetected = false,
                mistakeFen = step.fen,
                canRetryMistake = false,
                selectedSquare = null,
                legalTargets = emptySet(),
                recommendedArrow = null,
                trainRecommendedArrow = null,
                lastMove = null,
                trainLastMove = null
            )
        }
        chessEngineManager.startEvaluation(step.fen)
    }

    fun startNextLessonInCourse() {
        val currentTopicId = _state.value.activeLessonSession?.topicId ?: _state.value.curriculumLessonId
        val topics = LearnCurriculumRepository.topics
        val idx = topics.indexOfFirst { it.id == currentTopicId }
        val nextTopic = if (idx >= 0 && idx + 1 < topics.size) topics[idx + 1] else topics.first()
        curriculumCoordinator.exploreLearnTopic(nextTopic, _state::update)
    }

    fun retryMistake() {
        val mistakeFen = _state.value.mistakeFen ?: return
        val activeSession = _state.value.activeLessonSession
        val prompt = activeSession?.currentStep?.prompt ?: "Position reset. Find the winning move!"
        _state.update {
            it.copy(
                fen = mistakeFen,
                trainFen = mistakeFen,
                puzzlePhase = PuzzlePhase.SOLVING,
                message = prompt,
                trainMessage = prompt,
                mistakeDetected = false,
                canRetryMistake = false,
                selectedSquare = null,
                legalTargets = emptySet(),
                recommendedArrow = null,
                trainRecommendedArrow = null,
                lastMove = null,
                trainLastMove = null
            )
        }
        chessEngineManager.startEvaluation(mistakeFen)
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
                    activeLessonSession = null,
                    curriculumLessonId = null,
                    fen = drill.fen,
                    trainFen = drill.fen,
                    puzzlePhase = PuzzlePhase.SOLVING,
                    activeCoachTitle = drill.title,
                    activeCoachSubtitle = "Drill ${index + 1} of ${drills.size}",
                    activeCoachCategory = drill.category,
                    activeCoachRecommendedMove = drill.solutionUci,
                    message = drill.prompt,
                    trainMessage = drill.prompt,
                    hintLevel = 0,
                    hintText = "",
                    mistakeDetected = false,
                    mistakeFen = drill.fen,
                    canRetryMistake = false,
                    assessment = null,
                    selectedSquare = null,
                    legalTargets = emptySet(),
                    recommendedArrow = null,
                    trainRecommendedArrow = null,
                    lastMove = null,
                    trainLastMove = null,
                    isDrillSheetVisible = false
                )
            }
            chessEngineManager.startEvaluation(drill.fen)
        }
    }

    fun nextDrill() {
        if (_state.value.activeLessonSession != null) {
            advanceLessonStep()
            return
        }
        val drills = TrainDrillsRepository.drills
        val nextIdx = (_state.value.currentDrillIndex + 1) % drills.size
        selectDrill(nextIdx)
    }

    /**
     * S1 Fix: Never invents a hint! Only runs if a verified solution or mate-in-1
     * is legal in the current board position.
     */
    fun showHint() {
        val currentLevel = _state.value.hintLevel
        val nextLevel = if (currentLevel >= 4) 0 else currentLevel + 1

        val pos = runCatching { ChessPosition(_state.value.fen) }.getOrNull() ?: return
        val sessionSolution = _state.value.activeLessonSession?.currentStep?.solutionUci?.takeIf { it.isNotBlank() }
        val reviewSolution = if (_state.value.tab == 3) _state.value.activeReviewItem?.bestMoveUci else null
        val targetUci = reviewSolution ?: sessionSolution ?: _state.value.activeCoachRecommendedMove

        val targetMove = if (targetUci != null && targetUci.length >= 4) {
            pos.legalMoves.firstOrNull { it.uci.equals(targetUci, ignoreCase = true) }
        } else null
        val bestMove = targetMove ?: pos.matesInOne.firstOrNull()

        // Refuse to run if there is no verified legal solution in this FEN (S1)
        if (bestMove == null) return

        val conceptNote = _state.value.activeLessonSession?.currentStep?.conceptHint?.takeIf { it.isNotBlank() }
        val updatedSession = _state.value.activeLessonSession?.let {
            if (nextLevel > 0) it.copy(currentStepHintUsed = true) else it
        }

        when (nextLevel) {
            0 -> {
                _state.update {
                    it.copy(
                        hintLevel = 0,
                        hintText = "",
                        selectedSquare = null,
                        legalTargets = emptySet(),
                        recommendedArrow = null,
                        trainRecommendedArrow = null
                    )
                }
            }
            1 -> {
                val text = if (conceptNote != null) {
                    "Theme: $conceptNote. Look for a forcing check, capture, or threat."
                } else {
                    "Look for a forcing move: checks, captures, or threats."
                }
                _state.update {
                    it.copy(
                        activeLessonSession = updatedSession,
                        hintLevel = 1,
                        hintText = text,
                        selectedSquare = null,
                        legalTargets = emptySet(),
                        recommendedArrow = null,
                        trainRecommendedArrow = null
                    )
                }
            }
            2 -> {
                _state.update {
                    it.copy(
                        activeLessonSession = updatedSession,
                        hintLevel = 2,
                        hintText = "Start with the ${bestMove.from} piece.",
                        selectedSquare = bestMove.from,
                        legalTargets = emptySet(),
                        recommendedArrow = null,
                        trainRecommendedArrow = null
                    )
                }
            }
            3 -> {
                _state.update {
                    it.copy(
                        activeLessonSession = updatedSession,
                        hintLevel = 3,
                        hintText = "The key piece should move to ${bestMove.to}.",
                        selectedSquare = bestMove.from,
                        legalTargets = setOf(bestMove.to),
                        recommendedArrow = null,
                        trainRecommendedArrow = null
                    )
                }
            }
            4 -> {
                val arrow = Pair(bestMove.from, bestMove.to)
                _state.update {
                    it.copy(
                        activeLessonSession = updatedSession,
                        hintLevel = 4,
                        hintText = "The move is ${bestMove.san}.",
                        selectedSquare = bestMove.from,
                        legalTargets = setOf(bestMove.to),
                        recommendedArrow = arrow,
                        trainRecommendedArrow = arrow
                    )
                }
            }
        }
    }

    private fun playArenaMove(move: MoveChoice) {
        arenaGameManager.playArenaMove(move, _state.value, { viewModelScope.launch { loadReviews() } }, _state::update)
    }

    fun showOpeningBookMoveArrow() {
        val uci = _state.value.arenaNextBookMoveUci ?: _state.value.arenaDeviatedBookMoveUci ?: return
        if (uci.length >= 4) {
            val arrow = Pair(uci.take(2), uci.substring(2, 4))
            _state.update {
                it.copy(
                    recommendedArrow = arrow,
                    arenaRecommendedArrow = arrow
                )
            }
        }
    }

    fun retryOpeningBookMove() {
        val bookUci = _state.value.arenaDeviatedBookMoveUci
        takebackArenaMove()
        if (bookUci != null && bookUci.length >= 4) {
            val arrow = Pair(bookUci.take(2), bookUci.substring(2, 4))
            _state.update {
                it.copy(
                    recommendedArrow = arrow,
                    arenaRecommendedArrow = arrow
                )
            }
        }
    }

    fun continueOpeningFromDeviation() {
        _state.update {
            it.copy(
                arenaDeviatedUserMoveSan = null,
                arenaDeviatedBookMoveSan = null,
                arenaDeviatedBookMoveUci = null
            )
        }
    }

    fun switchOpeningToFreePlay() {
        _state.update {
            it.copy(
                selectedOpeningMode = OpeningMode.FREE,
                arenaNextBookMoveUci = null,
                arenaNextBookMoveSan = null,
                arenaDeviatedUserMoveSan = null,
                arenaDeviatedBookMoveSan = null,
                arenaDeviatedBookMoveUci = null
            )
        }
    }

    fun setImportDepth(depth: ImportDepth) {
        _state.update { it.copy(selectedImportDepth = depth) }
    }

    fun cancelAccountImport() {
        ratingLinkCoordinator.cancelImport(_state::update)
    }

    fun linkRatingAccount(
        platform: RatingPlatform,
        username: String,
        timeControl: RatingTimeControl = RatingTimeControl.RAPID,
        importDepth: ImportDepth = _state.value.selectedImportDepth
    ) {
        ratingLinkCoordinator.linkAccount(platform, username, timeControl, ::applyBotElo, _state::update, importDepth)
    }

    fun refreshLinkedRating(importDepth: ImportDepth = _state.value.selectedImportDepth) {
        ratingLinkCoordinator.refreshProfile(_state.value.linkedProfile?.username, ::applyBotElo, _state::update, importDepth)
    }

    fun setReviewMistakeSolverOpen(open: Boolean) {
        _state.update { current ->
            val firstDue = current.reviews.firstOrNull()
            current.copy(
                isReviewMistakeSolverOpen = open,
                activeReviewItem = if (open) (current.activeReviewItem ?: firstDue) else current.activeReviewItem,
                fen = if (open && firstDue != null) (current.activeReviewItem?.fen ?: firstDue.fen) else current.fen
            )
        }
    }

    fun runEngineSelfTest() {
        if (_state.value.isRunningEngineSelfTest) return
        _state.update { it.copy(isRunningEngineSelfTest = true, engineSelfTestReport = "Running UCI self-test...") }
        viewModelScope.launch {
            val report = chessEngineManager.runSelfTest()
            _state.update {
                it.copy(
                    isRunningEngineSelfTest = false,
                    engineSelfTestReport = report
                )
            }
        }
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

    fun setShowCoordinates(enabled: Boolean) {
        _state.update { it.copy(showCoordinates = enabled) }
    }

    fun setShowLegalDots(enabled: Boolean) {
        _state.update { it.copy(showLegalDots = enabled) }
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

    fun setArenaPlayerSide(side: Char) {
        val resolved = if (side == 'r') {
            if (kotlin.random.Random.nextBoolean()) 'w' else 'b'
        } else {
            side.lowercaseChar()
        }
        arenaGameManager.startNewGame(
            botName = _state.value.arenaBotName,
            updateState = _state::update,
            playerSide = resolved
        )
    }

    fun setCoachingLevel(level: CoachingLevel) {
        _state.update { it.copy(coachingLevel = level) }
    }

    fun setOpeningPractice(mode: OpeningMode, lineId: String? = null) {
        arenaGameManager.startNewGame(
            botName = _state.value.arenaBotName,
            updateState = _state::update,
            openingMode = mode,
            openingLineId = lineId
        )
    }

    fun startNewArenaGame() {
        arenaGameManager.startNewGame(_state.value.arenaBotName, _state::update)
    }

    fun resignArenaGame() {
        arenaGameManager.resignGame(_state.value, _state::update)
    }

    fun claimArenaDraw() {
        arenaGameManager.claimDraw(_state.value, _state::update)
    }

    fun takebackArenaMove() {
        arenaGameManager.takebackMove(_state.value, _state::update)
    }

    fun showAnalysisArrow(from: String, to: String) {
        _state.update { it.copy(recommendedArrow = Pair(from, to)) }
    }

    fun practiceLesson(topic: LearnTopic) {
        startTrain(TrainRequest.Lesson(topic.id))
    }

    fun showReviewAnswer(item: ReviewItem) {
        if (item.bestMoveUci.length >= 4) {
            val from = item.bestMoveUci.take(2)
            val to = item.bestMoveUci.substring(2, 4)
            _state.update {
                it.copy(
                    puzzlePhase = PuzzlePhase.REVEALED,
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

    fun analyzeSavedGame(game: GameRecord, depth: Int = 5) {
        if (_state.value.analyzingGameId != null) return
        _state.update { it.copy(analyzingGameId = game.id, gameAnalysis = null) }

        viewModelScope.launch {
            val result = runCatching {
                gameAnalysisService.analyze(game, depth)
            }.getOrElse {
                com.chesstutor.app.engine.GameAnalysisResult(
                    gameId = game.id,
                    analyzedMoves = emptyList(),
                    skippedMoves = game.moveCount.coerceAtLeast(1),
                    status = com.chesstutor.app.engine.GameAnalysisStatus.UNAVAILABLE
                )
            }

            result.userSuboptimalMoves.forEach { move ->
                val moduleId = move.recommendedModuleId ?: return@forEach
                runCatching {
                    learningRepository.recordModuleAttempt(moduleId, correct = false)
                }
            }
            if (result.userSuboptimalMoves.isNotEmpty()) {
                refreshTrainingRecommendation()
            }

            _state.update {
                if (it.analyzingGameId == game.id) {
                    it.copy(
                        analyzingGameId = null,
                        gameAnalysis = result
                    )
                } else {
                    it
                }
            }
        }
    }

    fun clearGameAnalysis() {
        _state.update { it.copy(gameAnalysis = null, analyzingGameId = null) }
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

class AppViewModelFactory(
    private val repository: ReviewRepository,
    private val engine: EngineClient,
    private val ratingRepository: RatingRepository,
    private val learningRepository: LearningRepository,
    private val chessEngineManager: ChessEngineManager,
    private val gameRepository: GameRepository,
    private val soundManager: com.chesstutor.app.audio.ChessSoundManager?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            return AppViewModel(
                repository = repository,
                engine = engine,
                ratingRepository = ratingRepository,
                learningRepository = learningRepository,
                chessEngineManager = chessEngineManager,
                gameRepository = gameRepository,
                soundManager = soundManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
