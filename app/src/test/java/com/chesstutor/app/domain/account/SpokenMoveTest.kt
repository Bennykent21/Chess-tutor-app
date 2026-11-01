package com.chesstutor.app.domain.account

import org.junit.Assert.assertEquals
import org.junit.Test

/** Moves in the coach's words (beta review: name the pieces). */
class SpokenMoveTest {

    @Test
    fun piecesCapturesCastlingAndPromotionReadAsWords() {
        assertEquals("Knight to f6", SpokenMove.of("Nf6"))
        assertEquals("Bishop to d3", SpokenMove.of("Bd3"))
        assertEquals("Pawn to e4", SpokenMove.of("e4"))
        assertEquals("Pawn takes d5", SpokenMove.of("exd5"))
        assertEquals("Queen takes f7", SpokenMove.of("Qxf7#"))
        assertEquals("Rook to d1", SpokenMove.of("Rad1+"))
        assertEquals("Knight to d2", SpokenMove.of("Nbd2"))
        assertEquals("Castling short", SpokenMove.of("O-O"))
        assertEquals("Castling long", SpokenMove.of("O-O-O+"))
        assertEquals("Pawn to e8, becoming a queen", SpokenMove.of("e8=Q+"))
        assertEquals("knight to f6", SpokenMove.inSentence("Nf6"))
    }
}
