package com.chesstutor.app.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PgnFormatter {
    fun formatPgn(
        moves: List<String>,
        whitePlayer: String = "Player",
        blackPlayer: String = "Bot",
        whiteElo: Int? = null,
        blackElo: Int? = null,
        result: String = "*",
        date: Date = Date(),
        event: String = "Chess Tutor Arena Match"
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
        headerBuilder.append("[Result \"$result\"]\n\n")

        val moveBuilder = StringBuilder()
        var moveNum = 1
        for (i in moves.indices step 2) {
            val whiteMove = moves[i]
            val blackMove = moves.getOrNull(i + 1)
            moveBuilder.append("$moveNum. $whiteMove")
            if (blackMove != null) {
                moveBuilder.append(" $blackMove ")
            } else {
                moveBuilder.append(" ")
            }
            moveNum++
        }
        moveBuilder.append(result)

        return headerBuilder.toString() + moveBuilder.toString().trim()
    }
}
