package com.chesstutor.app.domain.opening

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class OpeningTrainerTest {

    private val tree by lazy { File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream) }

    @Test
    fun everyOpeningHasLegalLinesFromTheDatabase() {
        assertEquals(15, OpeningTrainer.openings.size)
        assertEquals(OpeningTrainer.openings.size, OpeningTrainer.openings.map { it.id }.toSet().size)
        OpeningTrainer.openings.forEach { opening ->
            val lines = OpeningTrainer.linesFor(opening, tree)
            assertTrue("${opening.family}: ${lines.size} lines", lines.size >= 4)
            if (opening.variations.isNotEmpty()) assertEquals("every listed variation is in the database: ${opening.id}", opening.variations.size, lines.size)
            lines.forEach { line ->
                assertTrue("${line.name} gives the player a move", line.playerMoves >= 1)
                var position = Position.initial()
                line.moves.forEach { uci ->
                    val move = LegalMoveGenerator.generateLegalMoves(position).firstOrNull { it.uci == uci }
                    assertTrue("$uci is legal in ${line.name}", move != null)
                    position = LegalMoveGenerator.makeMove(position, move!!)
                }
            }
        }
        val najdorf = OpeningTrainer.linesFor(OpeningTrainer.byId("sicilian")!!, tree).first()
        assertEquals("Najdorf Variation", najdorf.variation)
        assertEquals(listOf("e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4", "g8f6", "b1c3", "a7a6"), najdorf.moves.take(10))
        assertEquals(najdorf.moves.size / 2, najdorf.playerMoves)
        assertTrue(najdorf.id.startsWith("b:"))
    }

    @Test
    fun cleanRunsSpaceALineOutAndASlipBringsItBack() {
        val day = TimeUnit.DAYS.toMillis(1)
        val first = OpeningTrainer.afterRun(null, "w:x", clean = true, now = 0)
        assertEquals(1, first.stage)
        assertEquals(day, first.dueAt)
        assertFalse(first.learned)
        val second = OpeningTrainer.afterRun(first, "w:x", clean = true, now = day)
        assertEquals(2, second.stage)
        assertEquals(day + 3 * day, second.dueAt)
        assertTrue(second.learned)
        val slip = OpeningTrainer.afterRun(second, "w:x", clean = false, now = 5 * day)
        assertEquals(0, slip.stage)
        assertEquals(5 * day, slip.dueAt)
        assertEquals(3, slip.attempts)
        assertEquals(2, slip.cleanRuns)

        val lines = listOf(TrainerLine("A", listOf("e2e4"), PieceColor.WHITE), TrainerLine("B", listOf("d2d4"), PieceColor.WHITE))
        val progress = mapOf("w:A" to second)
        assertTrue("not due yet", OpeningTrainer.due(lines, progress, now = 2 * day).isEmpty())
        assertEquals(listOf("A"), OpeningTrainer.due(lines, progress, now = 4 * day).map { it.name })
        assertEquals("B", OpeningTrainer.nextNew(lines, progress)?.name)
        assertNull(OpeningTrainer.nextNew(lines, progress + ("w:B" to first)))
    }
}
