package com.chesstutor.app.domain.puzzle

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.Position
import com.chesstutor.app.domain.ReviewItem
import com.chesstutor.app.domain.ReviewScheduler
import java.time.Instant

/** What a move in the mistakes review meant. */
enum class DueMoveResult { CORRECT, WRONG, ILLEGAL, NOT_NOW }

/** A hint: first the piece to move, then where it goes. */
data class DueHint(val from: String, val to: String?)

/**
 * The spaced review of positions from the player's own games (Puzzles > Mistakes review,
 * plan 6.3 and 6.4): each due position once, in the order they fell due. The first try
 * decides the schedule ([ReviewScheduler]): right without help moves the position further
 * out, right with a hint keeps it close, and a wrong move or "Show answer" brings it back
 * now. Pure, so it is unit tested; the screen saves each [ReviewItem] this returns.
 */
class DueReview(items: List<ReviewItem>, private val now: () -> Instant = Instant::now) {
    val items: List<ReviewItem> = items.filter { ReviewScheduler.isDue(it, now()) }.sortedBy { it.dueAt }

    var index: Int = 0
        private set

    val current: ReviewItem? get() = items.getOrNull(index)
    val finished: Boolean get() = index >= items.size

    /** True once the current position is solved or its answer shown: the player moves on. */
    var done: Boolean = false
        private set
    var hintLevel: Int = 0
        private set

    /** The current position was missed (a wrong move or "Show answer"), so it is scheduled again now. */
    var missed: Boolean = false
        private set

    /** Positions solved on the first try without a hint. */
    var cleanCount: Int = 0
        private set

    val position: Position? get() = current?.let { Position.tryFromFen(it.fen).getOrNull() }

    /** The accepted answers: the engine's move and any alternatives saved with it. */
    val answers: Set<String> get() = current?.let { (listOf(it.bestMoveUci) + it.acceptedMovesUci).filter(String::isNotBlank).toSet() }.orEmpty()

    /**
     * Judges [uci]. Returns the result and, when the schedule changed, the updated item to
     * save. A mate counts even when it is not the saved move.
     */
    fun submit(uci: String): Pair<DueMoveResult, ReviewItem?> {
        val item = current ?: return DueMoveResult.NOT_NOW to null
        if (done) return DueMoveResult.NOT_NOW to null
        val start = position ?: return DueMoveResult.ILLEGAL to null
        val move = LegalMoveGenerator.generateLegalMoves(start).firstOrNull { it.uci == uci } ?: return DueMoveResult.ILLEGAL to null
        val mates = LegalMoveGenerator.isCheckmate(LegalMoveGenerator.makeMove(start, move))
        return if (uci in answers || mates) {
            done = true
            if (missed) {
                DueMoveResult.CORRECT to null
            } else {
                if (hintLevel == 0) cleanCount++
                DueMoveResult.CORRECT to ReviewScheduler.recordAttempt(item, correct = true, usedHint = hintLevel > 0, now = now())
            }
        } else {
            DueMoveResult.WRONG to miss(item)
        }
    }

    /** The next hint level, or null when there is nothing more to show. */
    fun hint(): DueHint? {
        if (done) return null
        val best = current?.bestMoveUci?.takeIf { it.length >= 4 } ?: return null
        hintLevel = (hintLevel + 1).coerceAtMost(2)
        return DueHint(best.substring(0, 2), if (hintLevel >= 2) best.substring(2, 4) else null)
    }

    /** "Show answer": the engine's move, and the item to save (scheduled again now) if not missed yet. */
    fun reveal(): Pair<Move?, ReviewItem?> {
        val item = current ?: return null to null
        if (done) return null to null
        val start = position
        val best = start?.let { p -> LegalMoveGenerator.generateLegalMoves(p).firstOrNull { it.uci == item.bestMoveUci } }
        val saved = miss(item)
        done = true
        return best to saved
    }

    fun next() {
        if (finished) return
        index++
        done = false
        hintLevel = 0
        missed = false
    }

    private fun miss(item: ReviewItem): ReviewItem? {
        if (missed) return null
        missed = true
        return ReviewScheduler.recordAttempt(item, correct = false, usedHint = hintLevel > 0, now = now())
    }
}
