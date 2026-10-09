package com.chesstutor.app.viewmodel

import com.chesstutor.app.data.model.LearningProfile
import com.chesstutor.app.data.model.ModuleProgress
import com.chesstutor.app.data.repository.LearningRepository
import com.chesstutor.app.domain.AdaptiveTrainingPlanner
import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.domain.LearnCurriculumRepository
import com.chesstutor.app.domain.LearnTopic
import com.chesstutor.app.domain.LessonSessionFactory
import com.chesstutor.app.domain.ReviewItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CurriculumCoordinator(
    private val learningRepository: LearningRepository,
    private val scope: CoroutineScope,
    private val profileMutex: Mutex = Mutex(),
    private val startEvaluation: (String) -> Unit = {}
) {

    fun persistProfile(transform: (LearningProfile) -> LearningProfile) {
        scope.launch {
            profileMutex.withLock {
                val current = learningRepository.getProfile()
                val updated = transform(current).copy(updatedAt = System.currentTimeMillis())
                learningRepository.saveProfile(updated)
            }
        }
    }

    fun recordTacticalAttempt(
        correct: Boolean,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        updateState {
            it.copy(
                tacticalAttempts = it.tacticalAttempts + 1,
                tacticalCorrect = it.tacticalCorrect + if (correct) 1 else 0
            )
        }
        persistProfile {
            it.copy(
                totalTacticalAttempts = it.totalTacticalAttempts + 1,
                totalTacticalCorrect = it.totalTacticalCorrect + if (correct) 1 else 0
            )
        }
    }

    /**
     * Starts a structured multi-step [com.chesstutor.app.domain.LessonTrainSession]
     * (Explain -> Guided Try -> 3 Practice Positions -> Summary) with NO pre-drawn
     * answer arrow on interactive steps (Review 2 §4.3-4.4 & Review 3 S4).
     */
    fun exploreLearnTopic(
        topic: LearnTopic,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        val session = LessonSessionFactory.buildSessionForTopic(topic)
        val firstStep = session.currentStep
        updateState {
            it.copy(
                tab = 0,
                fen = firstStep.fen,
                trainFen = firstStep.fen,
                curriculumLessonId = topic.id,
                selectedLearnTopicId = topic.id,
                activeLessonSession = session,
                inProgressModules = it.inProgressModules + topic.id,
                activeCoachTitle = firstStep.title,
                activeCoachSubtitle = topic.subtitle,
                activeCoachCategory = "LESSON · STEP 1 OF ${session.totalSteps}",
                activeCoachRecommendedMove = firstStep.solutionUci,
                puzzlePhase = PuzzlePhase.SOLVING,
                hintLevel = 0,
                hintText = "",
                selectedSquare = null,
                legalTargets = emptySet(),
                // Never draw the answer arrow at lesson start (S4)
                recommendedArrow = null,
                trainRecommendedArrow = null,
                lastMove = null,
                trainLastMove = null,
                mistakeDetected = false,
                canRetryMistake = false,
                assessment = null,
                message = firstStep.prompt,
                trainMessage = firstStep.prompt
            )
        }
        startEvaluation(firstStep.fen)
    }

    fun playActivePrincipleMove(
        recommendedMoveUci: String?,
        currentFen: String,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        val uci = recommendedMoveUci ?: return
        val pos = runCatching { ChessPosition(currentFen) }.getOrNull() ?: return
        val move = pos.legalMoves.firstOrNull { it.uci.equals(uci, ignoreCase = true) } ?: return
        if (!pos.play(move)) return
        updateState {
            it.copy(
                fen = pos.fen,
                trainFen = pos.fen,
                lastMove = Pair(move.from, move.to),
                trainLastMove = Pair(move.from, move.to),
                puzzlePhase = PuzzlePhase.CORRECT,
                recommendedArrow = null,
                trainRecommendedArrow = null,
                message = "Principle demonstrated with ${move.san}.",
                trainMessage = "Principle demonstrated with ${move.san}."
            )
        }
        startEvaluation(pos.fen)
    }

    fun resetProgress(updateState: ((AppUiState) -> AppUiState) -> Unit) {
        scope.launch {
            learningRepository.resetModuleProgress()
            updateState {
                it.copy(
                    inProgressModules = emptySet(),
                    practicedModules = emptySet(),
                    masteredModules = emptySet()
                )
            }
        }
    }

    /**
     * Records an attempt on a curriculum module. Only marks [practiced] = true when
     * [markCompleted] is true or multiple attempts succeed, preventing single-move inflation
     * of Learn progress (Review 3 §2.11).
     */
    fun recordCurriculumAttempt(
        lessonId: String?,
        correct: Boolean,
        updateState: ((AppUiState) -> AppUiState) -> Unit,
        onRefreshRecommendation: () -> Unit = {},
        markCompleted: Boolean = false
    ) {
        val id = lessonId ?: return
        scope.launch {
            learningRepository.recordModuleAttempt(id, correct)
            if (markCompleted) {
                learningRepository.markPracticed(id)
                if (correct) {
                    learningRepository.markMastered(id)
                }
            }
            val updatedList = learningRepository.getModuleProgress()
            val mod = updatedList.firstOrNull { it.moduleId == id } ?: ModuleProgress(moduleId = id)
            val isPracticed = mod.practiced || markCompleted
            val isMastered = mod.mastered || (markCompleted && correct)
            updateState {
                it.copy(
                    inProgressModules = if (isMastered) it.inProgressModules - id else it.inProgressModules + id,
                    practicedModules = if (isPracticed) it.practicedModules + id else it.practicedModules,
                    masteredModules = if (isMastered) it.masteredModules + id else it.masteredModules
                )
            }
            onRefreshRecommendation()
        }
    }

    fun refreshTrainingRecommendation(
        reviews: List<ReviewItem>,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        scope.launch {
            val profile = learningRepository.getProfile()
            val progress = learningRepository.getModuleProgress()
            val recommendation = AdaptiveTrainingPlanner.recommend(
                profile = profile,
                modules = progress
            )
            val practicedSet = progress.filter { it.practiced }.map { it.moduleId }.toSet()
            val masteredSet = progress.filter { it.mastered }.map { it.moduleId }.toSet()
            val inProgressSet = progress.filter { !it.mastered && it.attempts > 0 }.map { it.moduleId }.toSet()
            updateState {
                it.copy(
                    trainingRecommendation = recommendation.topic.title,
                    trainingRecommendationReason = recommendation.reason,
                    inProgressModules = inProgressSet,
                    practicedModules = practicedSet,
                    masteredModules = masteredSet
                )
            }
        }
    }
}
