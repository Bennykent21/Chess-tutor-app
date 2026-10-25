package com.chesstutor.app.domain.puzzle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/** Where a puzzle was played (plan section 6.3.2). Stored by name, so never rename one. */
enum class PuzzleMode { LEVEL, RUSH, DAILY, THEME, CUSTOM, REVIEW }

/** A finished puzzle, ready to be saved. */
data class AttemptRecord(
    val puzzle: Puzzle,
    val outcome: PuzzleOutcome,
    val mode: PuzzleMode,
    val level: Int?,
    val elapsedMs: Long,
    val finishedAt: Long,
)

/** What saving an attempt changed. */
data class AttemptResult(val progress: PuzzleProgress, val xpEarned: Int, val ratingDelta: Int)

/** What finishing a level play changed. */
data class LevelRunResult(val newlyPassed: Boolean, val worldCompleted: PuzzleWorld?, val bonusXp: Int)

/** Best result on one level of the path. */
data class LevelProgress(val level: Int, val bestScore: Int, val passed: Boolean, val plays: Int)

/** The numbers on the Puzzles home strip (plan section 6.3.1). */
data class PuzzleStats(
    val rating: Int = 1000,
    val totalXp: Int = 0,
    val playerLevel: PlayerLevel = PlayerLevel.fromXp(0),
    val dayStreak: Int = 0,
    val cleanStreak: Int = 0,
    val bestStreak: Int = 0,
    val solved: Int = 0,
    /** A puzzle was solved today, so the day streak is safe until midnight. */
    val solvedToday: Boolean = false,
)

/** Pure bookkeeping shared by every [PuzzleRepository], so the rules are tested once. */
object PuzzleBookkeeping {
    fun afterAttempt(progress: PuzzleProgress, record: AttemptRecord): AttemptResult {
        val next = progress.afterFinish(record.puzzle, record.outcome)
        val xp = XpRules.forSolve(
            SolveRecord(
                solved = record.outcome.solved,
                clean = record.outcome.clean,
                revealed = record.outcome.revealed,
                elapsedMs = record.elapsedMs,
                cleanStreak = if (record.outcome.clean) next.streak else 0,
            )
        )
        return AttemptResult(next, xp, next.rating - progress.rating)
    }

    /**
     * A level play's result. Passing a level for the first time earns a bonus; passing the
     * last level of a world for the first time also completes the world.
     */
    fun afterLevelRun(run: LevelRun, before: Map<Int, LevelProgress>): LevelRunResult {
        if (!run.passed || before[run.level]?.passed == true) return LevelRunResult(false, null, 0)
        val passedNow = before.filterValues { it.passed }.keys + run.level
        val world = PuzzleWorld.of(run.level)
        val completed = world.takeIf { w -> w.levels.all { it in passedNow } }
        val bonus = XpRules.LEVEL_PASSED + if (completed != null) XpRules.WORLD_COMPLETED else 0
        return LevelRunResult(true, completed, bonus)
    }
}

/**
 * Puzzle history (plan section 7.4): attempts, XP, rating and the level path. The app
 * stores it in Room; tests and previews use [InMemoryPuzzleRepository].
 */
interface PuzzleRepository {
    /** Rating, counters and every puzzle seen so far. */
    suspend fun loadProgress(seedRating: Int): PuzzleProgress

    suspend fun recordAttempt(record: AttemptRecord): AttemptResult

    suspend fun recordLevelRun(run: LevelRun, finishedAt: Long): LevelRunResult

    fun stats(today: () -> LocalDate = { LocalDate.now() }): Flow<PuzzleStats>

    fun levelProgress(): Flow<Map<Int, LevelProgress>>

    /** "I found another move that works" (plan section 6.3.5), saved on the phone. */
    suspend fun reportAnotherMove(puzzleId: String, fen: String, moveUci: String, note: String, at: Long)

    /** Puzzles to practise again: the latest attempt was not clean. Most recent first. */
    suspend fun mistakePuzzleIds(limit: Int = 100): List<String>

    /** Every finished puzzle, oldest first, for the Training page (rating history, themes). */
    fun history(): Flow<List<PuzzleHistoryEntry>> = flowOf(emptyList())
}

/** One finished puzzle as the Training page sees it. */
data class PuzzleHistoryEntry(
    val puzzleId: String,
    val solved: Boolean,
    val clean: Boolean,
    val ratingAfter: Int,
    val finishedAt: Long,
)
