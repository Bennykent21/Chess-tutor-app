package com.chesstutor.app.domain

import java.time.Instant

data class ReviewItem(
    val id: String,
    val fen: String,
    var dueAt: Instant,
    var stage: Int = -1,
    var attempts: Int = 0,
    val mistakeUci: String = "",
    val bestMoveUci: String = "",
    val explanation: String = ""
)
