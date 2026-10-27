package com.chesstutor.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LearningDao {
    @Query("SELECT * FROM learning_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfile(): LearningProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: LearningProfileEntity)

    /** The classic curriculum's progress, kept only so a data reset can clear it. */
    @Query("DELETE FROM module_progress")
    suspend fun clearModuleProgress()
}
