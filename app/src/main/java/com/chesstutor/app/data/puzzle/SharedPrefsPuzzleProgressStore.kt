package com.chesstutor.app.data.puzzle

import android.content.Context
import com.chesstutor.app.domain.puzzle.PuzzleProgress
import com.chesstutor.app.domain.puzzle.PuzzleProgressStore
import com.chesstutor.app.domain.puzzle.PuzzleRating

/** Keeps the player's puzzle rating, streaks and seen puzzles in SharedPreferences. */
class SharedPrefsPuzzleProgressStore(context: Context) : PuzzleProgressStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(seedRating: Int): PuzzleProgress {
        val seed = seedRating.coerceIn(PuzzleRating.MIN, PuzzleRating.MAX)
        return PuzzleProgress(
            rating = prefs.getInt(KEY_RATING, seed),
            seenIds = prefs.getStringSet(KEY_SEEN, null)?.toSet() ?: emptySet(),
            attempts = prefs.getInt(KEY_ATTEMPTS, 0),
            solved = prefs.getInt(KEY_SOLVED, 0),
            cleanSolves = prefs.getInt(KEY_CLEAN, 0),
            streak = prefs.getInt(KEY_STREAK, 0),
            bestStreak = prefs.getInt(KEY_BEST_STREAK, 0)
        )
    }

    override fun save(progress: PuzzleProgress) {
        prefs.edit()
            .putInt(KEY_RATING, progress.rating)
            .putStringSet(KEY_SEEN, progress.seenIds.toSet())
            .putInt(KEY_ATTEMPTS, progress.attempts)
            .putInt(KEY_SOLVED, progress.solved)
            .putInt(KEY_CLEAN, progress.cleanSolves)
            .putInt(KEY_STREAK, progress.streak)
            .putInt(KEY_BEST_STREAK, progress.bestStreak)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "puzzle_progress"
        const val KEY_RATING = "rating"
        const val KEY_SEEN = "seen_ids"
        const val KEY_ATTEMPTS = "attempts"
        const val KEY_SOLVED = "solved"
        const val KEY_CLEAN = "clean_solves"
        const val KEY_STREAK = "streak"
        const val KEY_BEST_STREAK = "best_streak"
    }
}
