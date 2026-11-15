package com.chesstutor.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chess960 rules: perft counts from python-chess (chess960=True) for six positions from the
 * Chess Programming Wiki's Chess960 perft list, the start-position numbering, and castling.
 */
class Chess960Test {

    private fun perft(position: Position, depth: Int): Long {
        if (depth == 0) return 1L
        return LegalMoveGenerator.generateLegalMoves(position).sumOf { perft(LegalMoveGenerator.makeMove(position, it), depth - 1) }
    }

    private fun counts(fen: String) = Position.fromFen(fen).let { p -> (1..3).map { perft(p, it) } }

    @Test
    fun perftMatchesPythonChess() {
        assertEquals(listOf(21L, 528L, 12189L), counts("bqnb1rkr/pp3ppp/3ppn2/2p5/5P2/P2P4/NPP1P1PP/BQ1BNRKR w HFhf - 2 9"))
        assertEquals(listOf(21L, 807L, 18002L), counts("2nnrbkr/p1qppppp/8/1ppb4/6PP/3PP3/PPP2P2/BQNNRBKR w HEhe - 1 9"))
        assertEquals(listOf(20L, 479L, 10471L), counts("b1q1rrkb/pppppppp/3nn3/8/P7/1PPP4/4PPPP/BQNNRKRB w GE - 1 9"))
        assertEquals(listOf(22L, 593L, 13440L), counts("qbbnnrkr/2pp2pp/p7/1p2pp2/8/P3PP2/1PPP1KPP/QBBNNR1R w hf - 0 9"))
        assertEquals(listOf(29L, 502L, 14569L), counts("1rqbkrbn/1ppppp1p/1n6/p1N3p1/8/2P4P/PP1PPPP1/1RQBKRBN w FBfb - 0 9"))
        assertEquals(listOf(29L, 755L, 22924L), counts("rkb2nqr/pp3ppp/2np4/2p1p3/4P3/1PN2N2/P1PP1PPP/RKB1Q2R w HAha - 0 9"))
    }

    @Test
    fun startPositionsAreNumberedAsScharnaglDid() {
        assertEquals("RNBQKBNR", Chess960.backRank(518))
        assertEquals("BBQNNRKR", Chess960.backRank(0))
        assertEquals("RKRNNQBB", Chess960.backRank(959))
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w HAha - 0 1", Chess960.startFen(518))
        assertEquals("all 960 are different", 960, (0 until 960).map(Chess960::backRank).toSet().size)
        // A knight in a corner has one square, and some starts can castle at once: python-chess
        // counts 18 to 21 moves, 18,882 in all, and 21 for position 3.
        val moves = (0 until 960).map { n ->
            val position = Position.fromFen(Chess960.startFen(n))
            assertEquals(Chess960.startFen(n), position.toFen())
            LegalMoveGenerator.generateLegalMoves(position).size
        }
        assertEquals(18882, moves.sum())
        assertEquals(21, moves[3])
    }

    @Test
    fun castlingMovesBothPiecesAndIsWrittenAsTheKingTakingItsRook() {
        // King on f1, rooks on b1 and g1: castling short leaves the king on g1 and the rook on f1.
        val position = Position.fromFen("1r3kr1/pppppppp/8/8/8/8/PPPPPPPP/1R3KR1 w GBgb - 0 1")
        val castles = LegalMoveGenerator.generateLegalMoves(position).filter { it.isCastling }
        assertEquals(listOf("f1g1"), castles.map { it.uci }.filter { it == "f1g1" })
        val short = castles.single { it.uci == "f1g1" }
        val after = LegalMoveGenerator.makeMove(position, short)
        assertEquals(Piece(PieceType.KING, PieceColor.WHITE), after.pieceAt(Square.fromAlgebraic("g1")))
        assertEquals(Piece(PieceType.ROOK, PieceColor.WHITE), after.pieceAt(Square.fromAlgebraic("f1")))
        assertFalse(after.castlingRights.whiteKingside || after.castlingRights.whiteQueenside)
        assertTrue(after.castlingRights.blackKingside)
        assertEquals("O-O", SanFormatter.format(position, short))
        assertEquals("a castle is not a capture", 1, after.halfmoveClock)
    }

    @Test
    fun onTheBoardTheKingCastlesOntoItsRookOrToItsSquare() {
        val position = Position.fromFen("1r3kr1/pppppppp/8/8/8/8/PPPPPPPP/1R3KR1 w GBgb - 0 1")
        val legal = LegalMoveGenerator.generateLegalMoves(position)
        val f1 = Square.fromAlgebraic("f1")
        val g1 = Square.fromAlgebraic("g1")
        val c1 = Square.fromAlgebraic("c1")
        // Tap the king, then its rook: the rook is not picked up, the king castles.
        val tap = com.chesstutor.app.board.BoardInput.tap(f1, g1, position, legal, PieceColor.WHITE, autoQueen = true)
        assertEquals("f1g1", (tap as com.chesstutor.app.board.BoardIntent.Play).move.uci)
        // Dragging the king to c1 castles long (the rook goes from b1 to d1).
        val drop = com.chesstutor.app.board.BoardInput.drop(f1, c1, legal, autoQueen = true)
        val long = (drop as com.chesstutor.app.board.BoardIntent.Play).move
        assertEquals("f1b1", long.uci)
        val plan = com.chesstutor.app.board.MoveEffects.animationPlan(position, long)
        assertEquals(listOf("f1c1", "b1d1"), plan.slides.map { it.from.algebraic + it.to.algebraic })
        assertNull("nothing is taken", plan.captured)
        assertTrue(c1 in com.chesstutor.app.board.BoardInput.targetsFrom(f1, legal))
    }

    @Test
    fun standardChessIsUnchanged() {
        val position = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val castles = LegalMoveGenerator.generateLegalMoves(position).filter { it.isCastling }.map { it.uci }.toSet()
        assertEquals(setOf("e1g1", "e1c1"), castles)
        assertEquals("KQkq", position.castlingRights.fenString)
        assertNull(LegalMoveGenerator.castleSquares(position, LegalMoveGenerator.generateLegalMoves(position).first { it.uci == "e1f1" }))
        assertFalse(Chess960.isChess960(position.toFen()))
        assertTrue(Chess960.isChess960(Chess960.startFen(3)))
    }
}
