package com.chesstutor.app.feature.home

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.domain.account.AnalysisSummary
import com.chesstutor.app.domain.account.GameLibrary
import com.chesstutor.app.domain.account.GameOutcome
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The player's newest game, for Home (D92): who against, how it went, and the accuracy once reviewed. */
data class LastGame(val id: String, val opponent: String, val outcome: GameOutcome, val moves: Int, val accuracy: Double?) {
    /** "Won against Marlo", "Lost to Marlo", "Drew with Marlo". */
    val title: String
        get() = when (outcome) {
            GameOutcome.WIN -> "Won against $opponent"
            GameOutcome.LOSS -> "Lost to $opponent"
            GameOutcome.DRAW -> "Drew with $opponent"
            GameOutcome.UNKNOWN -> "vs $opponent"
        }
}

/** Home's look back: the last game and which days of this week had a game or a solved puzzle. */
object HomeRecap {

    fun lastGame(games: List<GameRecord>, summaries: Map<String, AnalysisSummary>): LastGame? {
        val game = games.maxByOrNull { it.dateMillis } ?: return null
        val color = if (game.userColor.equals("black", ignoreCase = true)) PieceColor.BLACK else PieceColor.WHITE
        return LastGame(
            id = game.id,
            opponent = game.botName,
            outcome = GameLibrary.outcomeOf(game),
            moves = (game.moveCount + 1) / 2,
            accuracy = summaries[game.id]?.accuracyFor(color),
        )
    }

    /** Monday to Sunday of [today]'s week: true where something was played that day. */
    fun week(today: LocalDate, activeAt: List<Long>, zone: ZoneId = ZoneId.systemDefault()): List<Boolean> {
        val monday = today.with(DayOfWeek.MONDAY)
        val days = activeAt.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.toSet()
        return (0L until 7L).map { monday.plusDays(it) in days }
    }
}
