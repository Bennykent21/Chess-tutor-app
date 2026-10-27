package com.chesstutor.app.data.learn

import androidx.room.withTransaction
import com.chesstutor.app.data.local.AppDatabase
import com.chesstutor.app.data.local.CoursePracticeEntity
import com.chesstutor.app.data.local.OpeningDrillEntity
import com.chesstutor.app.domain.learn.PracticeRepository
import com.chesstutor.app.domain.learn.PracticeResult
import com.chesstutor.app.domain.opening.DrillProgress
import com.chesstutor.app.domain.opening.OpeningDrillRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Practice-set scores and opening drills in Room (`course_practice`, `opening_drill`). */
class RoomTrainingRepository(private val db: AppDatabase) : PracticeRepository, OpeningDrillRepository {
    private val dao get() = db.trainingDao()

    override fun observePractice(): Flow<Map<String, PracticeResult>> =
        dao.observePractice().map { rows -> rows.associate { it.courseId to PracticeResult(it.courseId, it.best, it.total, it.plays, it.lastPlayedAt) } }

    override suspend fun recordPractice(courseId: String, score: Int, total: Int, at: Long): PracticeResult = db.withTransaction {
        val old = dao.practice(courseId)
        val row = CoursePracticeEntity(courseId, maxOf(old?.best ?: 0, score), total, (old?.plays ?: 0) + 1, at)
        dao.upsertPractice(row)
        PracticeResult(row.courseId, row.best, row.total, row.plays, row.lastPlayedAt)
    }

    override fun observeDrills(): Flow<Map<String, DrillProgress>> =
        dao.observeDrills().map { rows -> rows.associate { it.lineId to DrillProgress(it.lineId, it.stage, it.dueAt, it.attempts, it.cleanRuns, it.lastPlayedAt) } }

    override suspend fun saveDrill(progress: DrillProgress) =
        dao.upsertDrill(OpeningDrillEntity(progress.lineId, progress.stage, progress.dueAt, progress.attempts, progress.cleanRuns, progress.lastPlayedAt))
}

/** Test double. */
class InMemoryTrainingRepository : PracticeRepository, OpeningDrillRepository {
    private val practice = MutableStateFlow<Map<String, PracticeResult>>(emptyMap())
    private val drills = MutableStateFlow<Map<String, DrillProgress>>(emptyMap())

    override fun observePractice(): Flow<Map<String, PracticeResult>> = practice

    override suspend fun recordPractice(courseId: String, score: Int, total: Int, at: Long): PracticeResult {
        val old = practice.value[courseId]
        val row = PracticeResult(courseId, maxOf(old?.best ?: 0, score), total, (old?.plays ?: 0) + 1, at)
        practice.value = practice.value + (courseId to row)
        return row
    }

    override fun observeDrills(): Flow<Map<String, DrillProgress>> = drills

    override suspend fun saveDrill(progress: DrillProgress) {
        drills.value = drills.value + (progress.lineId to progress)
    }
}
