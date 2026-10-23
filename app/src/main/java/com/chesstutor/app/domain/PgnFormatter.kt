package com.chesstutor.app.domain

import com.chesstutor.app.data.model.GameRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PgnFormatter {
    fun reconstructSanMovesFromUci(
        uciMoves: String,
        startFen: String = ChessPosition.STARTING_FEN
    ): List<String> = ChessPosition.replayUciToSan(uciMoves, startFen)

    fun formatMoveTextFromUci(
        uciMoves: String,
        startFen: String = ChessPosition.STARTING_FEN
    ): String {
        val sanMoves = reconstructSanMovesFromUci(uciMoves, startFen)
        if (sanMoves.isEmpty()) return ""
        val builder = StringBuilder()
        var moveNum = 1
        for (i in sanMoves.indices step 2) {
            val whiteMove = sanMoves[i]
            val blackMove = sanMoves.getOrNull(i + 1)
            builder.append("$moveNum. $whiteMove")
            if (blackMove != null) {
                builder.append(" $blackMove ")
            } else {
                builder.append(" ")
            }
            moveNum++
        }
        return builder.toString().trim()
    }

    fun reconstructPgnFromGameRecord(game: GameRecord): String {
        val sanMoves = reconstructSanMovesFromUci(game.uciMoves)
        if (sanMoves.isEmpty()) {
            return game.pgn
        }
        val userIsWhite = game.userColor.equals("white", ignoreCase = true)
        return formatPgn(
            moves = sanMoves,
            whitePlayer = if (userIsWhite) "You" else game.botName,
            blackPlayer = if (userIsWhite) game.botName else "You",
            whiteElo = if (userIsWhite) null else game.botRating,
            blackElo = if (userIsWhite) game.botRating else null,
            result = game.result,
            date = Date(game.dateMillis)
        )
    }

    fun formatPgn(
        moves: List<String>,
        whitePlayer: String = "Player",
        blackPlayer: String = "Bot",
        whiteElo: Int? = null,
        blackElo: Int? = null,
        result: String = "*",
        date: Date = Date(),
        event: String = "Chess Tutor Arena Match",
        /** A game from a set-up position records it (PGN SetUp and FEN tags) and numbers from there. */
        startFen: String? = null
    ): String {
        val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.US)
        val dateStr = dateFormat.format(date)

        val headerBuilder = StringBuilder()
        headerBuilder.append("[Event \"$event\"]\n")
        headerBuilder.append("[Site \"Chess Tutor App\"]\n")
        headerBuilder.append("[Date \"$dateStr\"]\n")
        headerBuilder.append("[Round \"1\"]\n")
        headerBuilder.append("[White \"$whitePlayer\"]\n")
        headerBuilder.append("[Black \"$blackPlayer\"]\n")
        if (whiteElo != null) headerBuilder.append("[WhiteElo \"$whiteElo\"]\n")
        if (blackElo != null) headerBuilder.append("[BlackElo \"$blackElo\"]\n")
        val customFen = startFen?.trim()?.takeIf { it != ChessPosition.STARTING_FEN }
        if (customFen != null) {
            headerBuilder.append("[SetUp \"1\"]\n")
            headerBuilder.append("[FEN \"$customFen\"]\n")
        }
        headerBuilder.append("[Result \"$result\"]\n\n")

        val fenParts = customFen?.split(Regex("\\s+")).orEmpty()
        val blackFirst = fenParts.getOrNull(1) == "b"
        var moveNum = fenParts.getOrNull(5)?.toIntOrNull() ?: 1
        val moveBuilder = StringBuilder()
        moves.forEachIndexed { index, san ->
            val whiteToMove = (index % 2 == 0) != blackFirst
            when {
                whiteToMove -> moveBuilder.append("$moveNum. $san ")
                index == 0 -> moveBuilder.append("$moveNum... $san ")
                else -> moveBuilder.append("$san ")
            }
            if (!whiteToMove) moveNum++
        }
        moveBuilder.append(result)

        return headerBuilder.toString() + moveBuilder.toString().trim()
    }
}
