package com.chesstutor.app.domain.account

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.GameSource
import com.chesstutor.app.domain.opening.OpeningTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PerformanceTest {

    private val tree by lazy { File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream) }

    private fun game(
        id: String,
        result: String,
        color: String = "white",
        moves: String = "e2e4 e7e5 g1f3 b8c6 f1c4",
        timeClass: String = "rapid",
        date: Long = 1,
        rating: Int? = null,
        source: GameSource = GameSource.CHESS_COM,
    ) = GameLibrary.summarise(
        GameRecord(
            id = id, dateMillis = date, botName = "Opp", botRating = 1500, result = result,
            pgn = rating?.let { "[${if (color == "white") "WhiteElo" else "BlackElo"} \"$it\"]" }.orEmpty(),
            moveCount = 5, userColor = color, finalFen = "", uciMoves = moves, source = source, timeClass = timeClass,
        ),
        tree,
    )

    @Test
    fun resultsAreSplitByColourTimeAndOpening() {
        val games = listOf(
            game("a", "1-0"),
            game("b", "0-1"),
            game("c", "0-1", color = "black", moves = "d2d4 d7d5 c2c4", timeClass = "blitz"),
            game("d", "1/2-1/2", color = "black", moves = "d2d4 d7d5 c2c4", timeClass = "blitz"),
            game("e", "*"),
        )
        val report = PerformanceCalculator.report(games, emptyMap())
        assertEquals(Wdl(wins = 2, draws = 1, losses = 1), report.overall)
        assertEquals(2.5f / 4, report.overall.score, 0.0001f)

        assertEquals(listOf("As White", "As Black"), report.byColor.map { it.label })
        assertEquals(Wdl(wins = 1, losses = 1), report.byColor[0].wdl)
        assertEquals(PieceColor.BLACK, report.byColor[1].filter.color)

        assertEquals(listOf("Blitz", "Rapid"), report.byTimeClass.map { it.label })
        assertEquals(TimeClass.BLITZ, report.byTimeClass[0].filter.timeClass)

        assertEquals(listOf("Italian Game", "Queen's Gambit"), report.byOpening.map { it.label }.sorted())
        assertEquals("Italian Game", report.byOpening.first { it.wdl.games == 2 }.filter.openingFamily)
        assertEquals(0, report.analysedGames)
        assertTrue(report.mistakes.isEmpty())
    }

    @Test
    fun rowsKeepThePagesSourceFilter() {
        val base = GameFilter(source = GameSource.LICHESS)
        val report = PerformanceCalculator.report(listOf(game("a", "1-0", source = GameSource.LICHESS)), emptyMap(), base)
        assertEquals(GameSource.LICHESS, report.byColor.single().filter.source)
        assertEquals(PieceColor.WHITE, report.byColor.single().filter.color)
    }

    @Test
    fun ratingTrendFollowsTheMostPlayedRatedTimeClass() {
        val games = listOf(
            game("a", "1-0", rating = 1500, date = 3, timeClass = "blitz"),
            game("b", "1-0", rating = 1480, date = 1, timeClass = "blitz"),
            game("c", "1-0", rating = 1700, date = 2, timeClass = "rapid"),
            game("d", "1-0", date = 4, timeClass = "blitz"),
        )
        assertEquals(1500, games[0].playerRating)
        assertNull(games[3].playerRating)
        val report = PerformanceCalculator.report(games, emptyMap())
        assertEquals(TimeClass.BLITZ, report.ratingTimeClass)
        assertEquals(listOf(1480, 1500), report.ratingTrend.map { it.rating })
    }

    @Test
    fun analysedGamesGiveAccuracyByPhaseAndMistakeKinds() {
        // Scholar's mate with the player as Black: 3...Nf6 walks into mate.
        val moves = listOf("e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6", "h5f7")
        val evals = listOf(
            PlyEval(0, 20, null, "e2e4"), PlyEval(1, 20, null, "e7e5"), PlyEval(2, 20, null, "g1f3"),
            PlyEval(3, 20, null, "g8f6"), PlyEval(4, 30, null, "d1h5"), PlyEval(5, 50, null, "g7g6"),
            PlyEval(6, null, 1, "h5f7"), GameReviewer.terminalEval(7, GameReviewer.positions(Position.STARTING_FEN, moves).last())!!,
        )
        val g = game("s", "1-0", color = "black", moves = moves.joinToString(" "))
        val review = GameReviewer.review(Position.STARTING_FEN, moves, evals)
        assertTrue(review.moves.all { it.phase == GamePhase.OPENING })

        val report = PerformanceCalculator.report(listOf(g), mapOf("s" to review))
        assertEquals(1, report.analysedGames)
        val opening = report.phases.first { it.phase == GamePhase.OPENING }
        assertEquals(3, opening.moves)
        assertEquals(1, opening.errors)
        assertEquals(0, report.phases.first { it.phase == GamePhase.ENDGAME }.moves)
        assertEquals(mapOf(MistakeKind.ALLOWED_MATE to 1), report.mistakes)
    }

    @Test
    fun mistakeKindsAndPhases() {
        fun move(before: Double, after: Double, quality: MoveQuality) = ReviewedMove(
            ply = 30, uci = "a2a3", san = "a3", mover = PieceColor.WHITE, quality = quality, winBefore = before, winAfter = after,
            bestMove = "h2h3", bestSan = "h3", accuracy = 0.0, fenBefore = Position.STARTING_FEN,
        )
        assertEquals(MistakeKind.MISSED_MATE, PerformanceCalculator.kindOf(move(100.0, 70.0, MoveQuality.BLUNDER)))
        assertEquals(MistakeKind.THREW_WIN, PerformanceCalculator.kindOf(move(85.0, 50.0, MoveQuality.BLUNDER)))
        assertEquals(MistakeKind.OTHER_BLUNDER, PerformanceCalculator.kindOf(move(50.0, 20.0, MoveQuality.BLUNDER)))
        assertEquals(MistakeKind.MISTAKE, PerformanceCalculator.kindOf(move(50.0, 35.0, MoveQuality.MISTAKE)))
        assertNull(PerformanceCalculator.kindOf(move(50.0, 42.0, MoveQuality.INACCURACY)))

        assertEquals(62, GamePhase.materialOf(Position.initial()))
        assertEquals(GamePhase.OPENING, GamePhase.of(10, Position.initial()))
        assertEquals(GamePhase.MIDDLEGAME, GamePhase.of(30, Position.initial()))
        assertEquals(GamePhase.ENDGAME, GamePhase.of(5, Position.fromFen("4k3/8/8/8/8/8/4P3/R3K3 w - - 0 1")))
    }
}
