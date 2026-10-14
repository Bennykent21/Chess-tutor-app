package com.example.chess.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessRulesExpandedTest {

    @Test
    fun testStalematePositionDetectedCorrectly() {
        // Black King on a8, White Queen on c7, White King on a6 -> Black has no legal moves and is not in check.
        val fen = "k7/2Q5/K7/8/8/8/8/8 b - - 0 1"
        val pos = Position.fromFen(fen)

        assertEquals(PieceColor.BLACK, pos.sideToMove)
        assertFalse(LegalMoveGenerator.isKingInCheck(pos, PieceColor.BLACK))
        val legalMoves = LegalMoveGenerator.generateLegalMoves(pos)
        assertTrue(legalMoves.isEmpty())
        assertEquals(GameStatus.STALEMATE, LegalMoveGenerator.getGameStatus(pos))

        val state = GameState(pos)
        assertEquals(GameStatus.STALEMATE, state.status)
        assertTrue(state.isOver)
    }

    @Test
    fun testEnPassantExpiresAfterOnePly() {
        var pos = Position.initial()
        // 1. e4 (white)
        pos = LegalMoveGenerator.makeMove(pos, Move(Square.fromAlgebraic("e2"), Square.fromAlgebraic("e4")))
        assertEquals(Square.fromAlgebraic("e3"), pos.enPassantSquare)

        // 1... a6 (black makes a move that does not capture en passant)
        pos = LegalMoveGenerator.makeMove(pos, Move(Square.fromAlgebraic("a7"), Square.fromAlgebraic("a6")))
        // En passant target e3 is now expired!
        assertNull(pos.enPassantSquare)

        // 2. e5
        pos = LegalMoveGenerator.makeMove(pos, Move(Square.fromAlgebraic("e4"), Square.fromAlgebraic("e5")))
        assertNull(pos.enPassantSquare)

        // 2... d5 (black pushes pawn 2 squares -> d6 is new en passant target)
        pos = LegalMoveGenerator.makeMove(pos, Move(Square.fromAlgebraic("d7"), Square.fromAlgebraic("d5")))
        assertEquals(Square.fromAlgebraic("d6"), pos.enPassantSquare)

        // 3. h3 (white does NOT capture en passant)
        pos = LegalMoveGenerator.makeMove(pos, Move(Square.fromAlgebraic("h2"), Square.fromAlgebraic("h3")))
        // enPassantSquare must now be null!
        assertNull(pos.enPassantSquare)

        // White cannot capture en passant now
        val whiteMovesAfterExpiry = LegalMoveGenerator.generateLegalMoves(pos)
        assertFalse(whiteMovesAfterExpiry.any { it.isEnPassant })
    }

    @Test
    fun testKingMoveClearsBothCastlingRights() {
        val fen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"
        val pos = Position.fromFen(fen)
        assertTrue(pos.castlingRights.whiteKingside)
        assertTrue(pos.castlingRights.whiteQueenside)

        // White plays Ke2
        val next = LegalMoveGenerator.makeMove(pos, Move(Square.fromAlgebraic("e1"), Square.fromAlgebraic("e2")))
        assertFalse(next.castlingRights.whiteKingside)
        assertFalse(next.castlingRights.whiteQueenside)
        // Black's rights untouched
        assertTrue(next.castlingRights.blackKingside)
        assertTrue(next.castlingRights.blackQueenside)
    }

    @Test
    fun testRookMoveClearsOnlyThatSideCastlingRight() {
        val fen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"
        val pos = Position.fromFen(fen)

        // Move a1 rook to a2
        val next = LegalMoveGenerator.makeMove(pos, Move(Square.fromAlgebraic("a1"), Square.fromAlgebraic("a2")))
        assertFalse(next.castlingRights.whiteQueenside)
        assertTrue(next.castlingRights.whiteKingside)
    }

    @Test
    fun testRookCapturedOnCornerClearsCastlingRightForCapturedSide() {
        // White Bishop on d5 captures Black Rook on a8
        val fen = "r3k2r/8/8/3B4/8/8/8/4K3 w kq - 0 1"
        val pos = Position.fromFen(fen)
        assertTrue(pos.castlingRights.blackQueenside)
        assertTrue(pos.castlingRights.blackKingside)

        val captureRook = Move(Square.fromAlgebraic("d5"), Square.fromAlgebraic("a8"))
        val next = LegalMoveGenerator.makeMove(pos, captureRook)

        assertFalse(next.castlingRights.blackQueenside)
        assertTrue(next.castlingRights.blackKingside)
    }

    @Test
    fun testHalfmoveAndFullmoveCountersUpdateAccurately() {
        var pos = Position.initial()
        assertEquals(0, pos.halfmoveClock)
        assertEquals(1, pos.fullmoveNumber)

        // 1. Nf3 (quiet knight move -> halfmove increments to 1)
        pos = LegalMoveGenerator.makeMove(pos, Move(Square.fromAlgebraic("g1"), Square.fromAlgebraic("f3")))
        assertEquals(1, pos.halfmoveClock)
        assertEquals(1, pos.fullmoveNumber)

        // 1... Nf6 (black quiet knight move -> halfmove 2, fullmove becomes 2)
        pos = LegalMoveGenerator.makeMove(pos, Move(Square.fromAlgebraic("g8"), Square.fromAlgebraic("f6")))
        assertEquals(2, pos.halfmoveClock)
        assertEquals(2, pos.fullmoveNumber)

        // 2. e4 (pawn move -> halfmove resets to 0)
        pos = LegalMoveGenerator.makeMove(pos, Move(Square.fromAlgebraic("e2"), Square.fromAlgebraic("e4")))
        assertEquals(0, pos.halfmoveClock)
        assertEquals(2, pos.fullmoveNumber)
    }

    @Test
    fun testPromotionToAllPiecesGeneratesCorrectSan() {
        // White pawn on a7 advancing to a8 without check
        val posWithoutCheck = Position.fromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1")
        val queenMove = Move(Square.fromAlgebraic("a7"), Square.fromAlgebraic("a8"), promotion = PieceType.QUEEN)
        assertEquals("a8=Q+", SanFormatter.format(posWithoutCheck, queenMove))

        val rookMove = Move(Square.fromAlgebraic("a7"), Square.fromAlgebraic("a8"), promotion = PieceType.ROOK)
        assertEquals("a8=R+", SanFormatter.format(posWithoutCheck, rookMove))

        val knightMove = Move(Square.fromAlgebraic("a7"), Square.fromAlgebraic("a8"), promotion = PieceType.KNIGHT)
        assertEquals("a8=N", SanFormatter.format(posWithoutCheck, knightMove))

        val bishopMove = Move(Square.fromAlgebraic("a7"), Square.fromAlgebraic("a8"), promotion = PieceType.BISHOP)
        assertEquals("a8=B", SanFormatter.format(posWithoutCheck, bishopMove))
    }
}
