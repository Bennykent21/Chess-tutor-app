package com.chesstutor.app.board

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardInputTest {

    private fun sq(name: String) = Square.fromAlgebraic(name)
    private val start = Position.initial()
    private val startMoves = LegalMoveGenerator.generateLegalMoves(start)

    private fun tap(selected: String?, square: String, movable: PieceColor? = PieceColor.WHITE, position: Position = start) =
        BoardInput.tap(selected?.let(::sq), sq(square), position, LegalMoveGenerator.generateLegalMoves(position), movable, autoQueen = false)

    @Test
    fun tapSelectsYourOwnPieceOnly() {
        assertEquals(BoardIntent.Select(sq("e2")), tap(null, "e2"))
        assertEquals(BoardIntent.Deselect, tap(null, "e7"))
        assertEquals(BoardIntent.Deselect, tap(null, "e4"))
    }

    @Test
    fun secondTapMovesSwitchesOrClears() {
        assertEquals(BoardIntent.Play(Move.fromUci("e2e4")), tap("e2", "e4"))
        assertEquals(BoardIntent.Select(sq("g1")), tap("e2", "g1"))
        assertEquals(BoardIntent.Deselect, tap("e2", "e2"))
        assertEquals("an illegal target just clears", BoardIntent.Deselect, tap("e2", "e5"))
    }

    @Test
    fun aReadOnlyBoardNeverSelects() {
        assertEquals(BoardIntent.Deselect, tap(null, "e2", movable = null))
        assertFalse(BoardInput.canPick(start, sq("e2"), null))
        assertTrue(BoardInput.canPick(start, sq("e2"), PieceColor.WHITE))
        assertFalse(BoardInput.canPick(start, sq("e2"), PieceColor.BLACK))
    }

    @Test
    fun dropsPlayLegalMovesAndRejectIllegalOnes() {
        assertEquals(BoardIntent.Play(Move.fromUci("g1f3")), BoardInput.drop(sq("g1"), sq("f3"), startMoves, autoQueen = false))
        assertEquals(BoardIntent.Rejected, BoardInput.drop(sq("g1"), sq("g3"), startMoves, autoQueen = false))
        assertEquals("dropping back home keeps it selected", BoardIntent.Select(sq("g1")), BoardInput.drop(sq("g1"), sq("g1"), startMoves, false))
        assertEquals("dropping off the board keeps it selected", BoardIntent.Select(sq("g1")), BoardInput.drop(sq("g1"), null, startMoves, false))
    }

    @Test
    fun promotionAsksUnlessAutoQueenIsOn() {
        val position = Position.fromFen("8/4P1k1/8/8/8/8/6K1/8 w - - 0 1")
        val moves = LegalMoveGenerator.generateLegalMoves(position)
        assertEquals(BoardIntent.ChoosePromotion(sq("e7"), sq("e8")), BoardInput.drop(sq("e7"), sq("e8"), moves, autoQueen = false))
        val auto = BoardInput.drop(sq("e7"), sq("e8"), moves, autoQueen = true) as BoardIntent.Play
        assertEquals(PieceType.QUEEN, auto.move.promotion)
        assertEquals(PieceType.KNIGHT, BoardInput.promotionMove(sq("e7"), sq("e8"), PieceType.KNIGHT, moves)?.promotion)
    }

    @Test
    fun targetsAreTheLegalDestinationsOfOnePiece() {
        assertEquals(setOf(sq("f3"), sq("h3")), BoardInput.targetsFrom(sq("g1"), startMoves))
        assertEquals(emptySet<Square>(), BoardInput.targetsFrom(sq("e1"), startMoves))
    }
}
