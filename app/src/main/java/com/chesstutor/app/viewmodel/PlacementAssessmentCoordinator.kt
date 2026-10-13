package com.chesstutor.app.viewmodel

import com.chesstutor.app.data.model.LearningProfile
import com.chesstutor.app.data.model.PlacementAssessment
import com.chesstutor.app.domain.MoveChoice
import com.chesstutor.app.domain.TrainDrillsRepository

class PlacementAssessmentCoordinator {

    private val solvedIds = mutableSetOf<String>()

    fun start(
        persistProfile: ((LearningProfile) -> LearningProfile) -> Unit,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        solvedIds.clear()
        persistProfile {
            it.copy(
                assessmentState = "IN_PROGRESS",
                assessmentPositionIndex = 0,
                assessmentCorrect = 0,
                assessmentTotal = 0,
                assessmentCompletedAt = null
            )
        }
        val question = PlacementAssessment.questions.first()
        updateState {
            it.copy(
                tab = 0,
                fen = question.fen,
                message = "Placement 1/${PlacementAssessment.questions.size}: ${question.skill}. Find the best move.",
                assessmentState = "IN_PROGRESS",
                assessmentPositionIndex = 0,
                assessmentCorrect = 0,
                assessmentTotal = 0,
                assessmentCompletedAt = null,
                selectedSquare = null,
                legalTargets = emptySet(),
                recommendedArrow = null,
                lastMove = null,
                mistakeDetected = false,
                assessment = null,
                hintLevel = 0,
                hintText = ""
            )
        }
    }

    fun answer(
        move: MoveChoice,
        currentState: AppUiState,
        persistProfile: ((LearningProfile) -> LearningProfile) -> Unit,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        if (currentState.assessmentState != "IN_PROGRESS") return
        val index = currentState.assessmentPositionIndex
        val question = PlacementAssessment.questions.getOrNull(index) ?: return
        val correct = move.uci.equals(question.expectedMoveUci, ignoreCase = true)
        if (correct) solvedIds.add(question.id)
        val nextCorrect = currentState.assessmentCorrect + if (correct) 1 else 0
        val nextTotal = currentState.assessmentTotal + 1
        val nextIndex = index + 1

        if (nextIndex >= PlacementAssessment.questions.size) {
            val estimate = if (solvedIds.isNotEmpty() || nextCorrect == 0) {
                PlacementAssessment.estimateRatingFromSolved(solvedIds.toSet())
            } else {
                PlacementAssessment.estimateRating(nextCorrect, nextTotal).coerceIn(400, 1800)
            }
            val completedAt = System.currentTimeMillis()
            val firstDrill = TrainDrillsRepository.drills.first()
            persistProfile {
                it.copy(
                    estimatedRating = estimate,
                    assessmentState = "COMPLETE",
                    assessmentPositionIndex = nextIndex,
                    assessmentCorrect = nextCorrect,
                    assessmentTotal = nextTotal,
                    assessmentCompletedAt = completedAt
                )
            }
            updateState {
                it.copy(
                    estimatedRating = estimate,
                    assessmentState = "COMPLETE",
                    assessmentPositionIndex = nextIndex,
                    assessmentCorrect = nextCorrect,
                    assessmentTotal = nextTotal,
                    assessmentCompletedAt = completedAt,
                    currentDrillIndex = 0,
                    activeLessonSession = null,
                    curriculumLessonId = null,
                    fen = firstDrill.fen,
                    trainFen = firstDrill.fen,
                    activeCoachTitle = firstDrill.title,
                    activeCoachSubtitle = firstDrill.subtitle,
                    activeCoachCategory = firstDrill.category,
                    activeCoachRecommendedMove = firstDrill.solutionUci,
                    puzzlePhase = PuzzlePhase.SOLVING,
                    message = firstDrill.objectivePrompt,
                    trainMessage = firstDrill.objectivePrompt,
                    hintLevel = 0,
                    hintText = "",
                    lastMove = null,
                    trainLastMove = null,
                    selectedSquare = null,
                    legalTargets = emptySet(),
                    recommendedArrow = null,
                    trainRecommendedArrow = null,
                    mistakeDetected = false
                )
            }
            return
        }

        val next = PlacementAssessment.questions[nextIndex]
        persistProfile {
            it.copy(
                assessmentState = "IN_PROGRESS",
                assessmentPositionIndex = nextIndex,
                assessmentCorrect = nextCorrect,
                assessmentTotal = nextTotal
            )
        }
        updateState {
            it.copy(
                assessmentPositionIndex = nextIndex,
                assessmentCorrect = nextCorrect,
                assessmentTotal = nextTotal,
                fen = next.fen,
                message = if (correct) "Correct. Next: ${next.skill}." else "Not quite. Next: ${next.skill}.",
                selectedSquare = null,
                legalTargets = emptySet(),
                recommendedArrow = null,
                lastMove = null,
                hintLevel = 0,
                hintText = ""
            )
        }
    }

    fun skipAsBeginner(
        persistProfile: ((LearningProfile) -> LearningProfile) -> Unit,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        val completedAt = System.currentTimeMillis()
        val firstDrill = TrainDrillsRepository.drills.first()
        persistProfile {
            it.copy(
                estimatedRating = 400,
                assessmentState = "COMPLETE",
                assessmentCompletedAt = completedAt
            )
        }
        updateState {
            it.copy(
                estimatedRating = 400,
                assessmentState = "COMPLETE",
                assessmentCompletedAt = completedAt,
                currentDrillIndex = 0,
                activeLessonSession = null,
                curriculumLessonId = null,
                fen = firstDrill.fen,
                trainFen = firstDrill.fen,
                activeCoachTitle = firstDrill.title,
                activeCoachSubtitle = firstDrill.subtitle,
                activeCoachCategory = firstDrill.category,
                activeCoachRecommendedMove = firstDrill.solutionUci,
                puzzlePhase = PuzzlePhase.SOLVING,
                message = firstDrill.objectivePrompt,
                trainMessage = firstDrill.objectivePrompt,
                hintLevel = 0,
                hintText = "",
                lastMove = null,
                trainLastMove = null,
                selectedSquare = null,
                legalTargets = emptySet(),
                recommendedArrow = null,
                trainRecommendedArrow = null,
                mistakeDetected = false
            )
        }
    }
}
