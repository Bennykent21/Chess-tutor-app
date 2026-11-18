package com.chesstutor.app.domain.account

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.domain.opening.OpeningTree
import org.junit.Assert.assertEquals
import org.junit.Test

/** Insights from reviewed games (D104) and the games filter's ending. */
class GameInsightsTest {

    private var n = 0

    private fun game(result: String, color: String = "white", termination: String = ""): GameSummary {
        val record = GameRecord(
            id = "g${n++}", dateMillis = n.toLong(), botName = "Bot", botRating = 1200, result = result, pgn = "*",
            moveCount = 2, userColor = color, finalFen = "", uciMoves = "e2e4 e7e5", termination = termination,
        )
        return GameLibrary.summarise(record, OpeningTree.EMPTY)
    }

    private fun move(ply: Int, mover: PieceColor, quality: MoveQuality, before: Double, after: Double, phase: GamePhase) =
        ReviewedMove(ply, "e2e4", "e4", mover, quality, before, after, "e2e4", "e4", accuracy = 80.0, fenBefore = "", phase = phase)

    private fun review(vararg moves: ReviewedMove) = GameReview(moves.toList(), graph = emptyList(), totalPlies = moves.size, accuracies = mapOf(PieceColor.WHITE to 70.0))

    @Test
    fun lossesTurnWhereThePlayersCostliestMoveWasAndCountWhy() {
        val lostToBlunder = game("0-1")
        val slipped = game("0-1")
        val won = game("1-0")
        val reviews = mapOf(
            lostToBlunder.id to review(
                move(1, PieceColor.WHITE, MoveQuality.INACCURACY, 50.0, 45.0, GamePhase.OPENING),
                move(3, PieceColor.WHITE, MoveQuality.BLUNDER, 45.0, 5.0, GamePhase.MIDDLEGAME),
            ),
            slipped.id to review(
                move(41, PieceColor.WHITE, MoveQuality.MISTAKE, 90.0, 40.0, GamePhase.ENDGAME),
            ),
            won.id to review(move(1, PieceColor.WHITE, MoveQuality.BEST, 50.0, 52.0, GamePhase.OPENING)),
        )
        val insight = GameInsights.clock(listOf(lostToBlunder, slipped, won), reviews)
        assertEquals(3, insight.reviewed)
        assertEquals(2, insight.losses)
        assertEquals(mapOf(GamePhase.MIDDLEGAME to 1, GamePhase.ENDGAME to 1), insight.turned)
        assertEquals(1, insight.blunders)
        assertEquals("the endgame loss was winning once", 1, insight.slipped)
        assertEquals(listOf(70.0, 70.0, 70.0), insight.accuracy)
    }

    @Test
    fun theGamesFilterKeepsTheEndingAndReadsOldFilters() {
        val filter = GameFilter(outcome = GameOutcome.LOSS, ending = GameEnding.TIME)
        assertEquals(filter, GameFilter.decode(filter.encode()))
        assertEquals(GameFilter(outcome = GameOutcome.LOSS), GameFilter.decode("|LOSS||||"))
        val games = listOf(game("0-1", termination = "timeout"), game("0-1", termination = "checkmate"))
        assertEquals(1, GameLibrary.filter(games, filter).size)
    }

    @Test
    fun theWinStreakOpensExactlyItsGames() {
        assertEquals(4..6, ClockStats.longestRunRange(listOf(false, true, true, false, true, true, true)))
        assertEquals(null, ClockStats.longestRunRange(listOf(false, false)))
        val games = listOf(game("1-0"), game("1-0"), game("0-1"), game("1-0"), game("1-0"), game("1-0"))
        val records = ClockStats.report(games, TimeClass.RAPID).records
        assertEquals(3, records.longestWinStreak)
        val filter = GameFilter(timeClass = TimeClass.RAPID, fromMillis = records.winStreakFrom, toMillis = records.winStreakTo)
        assertEquals(filter, GameFilter.decode(filter.encode()))
        assertEquals(3, GameLibrary.filter(games, filter).size)
    }
}
