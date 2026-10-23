package com.chesstutor.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Step and completion per lesson (plan 7.4 `lesson_progress`, database version 10). */
@Entity(tableName = "lesson_progress")
data class LessonProgressEntity(
    @PrimaryKey val lessonId: String,
    val stepIndex: Int,
    val stepCount: Int,
    val completed: Boolean,
    val plays: Int,
    val bestCleanChallenges: Int,
    val challengeCount: Int,
    val perfect: Boolean,
    val xpEarned: Int,
    val completedAt: Long?,
    val updatedAt: Long,
)

@Dao
interface LessonProgressDao {
    @Query("SELECT * FROM lesson_progress")
    fun observeAll(): Flow<List<LessonProgressEntity>>

    @Query("SELECT * FROM lesson_progress WHERE lessonId = :lessonId")
    suspend fun get(lessonId: String): LessonProgressEntity?

    @Upsert
    suspend fun upsert(progress: LessonProgressEntity)
}
