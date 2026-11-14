package com.chesstutor.app.domain.train

import com.chesstutor.app.core.Position
import com.chesstutor.app.core.repetitionKey
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.domain.account.GameLibrary
import com.chesstutor.app.domain.account.GameReviewer
import com.chesstutor.app.domain.account.GameSummary
import com.chesstutor.app.domain.account.PlyEval
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.domain.repertoire.TestRepertoire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameFindingsTest {

    private var n = 0

    private fun game(moves: String, color: String, result: String = "*", fen: String? = null): GameSummary {
        val pgn = fen?.let { "[SetUp \"1\"]\n[FEN \"$it\"]\n\n*" } ?: "*"
        val record = GameRecord(
            id = "g${n++}", dateMillis = n.toLong(), botName = "Bot", botRating = 1200, result = result, pgn = pgn,
            moveCount = moves.split(' ').size, userColor = color, finalFen = "", uciMoves = moves,
        )
        return GameLibrary.summarise(record, OpeningTree.EMPTY)
    }

    @Test
    fun leavingYourLineTheSameWayIsCountedWithTheMoveThatKeepsIt() {
        val book = TestRepertoire.caroKann()
        val games = List(3) { game("e2e4 c7c6 d2d4 d7d5 e4e5 e7e6 g1f3", "black") } +
            game("e2e4 c7c6 d2d4 d7d5 e4e5 c8f5 g1f3", "black") +
            game("e2e4 e7e5 g1f3", "black") +
            game("e2e4 c7c6 d2d4 d7d5 e4e5 e7e6", "white")
        val slip = GameFindings.lineSlips(games, listOf(book)).single()
        assertEquals(3, slip.misses)
        assertEquals("a game with 1...e5 is another opening, not a slip", 4, slip.reached)
        assertEquals("3.e5", slip.after)
        assertEquals("3...e6", slip.played)
        assertEquals("3...Bf5", slip.right)
        assertEquals(TestRepertoire.key("e2e4 c7c6 d2d4 d7d5 e4e5"), Position.fromFen(slip.fen).repetitionKey())
        assertEquals("e2e4 c7c6 d2d4 d7d5 e4e5".split(' '), slip.path)
    }

    @Test
    fun aSlipOnceIsNotAFinding() {
        val games = listOf(game("e2e4 c7c6 d2d4 d7d5 e4e5 e7e6", "black"), game("e2e4 c7c6 d2d4 d7d5 e4e5 c8f5", "black"))
        assertTrue(GameFindings.lineSlips(games, listOf(TestRepertoire.caroKann())).isEmpty())
    }

    @Test
    fun rookEndingsReachedAPawnUpAreCountedWithHowManyWereWon() {
        val upAPawn = "r5k1/5ppp/8/8/P7/8/5PPP/R5K1 w - - 0 30"
        val games = listOf(
            game("g1f1 g8f8", "white", "1-0", fen = upAPawn),
            game("g1f1 g8f8", "white", "1/2-1/2", fen = upAPawn),
            game("g1f1 g8f8", "white", "1/2-1/2", fen = upAPawn),
            game("g1f1 g8f8", "black", "1/2-1/2", fen = upAPawn),
        )
        val rook = GameFindings.endings(games).single()
        assertEquals(EndingKind.ROOK, rook.kind)
        assertEquals("the game where the learner was the pawn down does not count", 3, rook.reached)
        assertEquals(1, rook.won)
        assertNull("fewer than three games is not enough", GameFindings.endings(games.take(2)).firstOrNull())
    }

    @Test
    fun theSameMistakeInReviewedGamesIsCounted() {
        val moves = "e2e4 e7e5 g1f3 f7f6"
        val games = listOf(game(moves, "black"), game(moves, "black"), game("e2e4 e7e5 g1f3 b8c6", "black"))
        val evals = listOf(
            PlyEval(0, 30, null, "e2e4"), PlyEval(1, 30, null, "e7e5"), PlyEval(2, 30, null, "g1f3"),
            PlyEval(3, 30, null, "b8c6"), PlyEval(4, 400, null, "f3e5"),
        )
        val good = evals.dropLast(1) + PlyEval(4, 30, null, "f1b5")
        val reviews = games.associate { g ->
            g.id to GameReviewer.review(Position.STARTING_FEN, g.moves, if (g.moves.last() == "f7f6") evals else good)
        }
        val mistake = GameFindings.mistakes(games, reviews).single()
        assertEquals(2, mistake.times)
        assertEquals(3, mistake.reached)
        assertEquals("2.Nf3", mistake.after)
        assertEquals("2...f6", mistake.played)
        assertEquals("2...Nc6", mistake.better)
    }

    @Test
    fun endingsAreRecognisedByTheirPieces() {
        assertEquals(EndingKind.PAWN, GameFindings.kindOf(Position.fromFen("8/5k2/8/5P2/5K2/8/8/8 w - - 0 1")))
        assertEquals(EndingKind.ROOK, GameFindings.kindOf(Position.fromFen("8/5k2/8/5P2/5K2/8/8/r6R w - - 0 1")))
        assertNull(GameFindings.kindOf(Position.fromFen("8/5k2/8/5P2/5K2/8/8/b6R w - - 0 1")))
        assertNull("bare kings are no ending to train", GameFindings.kindOf(Position.fromFen("8/5k2/8/8/5K2/8/8/8 w - - 0 1")))
    }
}
