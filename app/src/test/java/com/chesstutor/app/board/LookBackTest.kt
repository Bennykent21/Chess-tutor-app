package com.chesstutor.app.board

import androidx.compose.runtime.mutableStateOf
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LookBackTest {

    private fun moves(vararg uci: String): List<Move> {
        var position = Position.initial()
        return uci.map { u ->
            LegalMoveGenerator.generateLegalMoves(position).first { it.uci == u }.also { position = LegalMoveGenerator.makeMove(position, it) }
        }
    }

    private val game = moves("e2e4", "e7e5", "g1f3")

    @Test
    fun itStartsOnTheLiveBoard() {
        val look = LookBack(mutableStateOf(null), Position.initial(), game)
        assertNull(look.shownPly)
        assertEquals(3, look.ply)
        assertNull(look.past)
    }

    @Test
    fun backAndForwardStepThroughThePastAndForwardEndsLive() {
        val look = LookBack(mutableStateOf(null), Position.initial(), game)
        look.back()
        assertEquals(2, look.shownPly)
        assertEquals("the move that led there is marked", game[1].from to game[1].to, look.pastMove)
        look.back()
        look.forward()
        assertEquals(2, look.shownPly)
        look.forward()
        assertNull("the latest move is the live board", look.shownPly)
    }

    @Test
    fun thePastPositionIsTheGameAfterThatManyMoves() {
        val look = LookBack(mutableStateOf(null), Position.initial(), game)
        look.show(1)
        assertEquals(LegalMoveGenerator.makeMove(Position.initial(), game[0]).toFen(), look.past?.toFen())
        look.show(0)
        assertEquals(Position.initial().toFen(), look.past?.toFen())
        assertFalse("nothing before the first position", look.canGoBack)
        look.live()
        assertTrue(look.canGoBack)
        assertNull(look.past)
    }

    @Test
    fun aNewMoveKeepsThePlaceLookedAt() {
        val viewed = mutableStateOf<Int?>(null)
        LookBack(viewed, Position.initial(), game.take(2)).show(1)
        val later = LookBack(viewed, Position.initial(), game)
        assertEquals(1, later.shownPly)
    }
}
