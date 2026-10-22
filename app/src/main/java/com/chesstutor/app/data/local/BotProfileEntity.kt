package com.chesstutor.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A bot the player built ("My bots", plan 7.4 `bot_profile`). Opening names are joined with "|". */
@Entity(tableName = "bot_profile")
data class BotProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val rating: Int,
    val style: String,
    val aggression: Float,
    val trades: Float,
    val mistakeLevel: Float,
    val thinkSpeed: String,
    val resign: String,
    val whiteOpenings: String,
    val blackOpenings: String,
    val face: String,
    val timeControl: String,
    val takebacks: Boolean,
    val hints: Boolean,
    val evalBar: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface BotProfileDao {
    @Query("SELECT * FROM bot_profile ORDER BY createdAt")
    fun observeAll(): Flow<List<BotProfileEntity>>

    @Query("SELECT * FROM bot_profile WHERE id = :id")
    suspend fun get(id: String): BotProfileEntity?

    @Upsert
    suspend fun upsert(bot: BotProfileEntity)

    @Query("DELETE FROM bot_profile WHERE id = :id")
    suspend fun delete(id: String)
}
