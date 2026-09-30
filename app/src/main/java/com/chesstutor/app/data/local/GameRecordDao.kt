package com.chesstutor.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameRecordDao {
    @Query("SELECT * FROM game_records ORDER BY dateMillis DESC")
    fun observeAllGames(): Flow<List<GameRecordEntity>>

    @Query("SELECT * FROM game_records ORDER BY dateMillis DESC LIMIT :limit")
    suspend fun getRecentGames(limit: Int = 20): List<GameRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: GameRecordEntity)

    @Query("DELETE FROM game_records WHERE id = :id")
    suspend fun deleteGame(id: String)

    @Query("DELETE FROM game_records")
    suspend fun clearAll()
}
