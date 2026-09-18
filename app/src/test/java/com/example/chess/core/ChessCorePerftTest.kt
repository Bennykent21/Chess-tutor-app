package com.example.chess.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Perft tests validate the move generator against canonical chess positions.
 *
 * Perft counts legal move-tree leaves at a fixed depth. These tests are
 * intentionally independent of the app/domain layer so regressions in
 * castling, en passant, checks, and promotions are caught at the rules core.
 */
class ChessCorePerftTest {

    @Test
    fun startingPositionPerft() {
        val position = Position.initial()

        assertEquals(20L, perft(position, 1))
        assertEquals(400L, perft(position, 2))
        assertEquals(8902L, perft(position, 3))
    }

    @Test
    fun kiwipetePerft() {
        val position = Position.fromFen(
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        )

        assertEquals(48L, perft(position, 1))
        assertEquals(2039L, perft(position, 2))
        assertEquals(97862L, perft(position, 3))
    }

    @Test
    fun enPassantAndPromotionPositionPerft() {
        val position = Position.fromFen(
            "8/P6k/8/3pP3/8/8/6K1/8 w - d6 0 1"
        )

        // The exact count exercises en-passant capture and promotion branches.
        assertEquals(14L, perft(position, 1))
    }

    private fun perft(position: Position, depth: Int): Long {
        if (depth == 0) return 1L

        var nodes = 0L
        for (move in LegalMoveGenerator.generateLegalMoves(position)) {
            nodes += perft(
                LegalMoveGenerator.makeMove(position, move),
                depth - 1
            )
        }
        return nodes
    }
}
