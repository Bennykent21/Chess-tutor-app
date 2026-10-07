package com.chesstutor.app.domain

import java.time.Instant

data class ReviewItem(
    val id: String,
    val fen: String,
    val dueAt: Instant,
    val stage: Int = -1,
    val attempts: Int = 0,
    val mistakeUci: String = "",
    val bestMoveUci: String = "",
    val explanation: String = "",
    val easeFactor: Float = 2.5f,
    val intervalDays: Int = 0,
    val sourceDescription: String = "",
    val acceptedMovesUci: List<String> = emptyList()
)
