package com.chesstutor.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The engine's view of one position of a saved game (plan 7.4 `analysis_result`, database version 11). */
@Entity(tableName = "analysis_result", primaryKeys = ["gameId", "ply"])
data class AnalysisResultEntity(
    val gameId: String,
    val ply: Int,
    val centipawns: Int?,
    val mate: Int?,
    val bestMove: String,
    /** The engine's line in UCI, separated by spaces. */
    val line: String,
    val depth: Int?,
    val lite: Boolean,
)

/** One finished analysis per game: accuracy for both sides (database version 11). */
@Entity(tableName = "game_analysis")
data class GameAnalysisEntity(
    @PrimaryKey val gameId: String,
    val plies: Int,
    val whiteAccuracy: Double?,
    val blackAccuracy: Double?,
    val lite: Boolean,
    val analysedAt: Long,
)

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM analysis_result WHERE gameId = :gameId ORDER BY ply")
    fun observeEvals(gameId: String): Flow<List<AnalysisResultEntity>>

    @Query("SELECT * FROM analysis_result WHERE gameId = :gameId ORDER BY ply")
    suspend fun evals(gameId: String): List<AnalysisResultEntity>

    @Upsert
    suspend fun upsertEval(eval: AnalysisResultEntity)

    @Query("SELECT * FROM game_analysis")
    fun observeSummaries(): Flow<List<GameAnalysisEntity>>

    @Upsert
    suspend fun upsertSummary(summary: GameAnalysisEntity)

    @Query("DELETE FROM analysis_result WHERE gameId = :gameId")
    suspend fun deleteEvals(gameId: String)

    @Query("DELETE FROM game_analysis WHERE gameId = :gameId")
    suspend fun deleteSummary(gameId: String)
}
