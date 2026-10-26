package com.chesstutor.app.domain.puzzle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** [PuzzleRepository] in memory, for tests and previews. Same rules as the Room one. */
class InMemoryPuzzleRepository(
    private val seed: PuzzleProgress? = null,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : PuzzleRepository {
    private val mutex = Mutex()
    private val progress = MutableStateFlow<PuzzleProgress?>(null)
    private val xp = MutableStateFlow(0)
    private val solvedTimes = MutableStateFlow<List<Long>>(emptyList())
    private val levels = MutableStateFlow<Map<Int, LevelProgress>>(emptyMap())
    private val latestCleanById = LinkedHashMap<String, Boolean>()
    private val attempts = MutableStateFlow<List<PuzzleHistoryEntry>>(emptyList())
    val reports = mutableListOf<String>()

    override suspend fun loadProgress(seedRating: Int): PuzzleProgress = mutex.withLock { current(seedRating) }

    private fun current(seedRating: Int): PuzzleProgress =
        progress.value ?: (seed ?: PuzzleProgress(rating = seedRating.coerceIn(PuzzleRating.MIN, PuzzleRating.MAX)))
            .also { progress.value = it }

    override suspend fun recordAttempt(record: AttemptRecord): AttemptResult = mutex.withLock {
        val result = PuzzleBookkeeping.afterAttempt(current(record.puzzle.rating), record)
        progress.value = result.progress
        xp.value += result.xpEarned
        if (record.outcome.solved) solvedTimes.value = solvedTimes.value + record.finishedAt
        latestCleanById.remove(record.puzzle.id)
        latestCleanById[record.puzzle.id] = record.outcome.clean
        attempts.value = attempts.value + PuzzleHistoryEntry(
            record.puzzle.id, record.outcome.solved, record.outcome.clean, result.progress.rating, record.finishedAt,
        )
        result
    }

    override suspend fun recordLevelRun(run: LevelRun, finishedAt: Long): LevelRunResult = mutex.withLock {
        val before = levels.value
        val result = PuzzleBookkeeping.afterLevelRun(run, before)
        val old = before[run.level]
        levels.value = before + (run.level to LevelProgress(
            level = run.level,
            bestScore = maxOf(old?.bestScore ?: 0, run.solvedWithoutReveal),
            passed = old?.passed == true || run.passed,
            plays = (old?.plays ?: 0) + 1,
        ))
        xp.value += result.bonusXp
        result
    }

    override fun stats(today: () -> LocalDate): Flow<PuzzleStats> =
        combine(progress, xp, solvedTimes) { p, total, times ->
            val days = times.mapTo(HashSet()) { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            PuzzleStats(
                rating = p?.rating ?: PuzzleStats().rating,
                totalXp = total,
                playerLevel = PlayerLevel.fromXp(total),
                dayStreak = DayStreak.compute(days, today()),
                solvedToday = today() in days,
                cleanStreak = p?.streak ?: 0,
                bestStreak = p?.bestStreak ?: 0,
                solved = p?.solved ?: 0,
            )
        }

    override fun levelProgress(): Flow<Map<Int, LevelProgress>> = levels.map { it }

    override suspend fun reportAnotherMove(puzzleId: String, fen: String, moveUci: String, note: String, at: Long) {
        mutex.withLock {
            reports += "$puzzleId $moveUci"
            saved += PuzzleReport(puzzleId, fen, moveUci, note, at)
        }
    }

    private val saved = mutableListOf<PuzzleReport>()

    override suspend fun savedReports(): List<PuzzleReport> = mutex.withLock { saved.toList() }

    override suspend fun clearReports() = mutex.withLock {
        saved.clear()
        reports.clear()
    }

    override suspend fun mistakePuzzleIds(limit: Int): List<String> = mutex.withLock {
        latestCleanById.entries.reversed().filter { !it.value }.map { it.key }.take(limit)
    }

    override fun history(): Flow<List<PuzzleHistoryEntry>> = attempts
}
