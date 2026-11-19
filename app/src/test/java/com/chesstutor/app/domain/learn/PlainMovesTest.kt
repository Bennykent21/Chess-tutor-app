package com.chesstutor.app.domain.learn

import org.junit.Assert.assertEquals
import org.junit.Test

/** Notation in plain words for beginner courses (D99). */
class PlainMovesTest {

    @Test
    fun pieceMovesCapturesAndCastlingBecomeWords() {
        assertEquals("Bishop to f5: the bishop goes first", PlainMoves.soften("3...Bf5: the bishop goes first"))
        assertEquals("Pawn takes on d5 opens the c8 bishop.", PlainMoves.soften("exd5 opens the c8 bishop."))
        assertEquals("After castling, the king is safe.", PlainMoves.soften("After O-O, the king is safe."))
        assertEquals("Then knight takes on e5 with check.", PlainMoves.soften("Then Nxe5+."))
        assertEquals("Queen takes on f7, checkmate.", PlainMoves.soften("Qxf7#."))
    }

    @Test
    fun aSquareOnItsOwnStaysButNumberedAndListedPawnMovesAreSaid() {
        assertEquals("Pawn to d3 protects e4.", PlainMoves.soften("4.d3 protects e4."))
        assertEquals("Then pawn to e6 and pawn to c5 hit the chain.", PlainMoves.soften("Then ...e6 and ...c5 hit the chain."))
        assertEquals("Pawn to e4, pawn to e5, knight to f3, knight to c6 is the start.", PlainMoves.soften("1.e4 e5 2.Nf3 Nc6 is the start."))
        assertEquals("The knight on f3 attacks e5.", PlainMoves.soften("The knight on f3 attacks e5."))
    }

    @Test
    fun promotionNamesThePiece() {
        assertEquals("Pawn to e8, becoming a queen wins.", PlainMoves.soften("e8=Q wins.").let { it })
        assertEquals("Pawn takes on b8, becoming a knight with check.", PlainMoves.soften("cxb8=N+."))
    }
}
