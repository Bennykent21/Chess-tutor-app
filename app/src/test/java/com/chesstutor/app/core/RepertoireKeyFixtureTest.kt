package com.chesstutor.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repertoire assets are keyed by position in tools/build_repertoire.py (python-chess);
 * the app looks them up with [repetitionKey]. Both must write every position the same way,
 * so the fixture the script writes (`build_repertoire.py keys`) is replayed here.
 */
class RepertoireKeyFixtureTest {

    private data class Case(val fen: String, val moves: List<String>, val key: String, val why: String)

    private val cases: List<Case> by lazy {
        javaClass.classLoader!!.getResource("repertoire/keys.tsv")!!.readText()
            .lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split('\t') }
            .map { Case(it[0], it[1].split(' ').filter(String::isNotEmpty), it[2], it[3]) }
            .toList()
    }

    @Test
    fun theFixtureCoversTheHardCases() {
        assertTrue(cases.size >= 40)
        assertTrue(cases.any { it.key.endsWith(" f6") })
        assertTrue(cases.any { it.why.startsWith("en passant would expose the king") })
        assertTrue(cases.any { it.why.startsWith("rook taken on its square") })
    }

    @Test
    fun everyPositionHasTheSameKeyInTheAppAndTheScript() {
        for (case in cases) {
            var position = Position.tryFromFen(case.fen).getOrThrow()
            for (uci in case.moves) {
                val move = LegalMoveGenerator.generateLegalMoves(position).firstOrNull { it.uci == uci }
                    ?: error("${case.why}: $uci is not legal")
                position = LegalMoveGenerator.makeMove(position, move)
            }
            assertEquals(case.why, case.key, position.repetitionKey())
        }
    }
}
