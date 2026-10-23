package com.chesstutor.app.domain.opening

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.repetitionKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OpeningTreeTest {

    private val sample = """
        C50	Italian Game	e2e4 e7e5 g1f3 b8c6 f1c4
        C50	Italian Game: Giuoco Piano	e2e4 e7e5 g1f3 b8c6 f1c4 f8c5
        C55	Italian Game: Two Knights Defense	e2e4 e7e5 g1f3 b8c6 f1c4 g8f6
        C44	King's Pawn Game: Tayler Opening	e2e4 e7e5 g1f3 b8c6 f1e2
        B20	Sicilian Defense	e2e4 c7c5
        B90	Sicilian Defense: Najdorf Variation	e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6
        E00	Indian Defense: East Indian Defense	d2d4 g8f6 c2c4 e7e6
        A13	English Opening: Agincourt Defense	c2c4 e7e6
        A40	Horwitz Defense	d2d4 e7e6 c2c4
        E00	Catalan Opening	d2d4 g8f6 c2c4 e7e6 g2g3
        A00	Broken	e2e4 e2e4
        A00		e2e4
    """.trimIndent()

    private val tree = OpeningTree.parse(sample.lineSequence())

    private fun keyAfter(vararg moves: String): String {
        var position = Position.initial()
        moves.forEach { uci ->
            position = LegalMoveGenerator.makeMove(position, LegalMoveGenerator.generateLegalMoves(position).first { it.uci == uci })
        }
        return position.repetitionKey()
    }

    @Test
    fun illegalAndMalformedLinesAreSkippedAndCounted() {
        assertEquals(10, tree.openings.size)
        assertEquals(2, tree.skippedLines)
    }

    @Test
    fun bookMovesAreWeightedByHowManyLinesUseThem() {
        assertEquals(
            listOf(BookMove("e2e4", 6), BookMove("d2d4", 3), BookMove("c2c4", 1)),
            tree.bookMoves(keyAfter()),
        )
        val afterNc6 = tree.bookMoves(keyAfter("e2e4", "e7e5", "g1f3", "b8c6"))
        assertEquals(listOf("f1c4", "f1e2"), afterNc6.map { it.uci })
        assertEquals(3, afterNc6.first().weight)
    }

    @Test
    fun transpositionsMeetAtOnePosition() {
        val viaD4 = keyAfter("d2d4", "g8f6", "c2c4", "e7e6")
        val viaC4 = keyAfter("c2c4", "e7e6", "d2d4", "g8f6")
        assertEquals(viaD4, viaC4)
        assertEquals("Catalan's 3.g3 is reachable from either move order", listOf("g2g3"), tree.bookMoves(viaC4).map { it.uci })
        // The first line to name a position keeps it.
        assertEquals("Indian Defense: East Indian Defense", tree.openingAt(viaC4)?.name)
    }

    @Test
    fun aGameIsNamedByTheDeepestNamedPositionItReached() {
        val giuoco = tree.match(listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f8c5", "c2c3"))
        assertEquals("Italian Game: Giuoco Piano", giuoco.opening?.name)
        assertEquals(6, giuoco.bookPlies)
        assertEquals(6, giuoco.leftBookAt)

        val stillIn = tree.match(listOf("e2e4", "c7c5", "g1f3"))
        assertEquals("Sicilian Defense", stillIn.opening?.name)
        assertNull(stillIn.leftBookAt)

        // 1.c4 e6 2.d4 is the Horwitz position reached from the other side; no line plays these moves.
        val transposed = tree.match(listOf("c2c4", "e7e6", "d2d4", "g8f6"))
        assertNull("an unusual move order still counts as book", transposed.leftBookAt)
        assertEquals("Indian Defense: East Indian Defense", transposed.opening?.name)

        val offAtOnce = tree.match(listOf("a2a3"))
        assertNull(offAtOnce.opening)
        assertEquals(0, offAtOnce.leftBookAt)
        assertEquals("an illegal move ends the book too", 1, tree.match(listOf("e2e4", "e2e4")).leftBookAt)
    }

    @Test
    fun namesSplitIntoFamilyAndVariation() {
        val najdorf = tree.openings.first { it.name.endsWith("Najdorf Variation") }
        assertEquals("Sicilian Defense", najdorf.family)
        assertEquals("Najdorf Variation", najdorf.variation)
        assertTrue(najdorf.isWithin("Sicilian Defense"))
        assertFalse("a different word is a different opening", Opening("C", "Italian Gambit", listOf("e2e4")).isWithin("Italian Game"))
        assertTrue("an added word stays inside", Opening("C", "King's Gambit Accepted", listOf("e2e4")).isWithin("King's Gambit"))
    }

    @Test
    fun familiesListTheirLinesShortestFirst() {
        val italian = tree.families.first()
        assertEquals("Italian Game", italian.name)
        assertEquals(3, italian.lines.size)
        assertEquals("Italian Game", italian.root.name)
    }

    @Test
    fun aRepertoireKeepsOnlyThePickedOpeningsForEachSide() {
        val repertoire = Repertoire.of(tree, whiteOpenings = listOf("Italian Game"), blackOpenings = listOf("Sicilian Defense"))
        assertEquals(listOf("e2e4"), repertoire.movesFor(PieceColor.WHITE, keyAfter()).map { it.uci })
        assertEquals(listOf("f1c4"), repertoire.movesFor(PieceColor.WHITE, keyAfter("e2e4", "e7e5", "g1f3", "b8c6")).map { it.uci })
        assertEquals(
            "the bot's Italian lines both continue after 3.Bc4",
            setOf("f8c5", "g8f6"),
            repertoire.movesFor(PieceColor.WHITE, keyAfter("e2e4", "e7e5", "g1f3", "b8c6", "f1c4")).map { it.uci }.toSet(),
        )
        assertEquals(listOf("c7c5"), repertoire.movesFor(PieceColor.BLACK, keyAfter("e2e4")).map { it.uci })
        assertTrue("1.d4 is outside its Black repertoire", repertoire.movesFor(PieceColor.BLACK, keyAfter("d2d4")).isEmpty())
        assertTrue(repertoire.covers(PieceColor.BLACK))
        assertFalse(Repertoire.NONE.covers(PieceColor.WHITE))
    }

    @Test
    fun aSingleLineRepertoireFollowsThatLineOnly() {
        val line = Repertoire.line(listOf("e2e4", "c7c5", "g1f3"))
        assertEquals(listOf("c7c5"), line.movesFor(PieceColor.BLACK, keyAfter("e2e4")).map { it.uci })
        assertTrue(line.movesFor(PieceColor.WHITE, keyAfter("e2e4", "c7c5", "g1f3")).isEmpty())
    }

    /** The bundled database (tools/build_openings.py): every line replays with the app's rules. */
    @Test
    fun theBundledDatabaseLoadsCompletely() {
        val asset = File("src/main/assets/openings.tsv.gz")
        assertTrue("run from the app module: ${asset.absolutePath}", asset.exists())
        val full = asset.inputStream().use(OpeningTree::fromGzipStream)
        assertEquals("no line may be dropped", 0, full.skippedLines)
        assertTrue("expected the full Lichess list, found ${full.openings.size}", full.openings.size > 3_500)

        val najdorf = full.match("e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6".split(' '))
        assertEquals("Sicilian Defense: Najdorf Variation", najdorf.opening?.name)
        assertEquals("B90", najdorf.opening?.eco)
        val first = full.bookMoves(keyAfter()).map { it.uci }
        assertEquals("1.e4 has the most named lines", "e2e4", first.first())
        assertTrue(first.containsAll(listOf("d2d4", "c2c4", "g1f3")))
        assertTrue(full.families.any { it.name == "Queen's Gambit Declined" })
    }
}
