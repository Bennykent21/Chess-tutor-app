package com.chesstutor.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading SAN directly must agree with writing the SAN of every legal move. */
class SanReplayTest {

    private fun uci(fen: String, san: String): String? {
        val position = Position.fromFen(fen)
        return SanReplay.moveFor(position, LegalMoveGenerator.generateLegalMoves(position), san)?.uci
    }

    @Test
    fun everyLegalMoveInTheseGamesIsFoundFromItsOwnSan() {
        val fens = listOf(
            Position.STARTING_FEN,
            "r3k2r/pppq1ppp/2n1bn2/3pp3/1b1PP3/2N1BN2/PPPQBPPP/R3K2R w KQkq - 4 9", // castling both ways
            "8/P5k1/8/8/8/8/1p4K1/8 w - - 0 1", // promotions
            "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2", // en passant
            "4k3/8/2N3N1/8/2N3N1/8/8/4K3 w - - 0 1", // knights needing file, rank or both
            "4k3/8/8/8/1R5R/8/8/1R2K3 w - - 0 1", // rooks needing a file or a rank
        )
        for (fen in fens) {
            val position = Position.fromFen(fen)
            val legal = LegalMoveGenerator.generateLegalMoves(position)
            for (move in legal) {
                val san = SanFormatter.format(position, move)
                assertEquals("$san in $fen", move.uci, SanReplay.moveFor(position, legal, san)?.uci)
            }
        }
    }

    @Test
    fun formsFromOtherSitesAreRead() {
        assertEquals("e1g1", uci("4k3/8/8/8/8/8/8/4K2R w K - 0 1", "0-0"))
        assertEquals("e1c1", uci("4k3/8/8/8/8/8/8/R3K3 w Q - 0 1", "O-O-O+"))
        assertEquals("a7a8q", uci("8/P5k1/8/8/8/8/6K1/8 w - - 0 1", "a8Q"))
        assertEquals("a7a8n", uci("8/P5k1/8/8/8/8/6K1/8 w - - 0 1", "a8=N+"))
    }

    @Test
    fun nonsenseNamesNoMove() {
        assertNull(uci(Position.STARTING_FEN, "Qh5"))
        assertNull(uci(Position.STARTING_FEN, "e5"))
        assertNull(uci(Position.STARTING_FEN, "banana"))
        assertNull(uci(Position.STARTING_FEN, "Z"))
    }

    /** Prints how much the direct reading saves; the point of this class. */
    @Test
    fun readingSanIsMuchFasterThanNamingEveryMove() {
        val sans = "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 d6 c3 O-O h3 Nb8 d4 Nbd7 c4 c6 cxb5 axb5 Nc3 Bb7 Bg5 b4 Nb1 h6 Bh4 c5 dxe5 Nxe4 Bxe7 Qxe7 exd6 Qf6 Nbd2 Nxd6".split(' ')
        fun direct(): Int {
            var position = Position.initial()
            for (san in sans) {
                val legal = LegalMoveGenerator.generateLegalMoves(position)
                position = LegalMoveGenerator.makeMove(position, SanReplay.moveFor(position, legal, san) ?: error(san))
            }
            return position.squares.size
        }
        fun naming(): Int {
            var position = Position.initial()
            for (san in sans) {
                val legal = LegalMoveGenerator.generateLegalMoves(position)
                position = LegalMoveGenerator.makeMove(position, legal.first { SanFormatter.format(position, it).trimEnd('+', '#') == san })
            }
            return position.squares.size
        }
        fun millis(block: () -> Int): Long {
            repeat(30) { block() } // warm up
            val start = System.nanoTime()
            repeat(100) { block() }
            return (System.nanoTime() - start) / 1_000_000
        }
        val old = millis(::naming)
        val new = millis(::direct)
        println("SAN replay of 100 games: naming every move $old ms, reading the token $new ms")
        assertTrue("reading should beat naming: $new against $old", new < old)
    }
}
