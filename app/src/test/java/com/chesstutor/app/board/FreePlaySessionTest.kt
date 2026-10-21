package com.chesstutor.app.board

import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreePlaySessionTest {

    @Test
    fun movesAlternateSidesAndAnimate() {
        val session = FreePlaySession()
        assertEquals(PieceColor.WHITE, session.state.value.movable)
        assertEquals(BoardSound.MOVE, session.onMove(Move.fromUci("e2e4")).sound)

        val state = session.state.value
        assertEquals(PieceColor.BLACK, state.movable)
        assertEquals(Move.fromUci("e2e4"), state.lastMove)
        assertEquals(Position.initial(), state.previous)
        assertTrue(state.animateLastMove)
        assertEquals(1, state.version)
        assertEquals(20, state.legalMoves.size)
    }

    @Test
    fun undoStepsBackWithoutAnimating() {
        val session = FreePlaySession()
        session.onMove(Move.fromUci("e2e4"))
        session.onMove(Move.fromUci("e7e5"))
        session.undo()

        val state = session.state.value
        assertEquals(Move.fromUci("e2e4"), state.lastMove)
        assertEquals(PieceColor.BLACK, state.movable)
        assertFalse(state.animateLastMove)
        assertEquals(3, state.version)

        session.undo()
        assertNull(session.state.value.lastMove)
        assertFalse(session.canUndo)
        session.undo() // nothing left: no crash, no change
        assertEquals(Position.initial(), session.state.value.position)
    }

    @Test
    fun matedSideCannotMove() {
        val session = FreePlaySession()
        listOf("f2f3", "e7e5", "g2g4").forEach { session.onMove(Move.fromUci(it)) }
        val feedback = session.onMove(Move.fromUci("d8h4"))
        assertEquals(BoardSound.CHECK, feedback.sound)
        assertNull(session.state.value.movable)
    }

    @Test
    fun resetStartsOverFromAnyPosition() {
        val session = FreePlaySession()
        session.onMove(Move.fromUci("e2e4"))
        val endgame = Position.fromFen("8/4P1k1/8/8/8/8/6K1/8 w - - 0 1")
        session.reset(endgame)
        assertEquals(endgame, session.state.value.position)
        assertNull(session.state.value.lastMove)
        assertFalse(session.canUndo)
    }
}
