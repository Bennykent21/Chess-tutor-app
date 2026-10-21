package com.chesstutor.app.board

import com.chesstutor.app.core.Move
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisSessionTest {

    @Test
    fun playingAndSteppingForwardAnimateButJumpsDoNot() {
        val session = AnalysisSession()
        session.onMove(Move.fromUci("e2e4"))
        assertTrue(session.state.value.animateLastMove)
        session.onMove(Move.fromUci("e7e5"))

        session.back()
        assertFalse(session.state.value.animateLastMove)
        assertEquals(Move.fromUci("e2e4"), session.state.value.lastMove)

        session.forward()
        assertTrue(session.state.value.animateLastMove)

        session.toStart()
        assertNull(session.state.value.lastMove)
        assertFalse(session.state.value.animateLastMove)
        assertEquals(Position.initial(), session.state.value.position)
    }

    @Test
    fun theViewTracksNavigationAndSideLines() {
        val session = AnalysisSession()
        assertFalse(session.view.value.canGoBack)
        session.onMove(Move.fromUci("e2e4"))
        session.onMove(Move.fromUci("e7e5"))
        session.back()
        session.onMove(Move.fromUci("c7c5"))

        val view = session.view.value
        assertTrue(view.inVariation)
        assertTrue(view.canGoBack)
        assertFalse(view.canGoForward)
        assertEquals(session.currentId, view.currentId)

        session.promoteCurrent()
        assertFalse(session.view.value.inVariation)
    }

    @Test
    fun movesListPathToTheCurrentPositionAndCountVersions() {
        val session = AnalysisSession()
        session.onMove(Move.fromUci("d2d4"))
        session.onMove(Move.fromUci("g8f6"))
        session.back()
        val state = session.state.value
        assertEquals(listOf(Move.fromUci("d2d4")), state.moves)
        assertEquals(3, state.version)
        assertEquals(Square.fromAlgebraic("d4"), state.lastMove?.to)
    }

    @Test
    fun resetStartsAFreshTreeAndDeleteLeavesTheLine() {
        val session = AnalysisSession()
        session.onMove(Move.fromUci("e2e4"))
        session.deleteCurrent()
        assertTrue(session.view.value.tokens.isEmpty())
        val endgame = Position.fromFen("8/4P2k/8/8/8/8/6K1/8 w - - 0 1")
        session.reset(endgame)
        assertEquals(endgame, session.state.value.position)
        assertTrue(session.view.value.tokens.isEmpty())
    }
}
