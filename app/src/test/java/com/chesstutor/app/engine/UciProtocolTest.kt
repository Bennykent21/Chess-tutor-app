package com.chesstutor.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UciProtocolTest {

    @Test
    fun parsesInfoScoreAndPrincipalVariation() {
        val info = UciProtocol.parseInfoLine(
            "info depth 18 score cp -42 nodes 1200 pv e7e5 g1f3 b8c6"
        )

        assertEquals(18, info?.depth)
        assertEquals(-42, info?.centipawns)
        assertNull(info?.mateInMoves)
        assertEquals(listOf("e7e5", "g1f3", "b8c6"), info?.pv)
    }

    @Test
    fun parsesMateScore() {
        val info = UciProtocol.parseInfoLine(
            "info depth 22 score mate 3 pv f7f8q"
        )

        assertEquals(22, info?.depth)
        assertEquals(3, info?.mateInMoves)
        assertNull(info?.centipawns)
        assertEquals(listOf("f7f8q"), info?.pv)
    }

    @Test
    fun ignoresNonInfoLines() {
        assertNull(UciProtocol.parseInfoLine("readyok"))
        assertNull(UciProtocol.parseInfoLine("id name Stockfish"))
    }

    @Test
    fun parsesBestMove() {
        assertEquals("e2e4", UciProtocol.parseBestMove("bestmove e2e4"))
        assertEquals("e7e8q", UciProtocol.parseBestMove("bestmove e7e8q ponder e2e4"))
    }

    @Test
    fun treatsNoBestMoveAsAbsent() {
        assertNull(UciProtocol.parseBestMove("bestmove (none)"))
        assertNull(UciProtocol.parseBestMove("bestmove"))
    }
}
