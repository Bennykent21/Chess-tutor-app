package com.chesstutor.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Best score per course practice set (plan 6C, database version 13). */
@Entity(tableName = "course_practice")
data class CoursePracticeEntity(
    @PrimaryKey val courseId: String,
    val best: Int,
    val total: Int,
    val plays: Int,
    val lastPlayedAt: Long,
)

/** How well each opening trainer line is known (plan 6C, database version 13). */
@Entity(tableName = "opening_drill")
data class OpeningDrillEntity(
    @PrimaryKey val lineId: String,
    val stage: Int,
    val dueAt: Long,
    val attempts: Int,
    val cleanRuns: Int,
    val lastPlayedAt: Long,
)

@Dao
interface TrainingDao {
    @Query("SELECT * FROM course_practice")
    fun observePractice(): Flow<List<CoursePracticeEntity>>

    @Query("SELECT * FROM course_practice WHERE courseId = :courseId")
    suspend fun practice(courseId: String): CoursePracticeEntity?

    @Upsert
    suspend fun upsertPractice(row: CoursePracticeEntity)

    @Query("SELECT * FROM opening_drill")
    fun observeDrills(): Flow<List<OpeningDrillEntity>>

    @Upsert
    suspend fun upsertDrill(row: OpeningDrillEntity)
}
