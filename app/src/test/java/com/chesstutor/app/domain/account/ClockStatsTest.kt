package com.chesstutor.app.domain.account

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.GameSource
import com.chesstutor.app.domain.opening.OpeningTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ClockStatsTest {

    private val tree by lazy { File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream) }

    private fun game(id: String, result: String, clock: String, date: Long, rating: Int?, opponent: Int = 1500, ending: String = "resigned", color: String = "white", moves: String = "e2e4 e7e5 g1f3 b8c6") =
        GameLibrary.summarise(
            GameRecord(
                id = id, dateMillis = date, botName = "opp$id", botRating = opponent, result = result,
                pgn = rating?.let { "[${if (color == "white") "WhiteElo" else "BlackElo"} \"$it\"]" }.orEmpty(),
                moveCount = moves.split(' ').size, userColor = color, finalFen = "", uciMoves = moves,
                source = GameSource.CHESS_COM, termination = ending, timeClass = clock,
            ),
            tree,
        )

    private val games by lazy {
        listOf(
            game("1", "1-0", "blitz", 1, 1400, opponent = 1450, ending = "checkmate"),
            game("2", "1-0", "blitz", 2, 1420, opponent = 1600, ending = "resigned"),
            game("3", "0-1", "blitz", 3, 1410, ending = "timeout"),
            game("4", "1-0", "blitz", 4, 1430, opponent = 1500, ending = "timeout"),
            game("5", "1-0", "blitz", 5, 1450, opponent = 1350, ending = "checkmate"),
            game("6", "0-1", "blitz", 6, 1470, opponent = 1380, ending = "resigned", color = "black"),
            game("7", "1/2-1/2", "blitz", 7, 1465, ending = "agreed"),
            game("r", "1-0", "rapid", 8, 1700),
        )
    }

    @Test
    fun aClockReportCountsOnlyThatClock() {
        val report = ClockStats.report(games, TimeClass.BLITZ)
        assertEquals(7, report.games)
        assertEquals(Wdl(wins = 5, draws = 1, losses = 1), report.results)
    }

    @Test
    fun recordsFollowTheRatingTheBestWinAndTheBestRun() {
        val records = ClockStats.report(games, TimeClass.BLITZ).records
        assertEquals(1465, records.current)
        assertEquals(1470, records.peak)
        assertEquals("the strongest player beaten", "opp2", records.bestWin?.opponent)
        assertEquals("wins 4, 5 and 6 in a row", 3, records.longestWinStreak)
    }

    @Test
    fun theLinkedSitesRatingIsTheCurrentOneAndCanBeThePeak() {
        val records = ClockStats.report(games, TimeClass.BLITZ, current = 1500).records
        assertEquals(1500, records.current)
        assertEquals(1500, records.peak)
    }

    @Test
    fun winsAndLossesAreCountedByHowTheyEnded() {
        val report = ClockStats.report(games, TimeClass.BLITZ)
        assertEquals(mapOf(GameEnding.CHECKMATE to 2, GameEnding.RESIGNATION to 2, GameEnding.TIME to 1), report.wonBy)
        assertEquals(mapOf(GameEnding.TIME to 1), report.lostBy)
        assertEquals(2, report.averageMoves)
    }

    @Test
    fun openingsAreSplitByColour() {
        val report = ClockStats.report(games, TimeClass.BLITZ)
        assertEquals(6, report.asWhite.sumOf { it.wdl.games })
        assertEquals(1, report.asBlack.sumOf { it.wdl.games })
        assertEquals("a tap shows that colour's games in that clock", GameFilter(timeClass = TimeClass.BLITZ, color = PieceColor.BLACK, openingFamily = report.asBlack.first().label), report.asBlack.first().filter)
    }

    @Test
    fun aClockWithNoGamesHasEmptyRecords() {
        val records = ClockStats.report(games, TimeClass.BULLET).records
        assertNull(records.current)
        assertNull(records.bestWin)
        assertEquals(0, records.longestWinStreak)
    }

    @Test
    fun clocksPlayedPutTheMainOnesFirst() {
        assertEquals(listOf(TimeClass.RAPID, TimeClass.BLITZ), ClockStats.clocksPlayed(games))
    }

    @Test
    fun endingsAreReadFromTheTermination() {
        assertEquals(GameEnding.CHECKMATE, GameEnding.of("checkmate"))
        assertEquals(GameEnding.RESIGNATION, GameEnding.of("Resigned"))
        assertEquals(GameEnding.TIME, GameEnding.of("timeout"))
        assertEquals(GameEnding.OTHER, GameEnding.of("stalemate"))
    }

    @Test
    fun aFilterSurvivesTheTripToTheGamesPage() {
        val filter = GameFilter(timeClass = TimeClass.BULLET, color = PieceColor.WHITE, outcome = GameOutcome.WIN, openingFamily = "Italian Game", query = "a|b")
        assertEquals(filter, GameFilter.decode(filter.encode()))
        assertEquals(GameFilter(), GameFilter.decode(""))
    }
}
