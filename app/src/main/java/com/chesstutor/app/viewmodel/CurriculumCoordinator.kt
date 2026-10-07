package com.chesstutor.app.viewmodel

import com.chesstutor.app.data.model.LearningProfile
import com.chesstutor.app.data.repository.LearningRepository
import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.domain.LearnTopic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CurriculumCoordinator(
    private val learningRepository: LearningRepository,
    private val scope: CoroutineScope,
    private val profileMutex: Mutex,
    private val startEvaluation: (String) -> Unit
) {

    fun persistProfile(update: (LearningProfile) -> LearningProfile) {
        scope.launch {
            profileMutex.withLock {
                val current = learningRepository.getProfile()
                learningRepository.saveProfile(update(current).copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun recordCurriculumAttempt(
        lessonId: String?,
        correct: Boolean,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        if (lessonId.isNullOrBlank()) return
        scope.launch {
            learningRepository.recordModuleAttempt(lessonId, correct)
            val mastered = learningRepository.getModuleProgress().firstOrNull { it.moduleId == lessonId }?.mastered == true
            updateState { current ->
                val nextPracticed = current.practicedModules + lessonId
                val nextMastered = if (mastered) current.masteredModules + lessonId else current.masteredModules
                current.copy(
                    practicedModules = nextPracticed,
                    masteredModules = nextMastered
                )
            }
        }
    }

    fun recordTacticalAttempt(
        correct: Boolean,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        scope.launch {
            val updated = profileMutex.withLock {
                val current = learningRepository.getProfile()
                val next = current.copy(
                    totalTacticalAttempts = current.totalTacticalAttempts + 1,
                    totalTacticalCorrect = current.totalTacticalCorrect + if (correct) 1 else 0,
                    updatedAt = System.currentTimeMillis()
                )
                learningRepository.saveProfile(next)
                next
            }
            updateState {
                it.copy(
                    tacticalAttempts = updated.totalTacticalAttempts,
                    tacticalCorrect = updated.totalTacticalCorrect
                )
            }
        }
    }

    fun exploreLearnTopic(
        topic: LearnTopic,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        val arrow = if (topic.recommendedMoveUci.length >= 4) {
            Pair(topic.recommendedMoveUci.take(2), topic.recommendedMoveUci.substring(2, 4))
        } else null
        updateState {
            it.copy(
                tab = 0,
                curriculumLessonId = topic.id,
                selectedLearnTopicId = topic.id,
                puzzlePhase = PuzzlePhase.SOLVING,
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
        startEvaluation(topic.demoFen)
    }

    fun playActivePrincipleMove(
        activeMoveUci: String?,
        currentFen: String,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        if (activeMoveUci == null || activeMoveUci.length < 4) return
        val from = activeMoveUci.substring(0, 2)
        val to = activeMoveUci.substring(2, 4)
        val pos = ChessPosition(currentFen)
        val success = pos.play(activeMoveUci)
        if (success) {
            updateState {
                it.copy(
                    fen = pos.fen,
                    lastMove = Pair(from, to),
                    selectedSquare = null,
                    legalTargets = emptySet(),
                    recommendedArrow = null,
                    message = "Demonstrated principle move: $activeMoveUci. Now test your own response moves!"
                )
            }
            startEvaluation(pos.fen)
        }
    }

    fun resetProgress(updateState: ((AppUiState) -> AppUiState) -> Unit) {
        scope.launch { learningRepository.resetModuleProgress() }
        updateState {
            it.copy(
                practicedModules = emptySet(),
                masteredModules = emptySet()
            )
        }
    }
}
