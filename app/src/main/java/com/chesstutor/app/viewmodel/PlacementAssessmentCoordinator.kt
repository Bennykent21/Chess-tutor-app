package com.chesstutor.app.viewmodel

import com.chesstutor.app.data.model.LearningProfile
import com.chesstutor.app.data.model.PlacementAssessment
import com.chesstutor.app.domain.MoveChoice

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
                assessment = null
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
        val correct = move.uci == question.expectedMoveUci
        if (correct) solvedIds.add(question.id)
        val nextCorrect = currentState.assessmentCorrect + if (correct) 1 else 0
        val nextTotal = currentState.assessmentTotal + 1
        val nextIndex = index + 1

        if (nextIndex >= PlacementAssessment.questions.size) {
            val estimate = if (solvedIds.isNotEmpty() || nextCorrect == 0) {
                PlacementAssessment.estimateRatingFromSolved(solvedIds.toSet())
            } else {
                PlacementAssessment.estimateRating(nextCorrect, nextTotal)
            }
            val completedAt = System.currentTimeMillis()
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
                    message = "Assessment complete. Estimated training rating: $estimate.",
                    lastMove = Pair(move.from, move.to),
                    selectedSquare = null,
                    legalTargets = emptySet(),
                    recommendedArrow = null
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
                lastMove = null
            )
        }
    }

    fun skipAsBeginner(
        persistProfile: ((LearningProfile) -> LearningProfile) -> Unit,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        val completedAt = System.currentTimeMillis()
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
                message = "Started at Beginner (400 ELO) curriculum track."
            )
        }
    }
}
