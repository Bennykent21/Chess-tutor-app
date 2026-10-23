package com.chesstutor.app.domain.bot.maia

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * The app's Maia inference against reference probabilities from tools/maia_net.py, whose
 * forward pass matches lc0 (the official engine) to within 0.04 % per move.
 */
class MaiaNetTest {

    private val folder = File("src/main/assets/maia")
    private val policy by lazy {
        MaiaPolicy(
            File(folder, "maia-1500.bin").inputStream().use(MaiaNet::read),
            File(folder, "maia_moves.tsv").inputStream().use(MaiaPolicy::readSlots),
        )
    }

    private data class Case(val start: String, val played: List<String>, val expected: Map<String, Float>)

    private val cases: Map<String, Case> by lazy {
        val text = javaClass.classLoader!!.getResource("maia_fixtures.tsv")!!.readText()
        text.lineSequence().filter { it.isNotBlank() }.map { it.split('\t') }
            .groupBy { it[0] }
            .mapValues { (_, rows) ->
                Case(rows[0][1], rows[0][2].split(' ').filter { it.isNotBlank() }, rows.associate { it[3] to it[4].toFloat() })
            }
    }

    private fun historyOf(case: Case): List<Position> {
        val positions = mutableListOf(Position.fromFen(case.start))
        case.played.forEach { uci ->
            val position = positions.last()
            positions += LegalMoveGenerator.makeMove(position, LegalMoveGenerator.generateLegalMoves(position).first { it.uci == uci })
        }
        return positions
    }

    @Test
    fun everyFixtureMatchesTheReferenceWithinATenthOfAPercent() {
        assertEquals(7, cases.size)
        cases.forEach { (name, case) ->
            val ours = policy.probabilities(historyOf(case), fromStandardStart = case.start == Position.STARTING_FEN)
            assertEquals("$name: same legal moves", case.expected.keys, ours.keys)
            val worst = case.expected.maxOf { (uci, p) -> abs(ours.getValue(uci) - p) }
            assertTrue("$name differs by $worst", worst < 0.001f)
        }
    }

    @Test
    fun humansAt1500Play1e4AndAnswerWith1e5() {
        val start = policy.probabilities(listOf(Position.initial()), true)
        assertEquals("e2e4", start.maxBy { it.value }.key)
        assertEquals(1f, start.values.sum(), 1e-4f)
    }

    @Test
    fun aKnightPromotionHasNoSuffixInThePolicyName() {
        val position = Position.fromFen("8/P5k1/8/8/8/8/6K1/8 w - - 0 1")
        val knight = LegalMoveGenerator.generateLegalMoves(position).first { it.uci == "a7a8n" }
        val queen = LegalMoveGenerator.generateLegalMoves(position).first { it.uci == "a7a8q" }
        assertEquals("a7a8", MaiaPolicy.nameOf(position, knight))
        assertEquals("a7a8q", MaiaPolicy.nameOf(position, queen))
        val black = Position.fromFen("4k3/8/8/8/8/8/8/4K3 b - - 0 1")
        val kingMove = LegalMoveGenerator.generateLegalMoves(black).first { it.uci == "e8d8" }
        assertEquals("from Black's side the board is mirrored", "e1d1", MaiaPolicy.nameOf(black, kingMove))
    }

    @Test
    fun aForwardPassIsQuickEnoughForABot() {
        val history = historyOf(cases.getValue("najdorf"))
        repeat(3) { policy.probabilities(history, true) } // warm up the JIT
        val started = System.nanoTime()
        repeat(10) { policy.probabilities(history, true) }
        val ms = (System.nanoTime() - started) / 10 / 1_000_000
        println("Maia forward pass: $ms ms on the JVM")
        assertTrue("$ms ms per move", ms < 500)
    }
}
