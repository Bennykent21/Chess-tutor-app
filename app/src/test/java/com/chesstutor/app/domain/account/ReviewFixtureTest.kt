package com.chesstutor.app.domain.account

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Real games the developer found wrongly reviewed (Phase 13B), as Stockfish saw them on the PC at
 * the app's depth (`tools/review_fixture.py`), reviewed by the app's own [GameReviewer].
 */
class ReviewFixtureTest {

    /** A fixture: the moves, and per position the app-depth and deep engine views. */
    class Fixture(val moves: List<String>, val app: List<PlyEval>, val deep: List<PlyEval>)

    companion object {
        private val adapter = Moshi.Builder().build().adapter(Any::class.java)

        fun load(name: String): Fixture {
            val root = adapter.fromJson(File("src/test/resources/reviews/$name.json").readText()) as Map<*, *>
            val moves = (root["moves"] as List<*>).map { it as String }
            val plies = root["plies"] as List<*>
            fun evalsOf(key: String) = plies.mapNotNull { entry ->
                val ply = entry as Map<*, *>
                val n = (ply["ply"] as Number).toInt()
                val e = ply[key] as? Map<*, *> ?: return@mapNotNull null
                PlyEval(
                    ply = n,
                    centipawns = (e["cp"] as? Number)?.toInt(),
                    mate = (e["mate"] as? Number)?.toInt(),
                    bestMove = e["best"] as String,
                    line = (e["line"] as List<*>).map { it as String },
                    depth = (e["depth"] as? Number)?.toInt(),
                    secondCentipawns = (e["secondCp"] as? Number)?.toInt(),
                    secondMate = (e["secondMate"] as? Number)?.toInt(),
                )
            }
            return Fixture(moves, evalsOf("app"), evalsOf("deep"))
        }

        fun review(fixture: Fixture, deep: Boolean = false): GameReview {
            val evals = if (deep) fixture.deep else fixture.app
            // Terminal positions (mate on the board) are scored without the engine, as the analyser does.
            val positions = GameReviewer.positions(Position.STARTING_FEN, fixture.moves)
            val all = evals + positions.indices.filter { i -> evals.none { it.ply == i } }.mapNotNull { GameReviewer.terminalEval(it, positions[it]) }
            return GameReviewer.review(Position.STARTING_FEN, fixture.moves, all.sortedBy { it.ply })
        }
    }

    @Test
    fun decidedPositionsNoLongerHideMatesAndLostQueens() {
        val review = review(load("sokka"))
        fun quality(label: String) = review.moves.single { "${it.numberLabel}${it.san}" == label }.quality
        // -9.8, then a forced mate against: allowing it is a blunder while the game was not hopeless.
        assertEquals(MoveQuality.BLUNDER, quality("25.Qxa6"))
        // Mated in 10 became mated in 4 (Kg1 was the best defence).
        assertEquals(MoveQuality.MISTAKE, quality("26.Ke1"))
        // White's 26.Ke1 gave Black mate in 4, and Bxc4 let it go: a miss.
        assertEquals(MoveQuality.MISS, quality("26...Bxc4"))
        // The queen given away into a forced mate.
        assertEquals(MoveQuality.BLUNDER, quality("27.Qa3"))
        // The engine's own best moves stay best.
        assertEquals(MoveQuality.BEST, quality("27...Qxa3"))
        assertEquals(MoveQuality.BEST, quality("16.Rxd5"))
    }

    @Test
    fun printTheReviewOfTheDevelopersGame() {
        val fixture = load("sokka")
        val review = review(fixture)
        val cps = fixture.app.associateBy { it.ply }
        review.moves.forEach { m ->
            val before = cps[m.ply - 1]
            val after = cps[m.ply]
            println("${m.numberLabel}${m.san}".padEnd(10) + m.quality.label.padEnd(12) +
                "win ${"%.1f".format(m.winBefore)} -> ${"%.1f".format(m.winAfter)}  " +
                "eval ${before?.centipawns ?: "#" + before?.mate} -> ${after?.centipawns ?: "#" + after?.mate}  best ${m.bestSan}")
        }
        println("accuracy White ${review.report(PieceColor.WHITE).accuracy}  Black ${review.report(PieceColor.BLACK).accuracy}")
        val tree = File("src/main/assets/openings.tsv.gz").inputStream().use(com.chesstutor.app.domain.opening.OpeningTree::fromStream)
        val match = tree.match(fixture.moves)
        println("book: left at ply ${match.leftBookAt} (${match.opening?.name})")
        val masters = File("src/main/assets/book.bin").takeIf { it.exists() }?.inputStream()?.use(com.chesstutor.app.domain.opening.OpeningBook::fromStream)
        if (masters != null) println("with the masters' book: left at ply ${tree.withBook(masters).match(fixture.moves).leftBookAt}")
        // How the app's depth compares with a much deeper search, grade by grade.
        val deep = review(fixture, deep = true)
        val differ = review.moves.zip(deep.moves).filter { (a, b) -> a.quality != b.quality }
        differ.forEach { (a, b) -> println("differs: ${a.numberLabel}${a.san} app ${a.quality.label}, deep ${b.quality.label}") }
        println("grades that differ from depth 22: ${differ.size} of ${review.moves.size}")
        println("accuracy at depth 22: White ${deep.report(PieceColor.WHITE).accuracy}  Black ${deep.report(PieceColor.BLACK).accuracy}")
    }
}
