package com.chesstutor.app.domain.puzzle

import kotlin.math.max

/** Everything about the player's puzzle history that must survive an app restart. */
data class PuzzleProgress(
    val rating: Int = 1000,
    /** Puzzles already finished (solved or revealed), so they are not served again soon. */
    val seenIds: Set<String> = emptySet(),
    val attempts: Int = 0,
    /** Puzzles solved (with or without help). */
    val solved: Int = 0,
    /** Puzzles solved with no mistakes, hints or reveal. */
    val cleanSolves: Int = 0,
    /** Consecutive clean solves. */
    val streak: Int = 0,
    val bestStreak: Int = 0
) {
    fun afterFinish(puzzle: Puzzle, outcome: PuzzleOutcome): PuzzleProgress {
        val newStreak = if (outcome.clean) streak + 1 else 0
        return copy(
            rating = PuzzleRating.update(rating, puzzle.rating, solved = outcome.clean),
            seenIds = seenIds + puzzle.id,
            attempts = attempts + 1,
            solved = solved + if (outcome.solved) 1 else 0,
            cleanSolves = cleanSolves + if (outcome.clean) 1 else 0,
            streak = newStreak,
            bestStreak = max(bestStreak, newStreak)
        )
    }
}

/** Where [PuzzleProgress] is kept. The Android implementation uses SharedPreferences. */
interface PuzzleProgressStore {
    /** [seedRating] is used as the starting rating when nothing has been saved yet. */
    fun load(seedRating: Int): PuzzleProgress
    fun save(progress: PuzzleProgress)
}

class InMemoryPuzzleProgressStore(private var saved: PuzzleProgress? = null) : PuzzleProgressStore {
    override fun load(seedRating: Int): PuzzleProgress =
        saved ?: PuzzleProgress(rating = seedRating.coerceIn(PuzzleRating.MIN, PuzzleRating.MAX))

    override fun save(progress: PuzzleProgress) {
        saved = progress
    }
}
