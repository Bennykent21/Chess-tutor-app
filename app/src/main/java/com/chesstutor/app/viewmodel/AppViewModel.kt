package com.chesstutor.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.data.model.LinkedChessProfile
import com.chesstutor.app.data.model.PlacementAssessment
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.chesstutor.app.data.repository.InMemoryRatingRepository
import com.chesstutor.app.data.repository.LearningRepository
import com.chesstutor.app.data.repository.RatingRepository
import com.chesstutor.app.data.repository.ReviewRepository
import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.domain.MoveAssessment
import com.chesstutor.app.domain.MoveChoice
import com.chesstutor.app.domain.ReviewItem
import com.chesstutor.app.domain.ReviewScheduler
import com.chesstutor.app.domain.SearchConfidence
import com.chesstutor.app.domain.VerifiedConsequence
import com.chesstutor.app.engine.BlunderClassifier
import com.chesstutor.app.engine.BlunderKind
import com.chesstutor.app.engine.EngineClient
import com.example.chess.core.Position
import kotlinx.coroutines.delay
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
    private val ratingRepository: RatingRepository = InMemoryRatingRepository(),
    private val learningRepository: LearningRepository = com.chesstutor.app.data.repository.InMemoryLearningRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val blunderClassifier = BlunderClassifier(thresholdCentipawns = 150)
    private val localBotMoveSelector = com.chesstutor.app.engine.LocalBotMoveSelector()
    private val analysisService = com.chesstutor.app.engine.AnalysisService(engine)

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
            applyBotElo()
            loadReviews()
            loadLearningState()
            selectDrill(0)
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
            runCatching { engine.setStrengthRating(elo) }
        }
    }

    fun toggleAutoOpponent() {
        _state.update { it.copy(isAutoOpponentEnabled = !it.isAutoOpponentEnabled) }
    }

    suspend fun selectCalibratedBotMove(fen: String, elo: Int, botDifficulty: String): MoveChoice? {
        val chessPos = ChessPosition(fen)
        if (chessPos.isOver) return null
        val corePos = Position.tryFromFen(fen).getOrNull() ?: return null

        // Use the configured engine chain for every bot tier. This keeps the
        // real bundled Stockfish path primary instead of silently bypassing it
        // for most ratings. The engine strength is calibrated from the same
        // canonical BotStrength scale used by the Arena UI.
        return try {
            engine.setStrengthRating(elo)
            val preset = com.example.chess.engine.BotStrength.presets.minByOrNull {
                kotlin.math.abs(it.rating - elo)
            }
            val depth = when {
                preset == null -> 3
                preset.rating <= 600 -> 2
                preset.rating <= 1300 -> 3
                preset.rating <= 2000 -> 4
                preset.rating <= 2400 -> 5
                else -> 6
            }
            val result = analysisService.analyze(fen = fen, depth = depth, movetimeMs = 800)
            val matching = result?.let { analysis ->
                chessPos.legalMoves.firstOrNull { it.uci == analysis.bestMoveUci }
            }
            matching ?: localBotMoveSelector.selectMove(corePos, elo).let { fallback ->
                chessPos.legalMoves.firstOrNull { it.uci == fallback.uci }
                    ?: chessPos.legalMoves.firstOrNull()
            }
        } catch (_: Exception) {
            // Deterministic local selector is the final bot-move fallback.
            runCatching {
                localBotMoveSelector.selectMove(corePos, elo)
            }.getOrNull()?.let { fallback ->
                chessPos.legalMoves.firstOrNull { it.uci == fallback.uci }
            } ?: chessPos.legalMoves.firstOrNull()
        }
    }

    private fun triggerOpponentResponseInCoach(afterFen: String) {
        if (!_state.value.isAutoOpponentEnabled) return
        val pos = ChessPosition(afterFen)
        if (pos.isOver) return

        _state.update { it.copy(busy = true, opponentThinking = true) }
        viewModelScope.launch {
            delay(400) // Natural thinking delay
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

    private suspend fun loadLearningState() {
        runCatching {
            val profile = learningRepository.getProfile()
            val progress = learningRepository.getModuleProgress()
            _state.update { current ->
                current.copy(
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
                    practicedModules = progress.filter { it.practiced }.map { it.moduleId }.toSet(),
                    masteredModules = progress.filter { it.mastered }.map { it.moduleId }.toSet()
                )
            }
        }.onFailure { ex ->
            _state.update { it.copy(storageWarning = "Learning state storage note: ${ex.message}") }
        }
    }

    private fun persistProfile(update: (com.chesstutor.app.data.model.LearningProfile) -> com.chesstutor.app.data.model.LearningProfile) {
        viewModelScope.launch {
            val current = learningRepository.getProfile()
            learningRepository.saveProfile(update(current).copy(updatedAt = System.currentTimeMillis()))
        }
    }

    private fun recordTacticalAttempt(correct: Boolean) {
        viewModelScope.launch {
            val current = learningRepository.getProfile()
            val updated = current.copy(
                totalTacticalAttempts = current.totalTacticalAttempts + 1,
                totalTacticalCorrect = current.totalTacticalCorrect + if (correct) 1 else 0,
                updatedAt = System.currentTimeMillis()
            )
            learningRepository.saveProfile(updated)
            _state.update {
                it.copy(
                    tacticalAttempts = updated.totalTacticalAttempts,
                    tacticalCorrect = updated.totalTacticalCorrect
                )
            }
        }
    }

    fun startPlacementAssessment() {
        persistProfile { it.copy(assessmentState = "IN_PROGRESS", assessmentPositionIndex = 0, assessmentCorrect = 0, assessmentTotal = 0, assessmentCompletedAt = null) }
        val question = PlacementAssessment.questions.first()
        _state.update { it.copy(tab = 0, fen = question.fen, message = "Placement 1/" + PlacementAssessment.questions.size + ": " + question.skill + ". Find the best move.", assessmentState = "IN_PROGRESS", assessmentPositionIndex = 0, assessmentCorrect = 0, assessmentTotal = 0, assessmentCompletedAt = null, selectedSquare = null, legalTargets = emptySet(), recommendedArrow = null, lastMove = null, mistakeDetected = false, assessment = null) }
    }

    fun answerPlacementAssessment(move: MoveChoice) {
        if (_state.value.assessmentState != "IN_PROGRESS") return
        val index = _state.value.assessmentPositionIndex
        val question = PlacementAssessment.questions.getOrNull(index) ?: return
        val correct = move.uci == question.expectedMoveUci
        val nextCorrect = _state.value.assessmentCorrect + if (correct) 1 else 0
        val nextTotal = _state.value.assessmentTotal + 1
        val nextIndex = index + 1
        if (nextIndex >= PlacementAssessment.questions.size) {
            val estimate = PlacementAssessment.estimateRating(nextCorrect, nextTotal)
            val completedAt = System.currentTimeMillis()
            persistProfile { it.copy(estimatedRating = estimate, assessmentState = "COMPLETE", assessmentPositionIndex = nextIndex, assessmentCorrect = nextCorrect, assessmentTotal = nextTotal, assessmentCompletedAt = completedAt) }
            _state.update { it.copy(estimatedRating = estimate, assessmentState = "COMPLETE", assessmentPositionIndex = nextIndex, assessmentCorrect = nextCorrect, assessmentTotal = nextTotal, assessmentCompletedAt = completedAt, message = "Assessment complete. Estimated training rating: " + estimate + ".", lastMove = Pair(move.from, move.to), selectedSquare = null, legalTargets = emptySet(), recommendedArrow = null) }
            return
        }
        val next = PlacementAssessment.questions[nextIndex]
        persistProfile { it.copy(assessmentState = "IN_PROGRESS", assessmentPositionIndex = nextIndex, assessmentCorrect = nextCorrect, assessmentTotal = nextTotal) }
        _state.update { it.copy(assessmentPositionIndex = nextIndex, assessmentCorrect = nextCorrect, assessmentTotal = nextTotal, fen = next.fen, message = if (correct) "Correct. Next: " + next.skill + "." else "Not quite. Next: " + next.skill + ".", selectedSquare = null, legalTargets = emptySet(), recommendedArrow = null, lastMove = null) }
    }

    fun resetPlacementAssessment() { startPlacementAssessment() }
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
        if (currentState.busy || currentState.pendingPromotion != null) return

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

        if (matesBefore.isNotEmpty()) {
            // Verifiable category: Missed Mate in One
            if (isMatePlayed) {
                recordTacticalAttempt(correct = true)
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

                recordTacticalAttempt(correct = false)
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
            val rec = _state.value.activeCoachRecommendedMove
            if (rec != null && rec.length >= 4) {
                if (move.uci == rec || isMatePlayed) {
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
        val drills = com.chesstutor.app.domain.TrainDrillsRepository.drills
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
        }
    }

    fun nextDrill() {
        val drills = com.chesstutor.app.domain.TrainDrillsRepository.drills
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
                moveHistory = it.moveHistory + move.san,
                busy = true,
                message = "Move ${move.san} played. Analyzing..."
            )
        }

        viewModelScope.launch {
            val depth = when (_state.value.arenaDifficulty) {
                "Beginner" -> 1
                "Casual" -> 2
                "Intermediate" -> 3
                else -> 4
            }

            val analysisBefore = analysisService.analyze(fen = beforeFen, depth = depth)
            val analysisAfter = analysisService.analyze(fen = afterFen, depth = depth)

            if (analysisBefore == null || analysisAfter == null) {
                _state.update {
                    it.copy(
                        busy = false,
                        opponentThinking = false,
                        message = "Analysis superseded by a newer position."
                    )
                }
                return@launch
            }

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
                if (_state.value.isAutoOpponentEnabled) {
                    _state.update { it.copy(opponentThinking = true) }
                    delay(350)
                    val elo = _state.value.effectiveBotElo
                    val botDifficulty = _state.value.arenaDifficulty
                    val engineMove = selectCalibratedBotMove(afterFen, elo, botDifficulty)

                    if (engineMove != null) {
                        val engineMovePos = ChessPosition(afterFen)
                        engineMovePos.play(engineMove)
                        _state.update {
                            it.copy(
                                fen = engineMovePos.fen,
                                lastMove = Pair(engineMove.from, engineMove.to),
                                moveHistory = it.moveHistory + engineMove.san,
                                busy = false,
                                opponentThinking = false,
                                evaluationCp = analysisAfter.centipawns?.unaryMinus(),
                                mateIn = analysisAfter.mateInMoves,
                                arenaStatusText = "${it.botTuningDescription} played ${engineMove.san}."
                            )
                        }
                    } else {
                        _state.update { it.copy(busy = false, opponentThinking = false) }
                    }
                } else {
                    _state.update { it.copy(busy = false, opponentThinking = false) }
                }
            } else {
                _state.update {
                    it.copy(
                        busy = false,
                        opponentThinking = false,
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

    // Always start from the review position, not whatever position
    // may currently be displayed after a previous attempt.
    val reviewFen = activeItem.fen
    val pos = ChessPosition(reviewFen)

    val played = pos.play(move)
    if (!played) return

    val isBest =
        move.uci == activeItem.bestMoveUci ||
        (
            pos.isCheckmate &&
                activeItem.explanation.contains("mate", ignoreCase = true)
        )

    val usedHint = _state.value.hintLevel > 0

    if (isBest) {
        viewModelScope.launch {
            ReviewScheduler.recordAttempt(
                activeItem,
                correct = true,
                usedHint = usedHint,
                now = Instant.now()
            )

            repository.upsert(activeItem)
            loadReviews()
        }

        _state.update {
            it.copy(
                fen = pos.fen,
                lastMove = Pair(move.from, move.to),
                reviewSolved = true,
                message = "★ Correct! Spaced repetition updated: Next review in ${
                    ReviewScheduler.intervalsDays.getOrNull(activeItem.stage) ?: 1
                } days.",
                selectedSquare = null,
                legalTargets = emptySet()
            )
        }
    } else {
        viewModelScope.launch {
            ReviewScheduler.recordAttempt(
                activeItem,
                correct = false,
                usedHint = usedHint,
                now = Instant.now()
            )

            repository.upsert(activeItem)
            loadReviews()
        }

        _state.update {
            it.copy(
                // IMPORTANT:
                // Do not leave the board on the incorrect position.
                // Return immediately to the original review position.
                fen = reviewFen,
                lastMove = null,
                reviewSolved = false,
                message = "Incorrect move! Review stage reset to immediate review. Try again!",
                selectedSquare = null,
                legalTargets = emptySet(),
                recommendedArrow = null
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
        viewModelScope.launch { learningRepository.markPracticed(lessonId) }
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
        persistProfile { it.copy(soundEnabled = enabled) }
    }

    fun setSettingsVisible(visible: Boolean) {
        _state.update { it.copy(isSettingsVisible = visible) }
    }

    fun resetCurriculumProgress() {
        viewModelScope.launch { learningRepository.resetModuleProgress() }
        _state.update {
            it.copy(
                practicedModules = emptySet(),
                masteredModules = emptySet()
            )
        }
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
        _state.update {
            it.copy(
                fen = ChessPosition.STARTING_FEN,
                message = "New game against ${it.arenaBotName}. Make your first move!",
                lastMove = null,
                moveHistory = emptyList(),
                recommendedArrow = null,
                assessment = null,
                analysis = null,
                mistakeDetected = false,
                arenaStatusText = ""
            )
        }
    }

    fun showAnalysisArrow(from: String, to: String) {
        _state.update { it.copy(recommendedArrow = Pair(from, to)) }
    }

    fun practiceLesson(topic: com.chesstutor.app.domain.LearnTopic) {
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
        // Review uses the same move-selection and attempt-recording path as
        // the main board. This keeps retries, wrong answers, hints, and
        // scheduler updates consistent.
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

    fun runEngineDiagnostics() {
        if (_state.value.isRunningDiagnostics) return
        _state.update { it.copy(isRunningDiagnostics = true) }
        viewModelScope.launch {
            try {
                val start = System.currentTimeMillis()
                val res = analysisService.analyze(
                    fen = ChessPosition.STARTING_FEN,
                    movetimeMs = 1000
                ) ?: throw IllegalStateException("Engine diagnostics result became stale")
                val latency = System.currentTimeMillis() - start
                val diag = com.chesstutor.app.engine.EngineDiagnostics(
                    engineName = "Chess engine",
                    isAlive = true,
                    bestMove = res.bestMoveUci,
                    centipawns = res.centipawns,
                    depth = res.depth,
                    pv = res.principalVariation.joinToString(" "),
                    latencyMs = latency,
                    resolvedBinaryPath = "Configured engine chain",
                    launchError = null
                )
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
