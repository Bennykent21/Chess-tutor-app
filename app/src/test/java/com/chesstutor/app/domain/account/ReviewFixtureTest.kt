package com.chesstutor.app.domain.account

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun theChessComGameIsGradedCloseToChessCom() {
        val review = review(load("chesscom"))
        fun quality(label: String) = review.moves.single { "${it.numberLabel}${it.san}" == label }.quality
        // A winning game thrown back to level is a mistake, as chess.com says; a lost bishop is a blunder.
        assertEquals(MoveQuality.MISTAKE, quality("18.Bxc4"))
        assertEquals(MoveQuality.MISTAKE, quality("42.Qb4"))
        assertEquals(MoveQuality.BLUNDER, quality("24.Ba4"))
        // Taking a checking knight while a forked rook falls is no sacrifice; neither is moving an attacked rook.
        assertEquals(MoveQuality.BEST, quality("33.gxf3"))
        assertEquals(MoveQuality.BEST, quality("19.Rfe1"))
        assertEquals("chess.com counted one blunder for each side", listOf(1, 1), PieceColor.entries.map { side -> review.moves.count { it.mover == side && it.quality == MoveQuality.BLUNDER } })
        // Chess.com: White 71.6, Black 76.35. One lost piece no longer sinks Black below White.
        assertTrue(review.report(PieceColor.BLACK).accuracy!! > review.report(PieceColor.WHITE).accuracy!!)
    }

    /**
     * The developer's chess.com game (14 Nov, reviewed there and here on 16 Nov), with chess.com's own grades of the
     * developer's moves read from their screen recording: the app at depth 16 and at depth 22 next
     * to chess.com, and the accuracy computed several ways (chess.com said White 71.6, Black 76.35).
     */
    @Test
    fun printTheChessComGameAgainstItsReview() {
        if (!File("src/test/resources/reviews/chesscom.json").exists()) return
        val chessCom = mapOf(
            "5.Nd2" to "Book", "9.Qg3" to "Mistake", "14.Qh2" to "Great", "16.f3" to "Miss", "18.Bxc4" to "Mistake",
            "23.d5" to "Mistake", "24.Ba4" to "Blunder", "26.c4" to "Mistake", "27.Re4" to "Mistake", "31.Qc4+" to "Miss",
            "33.gxf3" to "Best", "34.Qb4" to "Mistake", "42.Qb4" to "Mistake", "46.c7" to "Mistake", "60.Kxh4" to "Excellent",
        )
        val fixture = load("chesscom")
        val app = review(fixture)
        val deep = review(fixture, deep = true)
        app.moves.zip(deep.moves).forEach { (a, d) ->
            val label = "${a.numberLabel}${a.san}"
            val theirs = chessCom[label]
            if (a.quality != d.quality || theirs != null || a.quality.isError || a.quality == MoveQuality.BRILLIANT) {
                println(label.padEnd(10) + "app ${a.quality.label.padEnd(11)} deep ${d.quality.label.padEnd(11)} chess.com ${(theirs ?: "-").padEnd(10)}" +
                    "win ${"%.1f".format(d.winBefore)} -> ${"%.1f".format(d.winAfter)}  best ${d.bestSan}")
            }
        }
        for (r in listOf(app, deep)) {
            for (side in PieceColor.entries) {
                val moves = r.moves.filter { it.mover == side && it.quality != MoveQuality.BOOK }
                val mean = moves.map { it.accuracy }.average()
                val harmonic = moves.size / moves.sumOf { 1.0 / it.accuracy.coerceAtLeast(1.0) }
                val counts = MoveQuality.entries.associateWith { q -> r.moves.count { it.mover == side && it.quality == q } }.filterValues { it > 0 }
                val wins = listOf(50.0) + r.moves.map { if (it.mover == PieceColor.WHITE) it.winAfter else 100 - it.winAfter }
                val n = wins.size - 1
                val w = (n / 10).coerceIn(2, 8)
                val windows = List((w - 2).coerceAtLeast(0)) { wins.take(w) } + wins.windowed(w)
                val weights = windows.map { win -> val m = win.average(); kotlin.math.sqrt(win.sumOf { (it - m) * (it - m) } / win.size).coerceIn(0.5, 12.0) }
                val mine = r.moves.withIndex().filter { it.value.mover == side }
                val weightedOnly = mine.sumOf { it.value.accuracy * weights.getOrElse(it.index) { weights.last() } } / mine.sumOf { weights.getOrElse(it.index) { weights.last() } }
                println("  weighted mean only ${"%.1f".format(weightedOnly)}")
                println("${if (r === app) "app " else "deep"} $side accuracy ${r.report(side).accuracy?.let { "%.1f".format(it) }} (plain mean ${"%.1f".format(mean)}, harmonic ${"%.1f".format(harmonic)}) $counts")
            }
        }
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
