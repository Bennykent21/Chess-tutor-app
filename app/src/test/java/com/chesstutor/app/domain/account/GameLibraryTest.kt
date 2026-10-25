package com.chesstutor.app.domain.account

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.GameSource
import com.chesstutor.app.domain.opening.OpeningTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GameLibraryTest {

    private val tree by lazy { File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream) }

    private fun record(
        id: String,
        moves: String = "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5",
        result: String = "1-0",
        color: String = "white",
        opponent: String = "Rival",
        timeClass: String = "rapid",
        date: Long = 1_000L,
        pgn: String = "",
    ) = GameRecord(
        id = id, dateMillis = date, botName = opponent, botRating = 1500, result = result, pgn = pgn,
        moveCount = moves.split(' ').count { it.isNotBlank() }, userColor = color, finalFen = "", uciMoves = moves, timeClass = timeClass,
    )

    @Test
    fun resultsAreSeenFromThePlayersSide() {
        assertEquals(GameOutcome.WIN, GameLibrary.outcomeOf(record("a", result = "1-0", color = "white")))
        assertEquals(GameOutcome.LOSS, GameLibrary.outcomeOf(record("a", result = "1-0", color = "black")))
        assertEquals(GameOutcome.WIN, GameLibrary.outcomeOf(record("a", result = "0-1", color = "Black")))
        assertEquals(GameOutcome.DRAW, GameLibrary.outcomeOf(record("a", result = "1/2-1/2")))
        assertEquals(GameOutcome.UNKNOWN, GameLibrary.outcomeOf(record("a", result = "*")))
    }

    @Test
    fun timeClassesFromEverySourceFoldIntoOneList() {
        assertEquals(TimeClass.BULLET, TimeClass.of("ultraBullet"))
        assertEquals(TimeClass.DAILY, TimeClass.of("correspondence"))
        assertEquals(TimeClass.DAILY, TimeClass.of("daily"))
        assertEquals(TimeClass.CLASSICAL, TimeClass.of("classical"))
        assertEquals(TimeClass.UNTIMED, TimeClass.of("untimed"))
        assertEquals("unknown names count as rapid", TimeClass.RAPID, TimeClass.of("weird"))
    }

    @Test
    fun namesTheOpeningAndWhereTheBookEnded() {
        val giuoco = GameLibrary.summarise(record("a"), tree)
        assertTrue(giuoco.openingName!!, giuoco.openingName!!.startsWith("Italian Game"))
        assertEquals("Italian Game", giuoco.openingFamily)
        assertNull("still in the book after six moves", giuoco.leftBookAt)

        // 4.Kf1 is no book move: White left the book on move 4 (ply 6).
        val strange = GameLibrary.summarise(record("b", moves = "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 e1f1 g8f6"), tree)
        assertEquals(6, strange.leftBookAt)
        assertEquals(4, strange.leftBookAtMove)
        assertTrue(strange.openingName!!.startsWith("Italian Game"))
        assertEquals(4, strange.moveNumber)
    }

    @Test
    fun setUpPositionsHaveNoOpeningAndSitesNamesAreTheFallback() {
        val fen = "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1"
        val setUp = GameLibrary.summarise(record("a", moves = "e2e4", pgn = "[SetUp \"1\"]\n[FEN \"$fen\"]\n\n1. e4 *"), tree)
        assertEquals(fen, setUp.startFen)
        assertNull(setUp.opening)
        assertNull(setUp.openingName)

        val normalStartTag = "[FEN \"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1\"]"
        assertNull("a FEN tag with the normal start is the normal start", GameLibrary.startFenOf(normalStartTag))

        val noMoves = GameLibrary.summarise(record("c", moves = "", pgn = "[Opening \"Bongcloud Attack\"]"), tree)
        assertEquals("Bongcloud Attack", noMoves.openingName)
        assertEquals("Bongcloud Attack", noMoves.openingFamily)
    }

    @Test
    fun filtersCombineAndSearchMatchesEveryWord() {
        val games = GameLibrary.summarise(
            listOf(
                record("app", opponent = "Pip Bot", result = "1-0", date = 3),
                record("cc", opponent = "magnus", result = "0-1", color = "black", timeClass = "blitz", date = 2, moves = "d2d4 d7d5 c2c4").copy(source = GameSource.CHESS_COM),
                record("li", opponent = "hikaru", result = "1/2-1/2", timeClass = "bullet", date = 1, moves = "d2d4 g8f6").copy(source = GameSource.LICHESS),
            ),
            tree,
        )
        assertEquals("newest first", listOf("app", "cc", "li"), games.map { it.id })

        fun ids(filter: GameFilter) = GameLibrary.filter(games, filter).map { it.id }
        assertEquals(listOf("app", "cc", "li"), ids(GameFilter()))
        assertEquals(listOf("cc"), ids(GameFilter(source = GameSource.CHESS_COM)))
        assertEquals(listOf("app", "cc"), ids(GameFilter(outcome = GameOutcome.WIN)))
        assertEquals(listOf("cc"), ids(GameFilter(outcome = GameOutcome.WIN, color = PieceColor.BLACK)))
        assertEquals(listOf("li"), ids(GameFilter(timeClass = TimeClass.BULLET)))
        assertEquals(listOf("app"), ids(GameFilter(openingFamily = "Italian Game")))
        assertEquals(listOf("cc"), ids(GameFilter(query = "  MAGNUS gambit ")))
        assertEquals(listOf("app"), ids(GameFilter(query = "pip italian")))
        assertEquals(emptyList<String>(), ids(GameFilter(query = "pip", source = GameSource.LICHESS)))

        assertEquals(listOf(GameSource.ARENA, GameSource.CHESS_COM, GameSource.LICHESS), GameLibrary.sources(games))
        assertEquals(listOf(TimeClass.BULLET, TimeClass.BLITZ, TimeClass.RAPID), GameLibrary.timeClasses(games))
        assertEquals(3, GameLibrary.topOpenings(games).size)

        val totals = GameLibrary.totals(games)
        assertEquals(GameTotals(games = 3, wins = 2, draws = 1, losses = 0), totals)
        assertEquals(2.5f / 3, totals.score, 0.0001f)
    }

    @Test
    fun topOpeningsAreTheMostPlayed() {
        val games = GameLibrary.summarise(
            listOf(record("a"), record("b"), record("c", moves = "d2d4 d7d5 c2c4")),
            tree,
        )
        assertEquals("Italian Game", GameLibrary.topOpenings(games).first())
        assertEquals(1, GameLibrary.topOpenings(games, limit = 1).size)
    }
}
