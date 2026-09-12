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

  // Repertoire Methods
  @Query("SELECT * FROM repertoire ORDER BY createdAtTimestampMs DESC")
  fun getAllRepertoiresFlow(): Flow<List<RepertoireEntity>>

  @Query("SELECT * FROM repertoire WHERE side = :side ORDER BY createdAtTimestampMs DESC")
  fun getRepertoiresBySideFlow(side: String): Flow<List<RepertoireEntity>>

  @Query("SELECT * FROM repertoire WHERE id = :id")
  suspend fun getRepertoireById(id: Long): RepertoireEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertRepertoire(repertoire: RepertoireEntity): Long

  @Update
  suspend fun updateRepertoire(repertoire: RepertoireEntity)

  @Query("DELETE FROM repertoire WHERE id = :id")
  suspend fun deleteRepertoire(id: Long)

  @Query("SELECT * FROM repertoire_position WHERE repertoireId = :repertoireId ORDER BY id ASC")
  fun getPositionsForRepertoireFlow(repertoireId: Long): Flow<List<RepertoirePositionEntity>>

  @Query("SELECT * FROM repertoire_position WHERE repertoireId = :repertoireId ORDER BY id ASC")
  suspend fun getPositionsForRepertoireSync(repertoireId: Long): List<RepertoirePositionEntity>

  @Query("SELECT * FROM repertoire_position WHERE status = 'WEAK' ORDER BY lastReviewedAt ASC")
  fun getWeakPositionsFlow(): Flow<List<RepertoirePositionEntity>>

  @Query("SELECT COUNT(*) FROM repertoire_position WHERE repertoireId = :repertoireId")
  suspend fun getPositionCount(repertoireId: Long): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertRepertoirePosition(pos: RepertoirePositionEntity): Long

  @Update
  suspend fun updateRepertoirePosition(pos: RepertoirePositionEntity)

  @Query("UPDATE repertoire_position SET status = :status, lastReviewedAt = :timestamp, reviewCount = reviewCount + 1 WHERE id = :id")
  suspend fun updatePositionStatus(id: Long, status: String, timestamp: Long = System.currentTimeMillis())

  @Query("UPDATE repertoire_position SET isCritical = CASE WHEN isCritical = 1 THEN 0 ELSE 1 END WHERE id = :id")
  suspend fun togglePositionCritical(id: Long)


  @Query("DELETE FROM repertoire_position WHERE id = :id")
  suspend fun deleteRepertoirePosition(id: Long)

  @Query("DELETE FROM repertoire_position WHERE repertoireId = :repertoireId")
  suspend fun deletePositionsByRepertoire(repertoireId: Long)
}

@Database(
  entities = [MistakeRecord::class, UserProgress::class, RepertoireEntity::class, RepertoirePositionEntity::class],
  version = 3,
  exportSchema = false
)
abstract class ChessDatabase : RoomDatabase() {
  abstract fun chessDao(): ChessDao
}

