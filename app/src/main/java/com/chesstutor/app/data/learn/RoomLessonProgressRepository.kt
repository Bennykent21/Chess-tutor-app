package com.chesstutor.app.data.learn

import androidx.room.withTransaction
import com.chesstutor.app.data.local.AppDatabase
import com.chesstutor.app.data.local.LessonProgressEntity
import com.chesstutor.app.data.local.XpEventEntity
import com.chesstutor.app.domain.learn.Lesson
import com.chesstutor.app.domain.learn.LessonCompletion
import com.chesstutor.app.domain.learn.LessonProgress
import com.chesstutor.app.domain.learn.LessonProgressRepository
import com.chesstutor.app.domain.learn.LessonResult
import com.chesstutor.app.domain.learn.LessonXp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Lesson progress in Room (`lesson_progress`, database version 10); XP goes to the shared `xp_event` ledger. */
class RoomLessonProgressRepository(
    private val db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : LessonProgressRepository {
    private val dao = db.lessonProgressDao()

    override fun observe(): Flow<Map<String, LessonProgress>> =
        dao.observeAll().map { rows -> rows.associate { it.lessonId to it.toDomain() } }

    override suspend fun get(lessonId: String): LessonProgress? = dao.get(lessonId)?.toDomain()

    override suspend fun saveStep(lessonId: String, stepIndex: Int, stepCount: Int) = db.withTransaction {
        val current = dao.get(lessonId)?.toDomain() ?: LessonProgress(lessonId)
        dao.upsert(current.copy(stepIndex = stepIndex, stepCount = stepCount).toEntity(now()))
    }

    override suspend fun complete(lesson: Lesson, result: LessonResult): LessonCompletion = db.withTransaction {
        val at = now()
        val completion = LessonXp.complete(lesson, dao.get(lesson.id)?.toDomain(), result, at)
        dao.upsert(completion.progress.toEntity(at))
        if (completion.xp > 0) db.puzzleDao().insertXp(XpEventEntity(amount = completion.xp, reason = XP_REASON, attemptId = null, createdAt = at))
        completion
    }

    companion object {
        /** The `xp_event.reason` for lesson XP. */
        const val XP_REASON = "LESSON"

        fun LessonProgressEntity.toDomain() = LessonProgress(
            lessonId = lessonId, stepIndex = stepIndex, stepCount = stepCount, completed = completed, plays = plays,
            bestCleanChallenges = bestCleanChallenges, challengeCount = challengeCount, perfect = perfect,
            xpEarned = xpEarned, completedAt = completedAt,
        )

        fun LessonProgress.toEntity(updatedAt: Long) = LessonProgressEntity(
            lessonId = lessonId, stepIndex = stepIndex, stepCount = stepCount, completed = completed, plays = plays,
            bestCleanChallenges = bestCleanChallenges, challengeCount = challengeCount, perfect = perfect,
            xpEarned = xpEarned, completedAt = completedAt, updatedAt = updatedAt,
        )
    }
}

class InMemoryLessonProgressRepository(private val now: () -> Long = { 0L }) : LessonProgressRepository {
    private val rows = MutableStateFlow<Map<String, LessonProgress>>(emptyMap())
    /** Every XP award, as the Room version would write to `xp_event`. */
    val xpAwards = mutableListOf<Int>()

    override fun observe(): Flow<Map<String, LessonProgress>> = rows
    override suspend fun get(lessonId: String): LessonProgress? = rows.value[lessonId]

    override suspend fun saveStep(lessonId: String, stepIndex: Int, stepCount: Int) {
        val current = rows.value[lessonId] ?: LessonProgress(lessonId)
        rows.value = rows.value + (lessonId to current.copy(stepIndex = stepIndex, stepCount = stepCount))
    }

    override suspend fun complete(lesson: Lesson, result: LessonResult): LessonCompletion {
        val completion = LessonXp.complete(lesson, rows.value[lesson.id], result, now())
        rows.value = rows.value + (lesson.id to completion.progress)
        if (completion.xp > 0) xpAwards += completion.xp
        return completion
    }
}
