package com.chesstutor.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.data.model.LinkedChessProfile
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.chesstutor.app.data.repository.InMemoryRatingRepository
import com.chesstutor.app.data.repository.RatingRepository
import com.chesstutor.app.data.repository.ReviewRepository
import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.domain.MoveAssessment
import com.chesstutor.app.domain.MoveChoice
import com.chesstutor.app.domain.ReviewItem
import com.chesstutor.app.domain.ReviewScheduler
import com.chesstutor.app.domain.SearchConfidence
import com.chesstutor.app.domain.VerifiedConsequence
import com.chesstutor.app.engine.AnalysisRequest
import com.chesstutor.app.engine.BlunderClassifier
import com.chesstutor.app.engine.BlunderKind
import com.chesstutor.app.engine.EngineClient
import com.chesstutor.app.engine.StockfishProcessEngineClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class AppViewModel(
    private val repository: ReviewRepository,
    private val engine: EngineClient,
    private val ratingRepository: RatingRepository = InMemoryRatingRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val blunderClassifier = BlunderClassifier(thresholdCentipawns = 150)
    private var analysisCounter = 0

    // Curated tactical positions featuring verifiable mistakes
    companion object {
        const val FEN_MATE_IN_ONE = "r1bqkb1r/pppp1ppp/2n5/4p3/2B1n3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 4"
        const val FEN_BACK_RANK_MATE = "6k1/5ppp/8/8/8/8/4QPPP/6K1 w - - 0 1"
        const val FEN_HANGING_PIECE = "r1bqk2r/pppp1ppp/2n5/4p3/1b2P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 6"
        const val FEN_FORK_TACTIC = "r1b1k2r/pppp1ppp/5q2/4n3/2B1P3/8/PPP2PPP/RNBQK2R w KQkq - 0 7"
    }

    init {
        viewModelScope.launch {
            runCatching { engine.initialize() }
            loadReviews()
            loadCoachPosition(FEN_MATE_IN_ONE)
            observeLinkedProfile()
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
            if (engine is StockfishProcessEngineClient) {
                engine.setElo(elo)
            }
        }
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
        runCatching {
            val items = repository.loadAll()
            _state.update { state ->
                val active = if (items.isNotEmpty()) {
                    val index = state.activeReviewIndex.coerceIn(0, items.lastIndex)
                    items[index]
                } else null
                state.copy(
                    reviews = items,
                    activeReviewItem = active,
                    storageWarning = null
                )
            }
        }.onFailure { ex ->
            _state.update { it.copy(storageWarning = "Local storage note: ${ex.message}") }
        }
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
    }

    fun onSquareTapped(square: String) {
        val currentState = _state.value
        if (currentState.busy) return

        val currentSelected = currentState.selectedSquare
        val pos = ChessPosition(currentState.fen)

        if (currentSelected == null) {
            // Select piece if it belongs to the side to move
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
                // Execute move
                val move = pos.legalMoves.firstOrNull { it.from == currentSelected && it.to == square }
                if (move != null) {
                    when (currentState.tab) {
                        0 -> playCoachMove(move)
                        1 -> playCurriculumMove(move)
                        2 -> playArenaMove(move)
                        3 -> playReviewMove(move)
                    }
                }
                _state.update { it.copy(selectedSquare = null, legalTargets = emptySet()) }
            } else {
                // Switch selection
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

    private fun playCoachMove(move: MoveChoice) {
        val beforeFen = _state.value.fen
        val beforePos = ChessPosition(beforeFen)
        val matesBefore = beforePos.matesInOne

        val afterPos = ChessPosition(beforeFen)
        val played = afterPos.play(move)
        if (!played) return

        val afterFen = afterPos.fen
        val isMatePlayed = afterPos.isCheckmate

        if (matesBefore.isNotEmpty()) {
            // Verifiable category: Missed Mate in One
            if (isMatePlayed) {
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
                // Mistake committed!
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

                // Automatically save mistake to Review Queue
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
            _state.update {
                it.copy(
                    fen = afterFen,
                    message = "Move ${move.san} played.",
                    lastMove = Pair(move.from, move.to)
                )
            }
        }
    }

    fun retryMistake() {
        val mistakeFen = _state.value.mistakeFen ?: return
        loadCoachPosition(mistakeFen)
        _state.update {
            it.copy(
                message = "Guided Retry: The position is reset. Look carefully for the winning move!",
                mistakeDetected = false,
                canRetryMistake = false
            )
        }
    }

    fun showHint() {
        val currentLevel = _state.value.hintLevel
        val nextLevel = (currentLevel + 1).coerceAtMost(4)

        val pos = ChessPosition(_state.value.fen)
        val bestMove = pos.matesInOne.firstOrNull() ?: pos.legalMoves.firstOrNull()

        val hintDescription = when (nextLevel) {
            1 -> "Concept Hint: Look for an unprotected square directly touching the opposing King."
            2 -> "Piece Hint: Focus your attention on the piece on ${bestMove?.from?.uppercase() ?: "the board"}."
            3 -> "Target Hint: Look at the target square ${bestMove?.to?.uppercase() ?: "where the King is vulnerable"}."
            4 -> "Direct Move Hint: Play ${bestMove?.san ?: "the decisive move"}."
            else -> ""
        }

        val arrow = if (nextLevel == 4 && bestMove != null) {
            Pair(bestMove.from, bestMove.to)
        } else null

        _state.update {
            it.copy(
                hintLevel = nextLevel,
                hintText = hintDescription,
                recommendedArrow = arrow
            )
        }
    }

    // Arena: Play vs Stockfish with real-time blunder detection
    private fun playArenaMove(move: MoveChoice) {
        val beforeFen = _state.value.fen
        val beforePos = ChessPosition(beforeFen)
        val afterPos = ChessPosition(beforeFen)
        if (!afterPos.play(move)) return

        val afterFen = afterPos.fen
        _state.update {
            it.copy(
                fen = afterFen,
                lastMove = Pair(move.from, move.to),
                busy = true,
                message = "Move ${move.san} played. Analyzing..."
            )
        }

        viewModelScope.launch {
            val reqId = ++analysisCounter
            val depth = when (_state.value.arenaDifficulty) {
                "Beginner" -> 1
                "Casual" -> 2
                "Intermediate" -> 3
                else -> 4
            }

            val analysisBefore = engine.analyze(AnalysisRequest(reqId, beforeFen, depth = depth))
            val analysisAfter = engine.analyze(AnalysisRequest(reqId + 1, afterFen, depth = depth))

            val verdict = blunderClassifier.classify(analysisBefore, analysisAfter)
            val consequences = mutableListOf<VerifiedConsequence>()

            if (verdict.kind == BlunderKind.MISSED_FORCED_MATE) {
                consequences.add(VerifiedConsequence.MISSED_FORCED_MATE)
            } else if (verdict.kind == BlunderKind.WALKED_INTO_FORCED_MATE) {
                consequences.add(VerifiedConsequence.WALKED_INTO_FORCED_MATE)
            } else if (verdict.kind == BlunderKind.CENTIPAWN_LOSS) {
                consequences.add(VerifiedConsequence.MATERIAL_LOST_BY_FORCE)
            }

            val coachingLabel = when (verdict.kind) {
                BlunderKind.MISSED_FORCED_MATE -> "Verified Fact: Missed forced checkmate!"
                BlunderKind.WALKED_INTO_FORCED_MATE -> "Verified Fact: Walked into opponent forced checkmate!"
                BlunderKind.CENTIPAWN_LOSS -> "Verified Blunder: Material or evaluation drop of ${verdict.centipawnLoss} cp."
                else -> "Solid move: Position maintained."
            }

            val assessment = MoveAssessment(
                evaluationBeforeCp = analysisBefore.centipawns,
                evaluationAfterCp = analysisAfter.centipawns,
                mateInMovesBefore = analysisBefore.mateInMoves,
                mateInMovesAfter = analysisAfter.mateInMoves,
                verifiedConsequences = consequences,
                confidence = SearchConfidence(depth = depth, nodes = null),
                coachingLabel = coachingLabel
            )

            if (verdict.isBlunder) {
                // Auto-save blunder to reviews
                val item = ReviewItem(
                    id = UUID.randomUUID().toString(),
                    fen = beforeFen,
                    dueAt = Instant.now(),
                    stage = -1,
                    attempts = 0,
                    mistakeUci = move.uci,
                    bestMoveUci = analysisBefore.bestMoveUci,
                    explanation = coachingLabel
                )
                repository.upsert(item)
                loadReviews()
            }

            _state.update {
                it.copy(
                    assessment = assessment,
                    message = coachingLabel,
                    mistakeDetected = verdict.isBlunder,
                    mistakeFen = if (verdict.isBlunder) beforeFen else null,
                    canRetryMistake = verdict.isBlunder
                )
            }

            // Engine response if not game over
            if (!afterPos.isOver) {
                applyBotElo()
                val engineResponse = engine.analyze(
                    AnalysisRequest(reqId + 2, afterFen, depth = 5, movetimeMs = 400)
                )
                val engineMovePos = ChessPosition(afterFen)
                val engineMove = engineMovePos.legalMoves.firstOrNull { it.uci == engineResponse.bestMoveUci }
                    ?: engineMovePos.legalMoves.firstOrNull()

                if (engineMove != null) {
                    engineMovePos.play(engineMove)
                    _state.update {
                        it.copy(
                            fen = engineMovePos.fen,
                            lastMove = Pair(engineMove.from, engineMove.to),
                            busy = false,
                            evaluationCp = engineResponse.centipawns,
                            mateIn = engineResponse.mateInMoves,
                            arenaStatusText = "Opponent (${it.botTuningDescription}) responded with ${engineMove.san}."
                        )
                    }
                } else {
                    _state.update { it.copy(busy = false) }
                }
            } else {
                _state.update {
                    it.copy(
                        busy = false,
                        arenaStatusText = if (afterPos.isCheckmate) "Game Over by Checkmate!" else "Draw!"
                    )
                }
            }
        }
    }

    // Rating Linking and Bot Calibration
    fun linkRatingAccount(
        platform: RatingPlatform,
        username: String,
        timeControl: RatingTimeControl = RatingTimeControl.RAPID
    ) {
        val clean = username.trim()
        if (clean.isBlank()) {
            _state.update { it.copy(linkingError = "Please enter a valid username.") }
            return
        }
        _state.update {
            it.copy(
                isLinkingLoading = true,
                linkingError = null,
                linkingSuccessMessage = null
            )
        }
        viewModelScope.launch {
            val result = ratingRepository.linkAccount(platform, clean, timeControl)
            result.onSuccess { profile ->
                _state.update {
                    it.copy(
                        isLinkingLoading = false,
                        linkingError = null,
                        linkingSuccessMessage = "Successfully linked ${profile.platform.displayName} profile '${profile.username}'",
                        useLinkedRatingForBot = true
                    )
                }
                applyBotElo()
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isLinkingLoading = false,
                        linkingError = err.message ?: "Failed to link profile. Please check username."
                    )
                }
            }
        }
    }

    fun refreshLinkedRating() {
        val current = _state.value.linkedProfile ?: return
        _state.update {
            it.copy(
                isLinkingLoading = true,
                linkingError = null,
                linkingSuccessMessage = null
            )
        }
        viewModelScope.launch {
            val result = ratingRepository.refreshProfile()
            result.onSuccess { profile ->
                _state.update {
                    it.copy(
                        isLinkingLoading = false,
                        linkingSuccessMessage = "Updated ratings for '${profile.username}'"
                    )
                }
                applyBotElo()
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isLinkingLoading = false,
                        linkingError = err.message ?: "Failed to refresh rating"
                    )
                }
            }
        }
    }

    fun setRatingTimeControl(timeControl: RatingTimeControl) {
        viewModelScope.launch {
            ratingRepository.updateTimeControl(timeControl)
            applyBotElo()
        }
    }

    fun setUseLinkedRatingForBot(useLinked: Boolean) {
        _state.update { it.copy(useLinkedRatingForBot = useLinked) }
        applyBotElo()
    }

    fun unlinkRatingAccount() {
        viewModelScope.launch {
            ratingRepository.unlinkAccount()
            _state.update {
                it.copy(
                    linkedProfile = null,
                    useLinkedRatingForBot = false,
                    linkingSuccessMessage = null,
                    linkingError = null
                )
            }
            applyBotElo()
        }
    }

    fun clearLinkingStatus() {
        _state.update { it.copy(linkingError = null, linkingSuccessMessage = null) }
    }

    // Review Mode: Spaced Repetition Practice
    fun selectReviewItem(index: Int) {
        val items = _state.value.reviews
        if (index in items.indices) {
            val item = items[index]
            _state.update {
                it.copy(
                    activeReviewIndex = index,
                    activeReviewItem = item,
                    fen = item.fen,
                    message = "Spaced Repetition Review: Find the best move for this position!",
                    hintLevel = 0,
                    hintText = "",
                    reviewSolved = false,
                    recommendedArrow = null,
                    lastMove = null
                )
            }
        }
    }

    private fun playReviewMove(move: MoveChoice) {
        val activeItem = _state.value.activeReviewItem ?: return
        val pos = ChessPosition(_state.value.fen)
        val played = pos.play(move)
        if (!played) return

        val isBest = move.uci == activeItem.bestMoveUci || (pos.isCheckmate && activeItem.explanation.contains("mate", ignoreCase = true))
        val usedHint = _state.value.hintLevel > 0

        viewModelScope.launch {
            ReviewScheduler.recordAttempt(activeItem, correct = isBest, usedHint = usedHint, now = Instant.now())
            repository.upsert(activeItem)
            loadReviews()
        }

        if (isBest) {
            _state.update {
                it.copy(
                    fen = pos.fen,
                    lastMove = Pair(move.from, move.to),
                    reviewSolved = true,
                    message = "★ Correct! Spaced repetition updated: Next review in ${ReviewScheduler.intervalsDays.getOrNull(activeItem.stage) ?: 1} days."
                )
            }
        } else {
            _state.update {
                it.copy(
                    fen = pos.fen,
                    lastMove = Pair(move.from, move.to),
                    reviewSolved = false,
                    message = "Incorrect move! Review stage reset to immediate review. Try again!"
                )
            }
        }
    }

    // Curriculum practice & progress
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
        _state.update {
            it.copy(
                tab = 0, // open in Coach for interactive guided retry
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

    fun exploreLearnTopic(topic: com.chesstutor.app.domain.LearnTopic) {
        val arrow = if (topic.recommendedMoveUci.length >= 4) {
            Pair(topic.recommendedMoveUci.take(2), topic.recommendedMoveUci.substring(2, 4))
        } else null
        _state.update {
            it.copy(
                tab = 0, // load onto the interactive board in Coach tab
                curriculumLessonId = topic.id,
                activeCoachTitle = topic.title,
                activeCoachSubtitle = topic.subtitle,
                activeCoachCategory = topic.category.displayName.uppercase(),
                activeCoachRecommendedMove = topic.recommendedMoveUci,
                fen = topic.demoFen,
                selectedSquare = null,
                legalTargets = emptySet(),
                lastMove = null,
                recommendedArrow = arrow,
                message = "${topic.title}: ${topic.moveExplanation}",
                mistakeDetected = false,
                assessment = null,
                hintLevel = 0,
                hintText = ""
            )
        }
    }

    fun loadLearnTopic(topic: com.chesstutor.app.domain.LearnTopic) {
        exploreLearnTopic(topic)
    }

    fun playActivePrincipleMove() {
        val moveUci = _state.value.activeCoachRecommendedMove ?: return
        if (moveUci.length < 4) return
        val from = moveUci.substring(0, 2)
        val to = moveUci.substring(2, 4)
        val pos = ChessPosition(_state.value.fen)
        val success = pos.play(moveUci)
        if (success) {
            _state.update {
                it.copy(
                    fen = pos.fen,
                    lastMove = Pair(from, to),
                    selectedSquare = null,
                    legalTargets = emptySet(),
                    recommendedArrow = null,
                    message = "Demonstrated principle move: $moveUci. Now test your own response moves!"
                )
            }
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        _state.update { it.copy(isSoundEnabled = enabled) }
    }

    fun setSettingsVisible(visible: Boolean) {
        _state.update { it.copy(isSettingsVisible = visible) }
    }

    fun resetCurriculumProgress() {
        _state.update {
            it.copy(
                practicedModules = emptySet(),
                masteredModules = emptySet()
            )
        }
    }

    fun markModuleMastered(lessonId: String) {
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
        val sampleItem = ReviewItem(
            id = "sample_review_${System.currentTimeMillis()}",
            fen = FEN_MATE_IN_ONE,
            bestMoveUci = "d1h5",
            explanation = "Missed Forced Mate: Qh5# was decisive checkmate in 1 move.",
            stage = 0,
            dueAt = java.time.Instant.now().minusSeconds(60)
        )
        viewModelScope.launch {
            repository.upsert(sampleItem)
            val updated = repository.loadAll()
            _state.update {
                it.copy(
                    reviews = updated,
                    activeReviewIndex = 0,
                    activeReviewItem = sampleItem,
                    fen = sampleItem.fen,
                    message = "Sample Mistake Loaded: Prove mastery by finding the mating move!",
                    hintLevel = 0,
                    hintText = "",
                    reviewSolved = false,
                    recommendedArrow = null,
                    lastMove = null
                )
            }
        }
    }

    private fun playCurriculumMove(move: MoveChoice) {
        playCoachMove(move)
    }

    fun setArenaDifficulty(difficulty: String) {
        _state.update { it.copy(arenaDifficulty = difficulty, useLinkedRatingForBot = false) }
        applyBotElo()
    }

    fun resetArenaGame() {
        _state.update {
            it.copy(
                fen = ChessPosition.STARTING_FEN,
                message = "New game against Stockfish (${it.arenaDifficulty}). Make your first move!",
                lastMove = null,
                recommendedArrow = null,
                assessment = null,
                arenaStatusText = ""
            )
        }
    }

    fun runEngineDiagnostics() {
        if (_state.value.isRunningDiagnostics) return
        _state.update { it.copy(isRunningDiagnostics = true) }
        viewModelScope.launch {
            try {
                val diag = if (engine is com.chesstutor.app.engine.StockfishProcessEngineClient) {
                    engine.runDiagnostics(movetimeMs = 1000)
                } else {
                    val start = System.currentTimeMillis()
                    val res = engine.analyze(
                        AnalysisRequest(
                            requestId = 9999,
                            fen = ChessPosition.STARTING_FEN,
                            movetimeMs = 1000
                        )
                    )
                    com.chesstutor.app.engine.EngineDiagnostics(
                        engineName = "Fallback Engine (Local)",
                        isAlive = true,
                        bestMove = res.bestMoveUci,
                        centipawns = res.centipawns,
                        depth = res.depth,
                        pv = res.principalVariation.joinToString(" "),
                        latencyMs = System.currentTimeMillis() - start,
                        resolvedBinaryPath = "In-memory Kotlin fallback (no native subprocess)",
                        launchError = "Running on LocalFallbackEngineClient. No native ELF binary attached."
                    )
                }
                android.util.Log.i("StockfishDiagnostics", "Engine diagnostics: $diag")
                _state.update {
                    it.copy(
                        isRunningDiagnostics = false,
                        engineDiagnostics = diag
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("StockfishDiagnostics", "Diagnostics failed", e)
                _state.update {
                    it.copy(
                        isRunningDiagnostics = false,
                        engineDiagnostics = com.chesstutor.app.engine.EngineDiagnostics(
                            engineName = "Engine Error",
                            isAlive = false,
                            bestMove = "Error: ${e.message}",
                            centipawns = null,
                            depth = null,
                            pv = "",
                            latencyMs = 0,
                            launchError = "${e::class.java.simpleName}: ${e.message}"
                        )
                    )
                }
            }
        }
    }
}
