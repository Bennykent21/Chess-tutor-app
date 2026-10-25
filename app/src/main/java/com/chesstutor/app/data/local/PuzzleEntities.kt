package com.chesstutor.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One finished puzzle (plan section 7.4). Replaces the SharedPreferences counters. */
@Entity(tableName = "puzzle_attempt", indices = [Index("puzzleId"), Index("finishedAt")])
data class PuzzleAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val puzzleId: String,
    val puzzleRating: Int,
    /** Where it was played: LEVEL, RUSH, DAILY, THEME, CUSTOM or REVIEW. */
    val mode: String,
    val levelNumber: Int?,
    val solved: Boolean,
    val clean: Boolean,
    val viaAlternative: Boolean,
    val revealed: Boolean,
    val mistakes: Int,
    val hintsUsed: Int,
    val elapsedMs: Long,
    val ratingBefore: Int,
    val ratingAfter: Int,
    val xpEarned: Int,
    val finishedAt: Long,
)

/** The XP ledger: every award, so levels and totals can always be recomputed. */
@Entity(tableName = "xp_event", indices = [Index("createdAt")])
data class XpEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Int,
    /** SOLVE, LEVEL_PASSED or WORLD_COMPLETED. */
    val reason: String,
    val attemptId: Long?,
    val createdAt: Long,
)

/** The player's puzzle rating and counters: one row, id 1. */
@Entity(tableName = "puzzle_profile")
data class PuzzleProfileEntity(
    @PrimaryKey val id: Int = 1,
    val rating: Int,
    val attempts: Int,
    val solved: Int,
    val cleanSolves: Int,
    val streak: Int,
    val bestStreak: Int,
    /** Puzzles seen before the move to Room (comma-separated ids), so they are not served again soon. */
    val legacySeenIds: String,
    val updatedAt: Long,
)

/** Best result per level of the path. */
@Entity(tableName = "puzzle_level_progress")
data class LevelProgressEntity(
    @PrimaryKey val levelNumber: Int,
    val bestScore: Int,
    val passed: Boolean,
    val plays: Int,
    val lastPlayedAt: Long,
)

/** "I found another move that works": saved on the phone to tune the pack later. */
@Entity(tableName = "report_issue", indices = [Index("puzzleId")])
data class ReportIssueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val puzzleId: String,
    val fen: String,
    val moveUci: String,
    val note: String,
    val createdAt: Long,
)

@Dao
interface PuzzleDao {
    @Insert
    suspend fun insertAttempt(attempt: PuzzleAttemptEntity): Long

    @Insert
    suspend fun insertXp(event: XpEventEntity): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_event")
    fun totalXp(): Flow<Int>

    @Query("SELECT * FROM puzzle_profile WHERE id = 1")
    suspend fun profile(): PuzzleProfileEntity?

    @Query("SELECT * FROM puzzle_profile WHERE id = 1")
    fun profileFlow(): Flow<PuzzleProfileEntity?>

    @Upsert
    suspend fun upsertProfile(profile: PuzzleProfileEntity)

    @Query("SELECT DISTINCT puzzleId FROM puzzle_attempt")
    suspend fun attemptedIds(): List<String>

    /** When each solved puzzle was finished, for the day streak. */
    @Query("SELECT finishedAt FROM puzzle_attempt WHERE solved = 1")
    fun solvedTimes(): Flow<List<Long>>

    @Query("SELECT * FROM puzzle_level_progress")
    fun levelProgress(): Flow<List<LevelProgressEntity>>

    @Query("SELECT * FROM puzzle_level_progress")
    suspend fun allLevels(): List<LevelProgressEntity>

    @Upsert
    suspend fun upsertLevel(progress: LevelProgressEntity)

    /** Puzzles whose latest attempt was not clean, most recent first: material for mistakes review. */
    @Query(
        "SELECT a.* FROM puzzle_attempt a " +
            "JOIN (SELECT puzzleId, MAX(finishedAt) AS lastAt FROM puzzle_attempt GROUP BY puzzleId) l " +
            "ON a.puzzleId = l.puzzleId AND a.finishedAt = l.lastAt " +
            "WHERE a.clean = 0 ORDER BY a.finishedAt DESC LIMIT :limit"
    )
    suspend fun latestUncleanAttempts(limit: Int): List<PuzzleAttemptEntity>

    @Query("SELECT puzzleId, solved, clean, ratingAfter, finishedAt FROM puzzle_attempt ORDER BY finishedAt")
    fun history(): Flow<List<PuzzleHistoryRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReport(report: ReportIssueEntity): Long

    @Query("SELECT COUNT(*) FROM report_issue")
    suspend fun reportCount(): Int
}

/** One row of the puzzle history (a subset of [PuzzleAttemptEntity]). */
data class PuzzleHistoryRow(
    val puzzleId: String,
    val solved: Boolean,
    val clean: Boolean,
    val ratingAfter: Int,
    val finishedAt: Long,
)
