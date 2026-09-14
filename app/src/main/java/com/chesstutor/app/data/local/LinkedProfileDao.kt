package com.chesstutor.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkedProfileDao {
    @Query("SELECT * FROM linked_profile WHERE id = 1 LIMIT 1")
    fun getLinkedProfileFlow(): Flow<LinkedProfileEntity?>

    @Query("SELECT * FROM linked_profile WHERE id = 1 LIMIT 1")
    suspend fun getLinkedProfile(): LinkedProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: LinkedProfileEntity)

    @Query("DELETE FROM linked_profile WHERE id = 1")
    suspend fun clear()
}
