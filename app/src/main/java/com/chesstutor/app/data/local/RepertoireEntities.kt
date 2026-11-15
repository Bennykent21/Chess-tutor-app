package com.chesstutor.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One opening trainer card: a position in one opening (Phase 10, D71, database version 15). */
@Entity(tableName = "opening_card", indices = [Index("bookId")])
data class OpeningCardEntity(
    @PrimaryKey val cardId: String,
    val bookId: String,
    val positionKey: String,
    /** RepertoireLevel ordinal. */
    val level: Int,
    val stage: Int,
    val easeFactor: Float,
    val intervalDays: Int,
    val dueAt: Long,
    val attempts: Int,
    val introducedAt: Long,
    val lastReviewedAt: Long?,
    /** Misses after the first recall, and clean first tries in a row: tricky lines (D76). */
    val lapses: Int,
    val streak: Int,
)

/** Every scheduled recall, for the mastery numbers (D72). Append-only. */
@Entity(tableName = "opening_review", indices = [Index("bookId")])
data class OpeningReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: String,
    val bookId: String,
    val level: Int,
    val at: Long,
    val firstTryRight: Boolean,
    val hinted: Boolean,
)

/** The open level of each opening and when each level was mastered, so unlocks stick (D72). */
@Entity(tableName = "opening_level")
data class OpeningLevelEntity(
    @PrimaryKey val bookId: String,
    val unlocked: Int,
    val coreMasteredAt: Long?,
    val standardMasteredAt: Long?,
    val deepMasteredAt: Long?,
)

@Dao
interface RepertoireDao {
    @Query("SELECT * FROM opening_card")
    fun observeCards(): Flow<List<OpeningCardEntity>>

    @Query("SELECT * FROM opening_level")
    fun observeLevels(): Flow<List<OpeningLevelEntity>>

    @Query("SELECT * FROM opening_review WHERE bookId = :bookId ORDER BY at")
    suspend fun reviews(bookId: String): List<OpeningReviewEntity>

    @Upsert
    suspend fun upsertCards(rows: List<OpeningCardEntity>)

    @Insert
    suspend fun insertReviews(rows: List<OpeningReviewEntity>)

    @Transaction
    suspend fun save(cards: List<OpeningCardEntity>, reviews: List<OpeningReviewEntity>) {
        upsertCards(cards)
        insertReviews(reviews)
    }

    @Upsert
    suspend fun upsertLevel(row: OpeningLevelEntity)
}
