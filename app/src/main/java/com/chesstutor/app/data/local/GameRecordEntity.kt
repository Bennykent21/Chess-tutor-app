package com.chesstutor.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.GameSource

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
    val uciMoves: String,
    val source: String = "ARENA",
    val termination: String = "",
    val timeClass: String = "rapid",
    val incompleteParse: Boolean = false
) {
    fun toDomain(): GameRecord {
        val parsedSource = runCatching { GameSource.valueOf(source) }.getOrElse {
            when {
                id.startsWith("chesscom_") -> GameSource.CHESS_COM
                id.startsWith("lichess_") -> GameSource.LICHESS
                else -> GameSource.ARENA
            }
        }
        val effectiveSource = when {
            id.startsWith("chesscom_") -> GameSource.CHESS_COM
            id.startsWith("lichess_") -> GameSource.LICHESS
            else -> parsedSource
        }
        return GameRecord(
            id = id,
            dateMillis = dateMillis,
            botName = botName,
            botRating = botRating,
            result = result,
            pgn = pgn,
            moveCount = moveCount,
            userColor = userColor,
            finalFen = finalFen,
            uciMoves = uciMoves,
            source = effectiveSource,
            termination = termination,
            timeClass = timeClass,
            incompleteParse = incompleteParse
        )
    }

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
            uciMoves = domain.uciMoves,
            source = domain.source.name,
            termination = domain.termination,
            timeClass = domain.timeClass,
            incompleteParse = domain.incompleteParse
        )
    }
}
