package com.chesstutor.app.data.puzzle

import androidx.room.withTransaction
import com.chesstutor.app.data.local.AppDatabase
import com.chesstutor.app.data.local.LevelProgressEntity
import com.chesstutor.app.data.local.PuzzleAttemptEntity
import com.chesstutor.app.data.local.PuzzleDao
import com.chesstutor.app.data.local.PuzzleProfileEntity
import com.chesstutor.app.data.local.ReportIssueEntity
import com.chesstutor.app.data.local.XpEventEntity
import com.chesstutor.app.domain.puzzle.AttemptRecord
import com.chesstutor.app.domain.puzzle.AttemptResult
import com.chesstutor.app.domain.puzzle.DayStreak
import com.chesstutor.app.domain.puzzle.LevelProgress
import com.chesstutor.app.domain.puzzle.LevelRun
import com.chesstutor.app.domain.puzzle.LevelRunResult
import com.chesstutor.app.domain.puzzle.PlayerLevel
import com.chesstutor.app.domain.puzzle.PuzzleBookkeeping
import com.chesstutor.app.domain.puzzle.PuzzleHistoryEntry
import com.chesstutor.app.domain.puzzle.PuzzleProgress
import com.chesstutor.app.domain.puzzle.PuzzleRating
import com.chesstutor.app.domain.puzzle.PuzzleReport
import com.chesstutor.app.domain.puzzle.PuzzleRepository
import com.chesstutor.app.domain.puzzle.PuzzleStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * [PuzzleRepository] in Room (plan section 7.4). The first time it runs, the progress the
 * old SharedPreferences store kept ([legacy]) is copied in once, so no rating, counter or
 * seen puzzle is lost.
 */
class RoomPuzzleRepository(
    private val db: AppDatabase,
    private val legacy: () -> PuzzleProgress? = { null },
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
) : PuzzleRepository {
    private val dao: PuzzleDao = db.puzzleDao()

    override suspend fun loadProgress(seedRating: Int): PuzzleProgress = db.withTransaction {
        val profile = profileOrImport(seedRating)
        val seen = profile.legacySeenIds.split(',').filter { it.isNotBlank() }.toSet() + dao.attemptedIds()
        profile.toProgress(seen)
    }

    /** The saved profile, creating it from the old store (or [seedRating]) the first time. */
    private suspend fun profileOrImport(seedRating: Int): PuzzleProfileEntity {
        dao.profile()?.let { return it }
        val start = legacy() ?: PuzzleProgress(rating = seedRating.coerceIn(PuzzleRating.MIN, PuzzleRating.MAX))
        val created = start.toEntity(legacySeen = start.seenIds.joinToString(","), at = now())
        dao.upsertProfile(created)
        return created
    }

    override suspend fun recordAttempt(record: AttemptRecord): AttemptResult = db.withTransaction {
        val profile = profileOrImport(record.puzzle.rating)
        val result = PuzzleBookkeeping.afterAttempt(profile.toProgress(emptySet()), record)
        val attemptId = dao.insertAttempt(
            PuzzleAttemptEntity(
                puzzleId = record.puzzle.id,
                puzzleRating = record.puzzle.rating,
                mode = record.mode.name,
                levelNumber = record.level,
                solved = record.outcome.solved,
                clean = record.outcome.clean,
                viaAlternative = record.outcome.viaAlternative,
                revealed = record.outcome.revealed,
                mistakes = record.outcome.mistakes,
                hintsUsed = record.outcome.hintsUsed,
                elapsedMs = record.elapsedMs,
                ratingBefore = profile.rating,
                ratingAfter = result.progress.rating,
                xpEarned = result.xpEarned,
                finishedAt = record.finishedAt,
            )
        )
        if (result.xpEarned > 0) {
            dao.insertXp(XpEventEntity(amount = result.xpEarned, reason = "SOLVE", attemptId = attemptId, createdAt = record.finishedAt))
        }
        dao.upsertProfile(result.progress.toEntity(legacySeen = profile.legacySeenIds, at = record.finishedAt))
        result
    }

    override suspend fun recordLevelRun(run: LevelRun, finishedAt: Long): LevelRunResult = db.withTransaction {
        val before = currentLevels()
        val result = PuzzleBookkeeping.afterLevelRun(run, before)
        val old = before[run.level]
        dao.upsertLevel(
            LevelProgressEntity(
                levelNumber = run.level,
                bestScore = maxOf(old?.bestScore ?: 0, run.solvedWithoutReveal),
                passed = old?.passed == true || run.passed,
                plays = (old?.plays ?: 0) + 1,
                lastPlayedAt = finishedAt,
            )
        )
        if (result.newlyPassed) {
            dao.insertXp(XpEventEntity(amount = result.bonusXp, reason = if (result.worldCompleted != null) "WORLD_COMPLETED" else "LEVEL_PASSED", attemptId = null, createdAt = finishedAt))
        }
        result
    }

    private suspend fun currentLevels(): Map<Int, LevelProgress> =
        dao.allLevels().map { it.toDomain() }.associateBy { it.level }

    override fun stats(today: () -> LocalDate): Flow<PuzzleStats> =
        combine(dao.profileFlow(), dao.totalXp(), dao.solvedTimes()) { profile, total, times ->
            val days = times.mapTo(HashSet()) { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            PuzzleStats(
                rating = profile?.rating ?: PuzzleStats().rating,
                totalXp = total,
                playerLevel = PlayerLevel.fromXp(total),
                dayStreak = DayStreak.compute(days, today()),
                solvedToday = today() in days,
                cleanStreak = profile?.streak ?: 0,
                bestStreak = profile?.bestStreak ?: 0,
                solved = profile?.solved ?: 0,
            )
        }

    override fun levelProgress(): Flow<Map<Int, LevelProgress>> =
        dao.levelProgress().map { rows -> rows.map { it.toDomain() }.associateBy { it.level } }

    override suspend fun reportAnotherMove(puzzleId: String, fen: String, moveUci: String, note: String, at: Long) {
        dao.insertReport(ReportIssueEntity(puzzleId = puzzleId, fen = fen, moveUci = moveUci, note = note, createdAt = at))
    }

    override suspend fun mistakePuzzleIds(limit: Int): List<String> =
        dao.latestUncleanAttempts(limit).map { it.puzzleId }

    override suspend fun savedReports(): List<PuzzleReport> =
        dao.reports().map { PuzzleReport(it.puzzleId, it.fen, it.moveUci, it.note, it.createdAt) }

    override suspend fun clearReports() = dao.clearReports()

    override fun history(): Flow<List<PuzzleHistoryEntry>> =
        dao.history().map { rows -> rows.map { PuzzleHistoryEntry(it.puzzleId, it.solved, it.clean, it.ratingAfter, it.finishedAt) } }

    private fun PuzzleProfileEntity.toProgress(seen: Set<String>) = PuzzleProgress(
        rating = rating,
        seenIds = seen,
        attempts = attempts,
        solved = solved,
        cleanSolves = cleanSolves,
        streak = streak,
        bestStreak = bestStreak,
    )

    private fun PuzzleProgress.toEntity(legacySeen: String, at: Long) = PuzzleProfileEntity(
        rating = rating,
        attempts = attempts,
        solved = solved,
        cleanSolves = cleanSolves,
        streak = streak,
        bestStreak = bestStreak,
        legacySeenIds = legacySeen,
        updatedAt = at,
    )

    private fun LevelProgressEntity.toDomain() = LevelProgress(levelNumber, bestScore, passed, plays)
}
