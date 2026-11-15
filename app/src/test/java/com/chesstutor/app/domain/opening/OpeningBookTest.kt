package com.chesstutor.app.domain.opening

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.repetitionKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

/** The master-game book for reviews (Phase 13B). */
class OpeningBookTest {

    private val game = "d2d4 d7d5 c1f4 b8c6 e2e3 c8f5 c2c4 e7e6 b1c3 f8b4".split(' ')

    private fun keys(moves: List<String>): List<String> {
        var position = Position.initial()
        val out = mutableListOf(position.repetitionKey())
        for (uci in moves) {
            position = LegalMoveGenerator.makeMove(position, LegalMoveGenerator.generateLegalMoves(position).first { it.uci == uci })
            out += position.repetitionKey()
        }
        return out
    }

    private fun bookOf(keys: List<String>): OpeningBook {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out -> keys.map(OpeningBook::fingerprint).sortedWith { a, b -> java.lang.Long.compareUnsigned(a, b) }.forEach(out::writeLong) }
        return OpeningBook.fromStream(ByteArrayInputStream(bytes.toByteArray()))
    }

    @Test
    fun theFingerprintIsTheSameAsTheBuildScripts() {
        // tools/build_book.py prints 0xe7550032f70614fc for the start.
        assertEquals(0xe7550032f70614fcUL.toLong(), OpeningBook.fingerprint(Position.initial().repetitionKey()))
    }

    @Test
    fun aBookCarriesAGameFurtherThanTheNamedLines() {
        val tree = File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream)
        assertEquals("the named lines stop after 2.Bf4", 3, tree.match(game).leftBookAt)
        val book = bookOf(keys(game.take(9)))
        assertTrue(book.contains(keys(game)[5]))
        assertFalse(book.contains(keys(game)[10]))
        assertEquals("the masters' positions count up to 5.Nc3", 9, tree.withBook(book).match(game).leftBookAt)
        assertEquals("names still come from the named lines", tree.match(game).opening, tree.withBook(book).match(game).opening)
    }
}
