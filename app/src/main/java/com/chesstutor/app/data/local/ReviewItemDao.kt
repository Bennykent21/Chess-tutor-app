package com.chesstutor.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReviewItemDao {
    @Query("SELECT * FROM review_items ORDER BY dueAt ASC")
    suspend fun getAll(): List<ReviewItemEntity>

    @Query("SELECT * FROM review_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ReviewItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ReviewItemEntity)

    @Query("DELETE FROM review_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM review_items")
    suspend fun clearAll()
}
