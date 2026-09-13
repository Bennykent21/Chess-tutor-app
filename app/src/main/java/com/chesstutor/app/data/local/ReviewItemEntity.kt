package com.chesstutor.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.chesstutor.app.domain.ReviewItem
import java.time.Instant

@Entity(tableName = "review_items")
data class ReviewItemEntity(
    @PrimaryKey val id: String,
    val fen: String,
    val dueAt: Long,     // epoch millis
    val stage: Int = -1,
    val attempts: Int = 0,
    val mistakeUci: String = "",
    val bestMoveUci: String = "",
    val explanation: String = ""
) {
    fun toDomain(): ReviewItem = ReviewItem(
        id = id,
        fen = fen,
        dueAt = Instant.ofEpochMilli(dueAt),
        stage = stage,
        attempts = attempts,
        mistakeUci = mistakeUci,
        bestMoveUci = bestMoveUci,
        explanation = explanation
    )
}

fun ReviewItem.toEntity(): ReviewItemEntity = ReviewItemEntity(
    id = id,
    fen = fen,
    dueAt = dueAt.toEpochMilli(),
    stage = stage,
    attempts = attempts,
    mistakeUci = mistakeUci,
    bestMoveUci = bestMoveUci,
    explanation = explanation
)
