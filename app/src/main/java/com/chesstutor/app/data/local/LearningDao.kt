package com.chesstutor.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LearningDao {
    @Query("SELECT * FROM learning_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfile(): LearningProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: LearningProfileEntity)

    @Query("SELECT * FROM module_progress ORDER BY moduleId")
    suspend fun getAllModuleProgress(): List<ModuleProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModuleProgress(progress: ModuleProgressEntity)

    @Query("DELETE FROM module_progress")
    suspend fun clearModuleProgress()

    @Query("SELECT * FROM module_progress ORDER BY moduleId")
    fun observeModuleProgress(): Flow<List<ModuleProgressEntity>>
}
