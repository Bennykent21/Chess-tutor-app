package com.chesstutor.app.feature.puzzles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardSession
import com.chesstutor.app.board.BoardSessionState
import com.chesstutor.app.board.MoveEffects
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.repository.ReviewRepository
import com.chesstutor.app.domain.ReviewItem
import com.chesstutor.app.domain.puzzle.DueHint
import com.chesstutor.app.domain.puzzle.DueMoveResult
import com.chesstutor.app.domain.puzzle.DueReview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** What the mistakes review screen shows. */
data class MistakeReviewState(
    val loading: Boolean = true,
    /** 1-based number of the current position, and how many are due. */
    val number: Int = 0,
    val total: Int = 0,
    val item: ReviewItem? = null,
    val message: String = "",
    val done: Boolean = false,
    val wrong: Boolean = false,
    val hint: DueHint? = null,
    val finished: Boolean = false,
    val cleanCount: Int = 0,
)

/**
 * Puzzles > Mistakes review for positions from the player's games (plan 6.3 and 6.4): the
 * spaced review that the classic lesson player used to run, now on the new board. Saves
 * every schedule change and tells [onChanged] so the due counts refresh.
 */
class MistakeReviewViewModel(
    private val repository: ReviewRepository,
    private val onChanged: () -> Unit = {},
    private val now: () -> Instant = Instant::now,
) : ViewModel(), BoardSession {
    private var review = DueReview(emptyList(), now)
    private val mutableBoard = MutableStateFlow(BoardSessionState(Position.initial(), movable = null))
    override val state: StateFlow<BoardSessionState> = mutableBoard.asStateFlow()
    private val mutableUi = MutableStateFlow(MistakeReviewState())
    val ui: StateFlow<MistakeReviewState> = mutableUi.asStateFlow()

    init {
        viewModelScope.launch {
            review = DueReview(repository.loadAll(), now)
            showCurrent()
        }
    }

    override fun onMove(move: Move): BoardFeedback {
        val before = mutableBoard.value.position
        val (result, saved) = review.submit(move.uci)
        save(saved)
        return when (result) {
            DueMoveResult.CORRECT -> {
                val after = LegalMoveGenerator.makeMove(before, move)
                publish(after, previous = before, last = move, movable = false)
                val item = review.current
                mutableUi.update {
                    it.copy(
                        done = true,
                        wrong = false,
                        hint = null,
                        message = listOfNotNull(if (review.missed) "That's it." else "Well found!", item?.explanation?.takeIf { e -> e.isNotBlank() }).joinToString(" "),
                    )
                }
                MoveEffects.CORRECT
            }
            DueMoveResult.WRONG -> {
                mutableUi.update { it.copy(wrong = true, message = "Not that one. This position comes back soon; try again, or take a hint.") }
                MoveEffects.WRONG
            }
            DueMoveResult.ILLEGAL, DueMoveResult.NOT_NOW -> MoveEffects.ILLEGAL
        }
    }

    fun hint() {
        val hint = review.hint() ?: return
        mutableUi.update {
            it.copy(hint = hint, message = if (hint.to == null) "Move the piece on ${hint.from}." else "Play ${hint.from} to ${hint.to}.")
        }
    }

    fun showAnswer() {
        val before = mutableBoard.value.position
        val (best, saved) = review.reveal()
        save(saved)
        if (best != null) publish(LegalMoveGenerator.makeMove(before, best), previous = before, last = best, movable = false)
        mutableUi.update {
            it.copy(done = true, hint = null, wrong = false, message = listOfNotNull("The answer is shown.", review.current?.explanation?.takeIf { e -> e.isNotBlank() }).joinToString(" "))
        }
    }

    fun next() {
        review.next()
        showCurrent()
    }

    private fun showCurrent() {
        val item = review.current
        val position = review.position
        if (item == null || position == null) {
            if (item != null) {
                // A broken position: skip it rather than get stuck.
                review.next()
                return showCurrent()
            }
            mutableBoard.value = mutableBoard.value.copy(movable = null, version = mutableBoard.value.version + 1)
            mutableUi.value = MistakeReviewState(loading = false, finished = true, total = review.items.size, cleanCount = review.cleanCount)
            return
        }
        publish(position, previous = null, last = null, movable = true)
        mutableUi.value = MistakeReviewState(
            loading = false,
            number = review.index + 1,
            total = review.items.size,
            item = item,
            message = item.sourceDescription.ifBlank { "A position from one of your games." } + " Find the best move.",
            cleanCount = review.cleanCount,
        )
    }

    private fun publish(position: Position, previous: Position?, last: Move?, movable: Boolean) {
        mutableBoard.value = BoardSessionState(
            position = position,
            previous = previous,
            lastMove = last,
            version = mutableBoard.value.version + 1,
            animateLastMove = last != null,
            movable = if (movable) position.sideToMove else null,
        )
    }

    private fun save(item: ReviewItem?) {
        item ?: return
        viewModelScope.launch {
            repository.upsert(item)
            onChanged()
        }
    }

    class Factory(private val repository: ReviewRepository, private val onChanged: () -> Unit) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MistakeReviewViewModel(repository, onChanged) as T
    }
}
