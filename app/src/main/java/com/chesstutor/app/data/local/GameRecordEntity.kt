package com.chesstutor.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.chesstutor.app.data.model.GameRecord

@Entity(tableName = "game_records")
data class GameRecordEntity(
    @PrimaryKey val id: String,
    val dateMillis: Long,
    val botName: String,
    val botRating: Int,
    val result: String,
    val pgn: String,
    val moveCount: Int,
    val userColor: String,
    val finalFen: String,
    val uciMoves: String
) {
    fun toDomain(): GameRecord = GameRecord(
        id = id,
        dateMillis = dateMillis,
        botName = botName,
        botRating = botRating,
        result = result,
        pgn = pgn,
        moveCount = moveCount,
        userColor = userColor,
        finalFen = finalFen,
        uciMoves = uciMoves
    )

    companion object {
        fun fromDomain(domain: GameRecord): GameRecordEntity = GameRecordEntity(
            id = domain.id,
            dateMillis = domain.dateMillis,
            botName = domain.botName,
            botRating = domain.botRating,
            result = domain.result,
            pgn = domain.pgn,
            moveCount = domain.moveCount,
            userColor = domain.userColor,
            finalFen = domain.finalFen,
            uciMoves = domain.uciMoves
        )
    }
}
