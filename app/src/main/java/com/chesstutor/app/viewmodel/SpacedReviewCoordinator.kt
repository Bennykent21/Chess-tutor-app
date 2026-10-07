package com.chesstutor.app.viewmodel

import com.chesstutor.app.data.repository.ReviewRepository
import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.domain.MoveChoice
import com.chesstutor.app.domain.ReviewItem
import com.chesstutor.app.domain.ReviewScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class SpacedReviewCoordinator(
    private val repository: ReviewRepository,
    private val scope: CoroutineScope,
    private val startEvaluation: (String) -> Unit
) {

    fun loadReviews(updateState: ((AppUiState) -> AppUiState) -> Unit) {
        scope.launch {
            val all = repository.loadAll()
            updateState { current ->
                val active = current.activeReviewItem ?: all.firstOrNull()
                current.copy(
                    reviews = all,
                    activeReviewItem = active,
                    activeReviewIndex = if (active != null) all.indexOfFirst { it.id == active.id }.coerceAtLeast(0) else 0
                )
            }
        }
    }

    fun selectReviewItem(
        index: Int,
        items: List<ReviewItem>,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        if (index in items.indices) {
            val item = items[index]
            updateState {
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
            startEvaluation(item.fen)
        }
    }

    fun playReviewMove(
        move: MoveChoice,
        activeItem: ReviewItem?,
        hintLevel: Int,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        if (activeItem == null) return

        val reviewFen = activeItem.fen
        val pos = ChessPosition(reviewFen)
        val played = pos.play(move)
        if (!played) return

        val isBest = move.uci == activeItem.bestMoveUci ||
            move.uci in activeItem.acceptedMovesUci ||
            pos.isCheckmate

        val usedHint = hintLevel > 0

        if (isBest) {
            val updatedItem = ReviewScheduler.recordAttempt(
                activeItem,
                correct = true,
                usedHint = usedHint,
                now = Instant.now()
            )
            scope.launch {
                repository.upsert(updatedItem)
                loadReviews(updateState)
            }

            updateState {
                it.copy(
                    fen = pos.fen,
                    lastMove = Pair(move.from, move.to),
                    reviewSolved = true,
                    puzzlePhase = PuzzlePhase.CORRECT,
                    activeReviewItem = updatedItem,
                    message = "★ Correct! Spaced repetition updated: Next review in ${updatedItem.intervalDays} days.",
                    selectedSquare = null,
                    legalTargets = emptySet()
                )
            }
            startEvaluation(pos.fen)
        } else {
            val updatedItem = ReviewScheduler.recordAttempt(
                activeItem,
                correct = false,
                usedHint = usedHint,
                now = Instant.now()
            )
            scope.launch {
                repository.upsert(updatedItem)
                loadReviews(updateState)
            }

            updateState {
                it.copy(
                    fen = reviewFen,
                    lastMove = null,
                    reviewSolved = false,
                    puzzlePhase = PuzzlePhase.WRONG,
                    activeReviewItem = updatedItem,
                    message = "Incorrect move! Review stage reset to immediate review. Try again!",
                    selectedSquare = null,
                    legalTargets = emptySet(),
                    recommendedArrow = null
                )
            }
            startEvaluation(reviewFen)
        }
    }

    fun loadSampleMistake(
        sampleFen: String,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        val sampleItem = ReviewItem(
            id = UUID.randomUUID().toString(),
            fen = sampleFen,
            dueAt = Instant.now(),
            stage = -1,
            attempts = 0,
            mistakeUci = "e4f2",
            bestMoveUci = "f3f7",
            explanation = "Sample blunder: Qxf7# was an immediate forced checkmate on the weak f7 square."
        )

        scope.launch {
            repository.upsert(sampleItem)
            val updated = repository.loadAll()
            updateState {
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
            startEvaluation(sampleItem.fen)
        }
    }
}
