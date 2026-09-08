package com.example.chess.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChessDao {
  @Query("SELECT * FROM mistake_book WHERE repetitionStage < 4 ORDER BY reviewDueTimestampMs ASC")
  fun getActiveMistakes(): Flow<List<MistakeRecord>>

  @Query("SELECT * FROM mistake_book WHERE reviewDueTimestampMs <= :nowMs AND repetitionStage < 4 LIMIT 10")
  suspend fun getDueMistakes(nowMs: Long): List<MistakeRecord>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMistake(mistake: MistakeRecord): Long

  @Update
  suspend fun updateMistake(mistake: MistakeRecord)

  @Query("SELECT * FROM user_progress WHERE id = :userId")
  fun getUserProgressFlow(userId: String = "default_user"): Flow<UserProgress?>

  @Query("SELECT * FROM user_progress WHERE id = :userId")
  suspend fun getUserProgress(userId: String = "default_user"): UserProgress?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertUserProgress(progress: UserProgress)
}

@Database(
  entities = [MistakeRecord::class, UserProgress::class],
  version = 2,
  exportSchema = false
)
abstract class ChessDatabase : RoomDatabase() {
  abstract fun chessDao(): ChessDao
}
