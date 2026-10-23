package com.chesstutor.app.domain.game

import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.Square
import com.chesstutor.app.domain.PgnFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupBoardTest {

    private fun sq(name: String) = Square.fromAlgebraic(name)
    private val whiteQueen = Piece(PieceType.QUEEN, PieceColor.WHITE)
    private val blackKing = Piece(PieceType.KING, PieceColor.BLACK)

    @Test
    fun theStartingPositionRoundTripsThroughFen() {
        assertEquals(Position.STARTING_FEN, SetupBoard.START.toFen())
        assertNull(SetupBoard.START.problem())
    }

    @Test
    fun placingTogglesAndKingsAreUnique() {
        val board = SetupBoard.KINGS_ONLY.place(sq("d4"), whiteQueen)
        assertEquals(whiteQueen, board.pieceAt(sq("d4")))
        assertNull("the same piece again removes it", board.place(sq("d4"), whiteQueen).pieceAt(sq("d4")))
        val moved = board.place(sq("a8"), blackKing)
        assertEquals(blackKing, moved.pieceAt(sq("a8")))
        assertNull("one king per side", moved.pieceAt(sq("e8")))
        assertNull("null erases", board.place(sq("d4"), null).pieceAt(sq("d4")))
    }

    @Test
    fun castlingRightsFollowKingsAndRooks() {
        val noRook = SetupBoard.START.place(sq("h1"), null)
        assertTrue(noRook.toFen().contains(" w Qkq - 0 1"))
        assertTrue(SetupBoard.KINGS_ONLY.toFen().endsWith(" w - - 0 1"))
    }

    @Test
    fun unplayablePositionsSayWhy() {
        assertEquals("Each side needs exactly one king.", SetupBoard.KINGS_ONLY.place(sq("e8"), null).problem())
        val pawnOnEighth = SetupBoard.KINGS_ONLY.place(sq("a8"), Piece(PieceType.PAWN, PieceColor.WHITE))
        assertEquals("Pawns cannot stand on the first or last rank.", pawnOnEighth.problem())
        // White queen gives check to the black king, but it is White's turn.
        val checkOnWrongSide = SetupBoard.KINGS_ONLY.place(sq("e5"), whiteQueen)
        assertTrue(checkOnWrongSide.problem()!!.contains("not to move is in check"))
        assertNull(checkOnWrongSide.withSideToMove(PieceColor.BLACK).problem())
        val mated = SetupBoard.fromFen("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1")!!.place(sq("a8"), Piece(PieceType.ROOK, PieceColor.WHITE)).place(sq("a1"), null).withSideToMove(PieceColor.BLACK)
        assertEquals("That is already checkmate.", mated.problem())
    }

    @Test
    fun fenThatCannotBeReadGivesNull() {
        assertNull(SetupBoard.fromFen("not a fen"))
        assertNull(SetupBoard.fromFen("8/8/8 w - - 0 1"))
        assertEquals(PieceColor.BLACK, SetupBoard.fromFen("4k3/8/8/8/8/8/8/4K3 b - - 0 1")!!.sideToMove)
    }

    @Test
    fun aGameFromASetUpPositionRecordsItAndNumbersFromThere() {
        val pgn = PgnFormatter.formatPgn(
            moves = listOf("Kd7", "Qd5+"),
            result = "*",
            startFen = "4k3/8/8/8/8/8/3Q4/4K3 b - - 0 12",
        )
        assertTrue(pgn.contains("[SetUp \"1\"]"))
        assertTrue(pgn.contains("[FEN \"4k3/8/8/8/8/8/3Q4/4K3 b - - 0 12\"]"))
        assertTrue(pgn, pgn.endsWith("12... Kd7 13. Qd5+ *"))
        val normal = PgnFormatter.formatPgn(moves = listOf("e4", "e5", "Nf3"), result = "*")
        assertTrue(normal.endsWith("1. e4 e5 2. Nf3 *"))
        assertTrue(!normal.contains("[FEN"))
    }
}
