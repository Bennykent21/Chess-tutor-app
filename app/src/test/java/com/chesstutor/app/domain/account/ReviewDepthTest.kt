package com.chesstutor.app.domain.account

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.domain.opening.OpeningTree
import com.squareup.moshi.Moshi
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Which depth reviews games well enough (Phase 13B): real amateur games searched by
 * `tools/review_depths.py` at several depths and a much deeper reference, graded by the app's own
 * [GameReviewer]. Prints, per depth, how many grades differ from the reference's, how many of those
 * flip between a sound move and an error, how far the accuracy is, and the search time.
 */
class ReviewDepthTest {

    private val adapter = Moshi.Builder().build().adapter(Any::class.java)
    private val tree by lazy { File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream) }

    private fun evalsAt(plies: List<*>, depth: String): List<PlyEval>? = plies.map { entry ->
        val ply = entry as Map<*, *>
        val n = (ply["ply"] as Number).toInt()
        val e = ply[depth] as? Map<*, *> ?: return@map null
        PlyEval(
            ply = n,
            centipawns = (e["cp"] as? Number)?.toInt(),
            mate = (e["mate"] as? Number)?.toInt(),
            bestMove = e["best"] as String,
            secondCentipawns = (e["secondCp"] as? Number)?.toInt(),
            secondMate = (e["secondMate"] as? Number)?.toInt(),
        )
    }.filterNotNull()

    /** Sound, inaccuracy, mistake (or miss), blunder: a different bucket is a grade worth noticing. */
    private fun bucket(quality: MoveQuality) = when (quality) {
        MoveQuality.INACCURACY -> 1
        MoveQuality.MISTAKE, MoveQuality.MISS -> 2
        MoveQuality.BLUNDER -> 3
        else -> 0
    }

    @Test
    fun printHowEachDepthComparesWithTheReference() {
        val files = File("src/test/resources/reviews/depths").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        if (files.isEmpty()) return
        data class Tally(var moves: Int = 0, var differ: Int = 0, var flips: Int = 0, var accuracyGap: Double = 0.0, var sides: Int = 0, var ms: Long = 0, var searches: Int = 0)
        val tallies = sortedMapOf<Int, Tally>()
        for (file in files) {
            val root = adapter.fromJson(file.readText()) as Map<*, *>
            val moves = (root["moves"] as List<*>).map { it as String }
            val plies = root["plies"] as List<*>
            val reference = (root["reference"] as Number).toInt()
            val depths = (root["depths"] as List<*>).map { (it as Number).toInt() }
            val positions = GameReviewer.positions(Position.STARTING_FEN, moves)
            val terminal = positions.indices.mapNotNull { GameReviewer.terminalEval(it, positions[it]) }
            val book = tree.match(moves).leftBookAt ?: moves.size
            fun reviewAt(depth: Int) = GameReviewer.review(Position.STARTING_FEN, moves, (evalsAt(plies, "$depth")!! + terminal).sortedBy { it.ply }, book)
            val truth = reviewAt(reference)
            for (depth in depths) {
                val review = reviewAt(depth)
                val tally = tallies.getOrPut(depth) { Tally() }
                review.moves.zip(truth.moves).forEach { (a, b) ->
                    tally.moves++
                    if (a.quality != b.quality) tally.differ++
                    if (bucket(a.quality) != bucket(b.quality)) tally.flips++
                }
                for (side in PieceColor.entries) {
                    val mine = review.report(side).accuracy
                    val real = truth.report(side).accuracy
                    if (mine != null && real != null) {
                        tally.accuracyGap += abs(mine - real)
                        tally.sides++
                    }
                }
                plies.forEach { entry -> ((entry as Map<*, *>)["$depth"] as? Map<*, *>)?.let { tally.ms += (it["ms"] as Number).toLong(); tally.searches++ } }
            }
        }
        println("${files.size} games")
        tallies.forEach { (depth, t) ->
            printTally("depth $depth",t.differ, t.flips, t.moves, t.accuracyGap / t.sides, t.ms / t.searches.coerceAtLeast(1))
        }
    }

    private fun printTally(name: String, differ: Int, flips: Int, moves: Int, accuracyGap: Double, ms: Long) = println(
        "$name: grades differ $differ/$moves (${100 * differ / moves}%), " +
            "sound/error flips $flips (${"%.1f".format(100.0 * flips / moves)}%), " +
            "accuracy off by ${"%.1f".format(accuracyGap)} on average, $ms ms a position",
    )

    /**
     * A cheap search everywhere, then a deeper one only around the moves the cheap review suspects,
     * repeated until no new move is suspected: how close that comes to the reference, and its cost.
     */
    @Test
    fun printHowARecheckOfSuspectMovesCompares() {
        val files = File("src/test/resources/reviews/depths").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        if (files.isEmpty()) return
        val suspects: List<Pair<String, (ReviewedMove) -> Boolean>> = listOf(
            "errors" to { m -> m.quality.isError },
            "below excellent" to { m -> m.quality.isError || m.quality == MoveQuality.GOOD },
            "not best" to { m -> m.quality.isError || m.quality == MoveQuality.GOOD || m.quality == MoveQuality.EXCELLENT },
        )
        for ((cheap, deep) in listOf(14 to 18, 14 to 20, 16 to 18)) {
            for ((name, suspect) in suspects) {
                var moves = 0; var differ = 0; var flips = 0; var gap = 0.0; var sides = 0; var ms = 0L; var positions = 0
                for (file in files) {
                    val root = adapter.fromJson(file.readText()) as Map<*, *>
                    val gameMoves = (root["moves"] as List<*>).map { it as String }
                    val plies = root["plies"] as List<*>
                    val reference = (root["reference"] as Number).toInt()
                    val all = GameReviewer.positions(Position.STARTING_FEN, gameMoves)
                    val terminal = all.indices.mapNotNull { GameReviewer.terminalEval(it, all[it]) }
                    val book = tree.match(gameMoves).leftBookAt ?: gameMoves.size
                    val cheapEvals = evalsAt(plies, "$cheap")!!.associateBy { it.ply }
                    val deepEvals = evalsAt(plies, "$deep")!!.associateBy { it.ply }
                    fun reviewOf(evals: Collection<PlyEval>) = GameReviewer.review(Position.STARTING_FEN, gameMoves, (evals + terminal).sortedBy { it.ply }, book)
                    val rechecked = HashSet<Int>()
                    var review = reviewOf(cheapEvals.values)
                    while (true) {
                        val more = review.moves.filter(suspect).flatMap { listOf(it.ply - 1, it.ply) }.filter { it in deepEvals && it !in rechecked }
                        if (more.isEmpty()) break
                        rechecked += more
                        review = reviewOf(cheapEvals.keys.map { if (it in rechecked) deepEvals.getValue(it) else cheapEvals.getValue(it) })
                    }
                    val truth = reviewOf(evalsAt(plies, "$reference")!!)
                    review.moves.zip(truth.moves).forEach { (a, b) ->
                        moves++
                        if (a.quality != b.quality) differ++
                        if (bucket(a.quality) != bucket(b.quality)) flips++
                    }
                    for (side in PieceColor.entries) {
                        val mine = review.report(side).accuracy
                        val real = truth.report(side).accuracy
                        if (mine != null && real != null) { gap += abs(mine - real); sides++ }
                    }
                    plies.forEach { entry ->
                        val ply = entry as Map<*, *>
                        val n = (ply["ply"] as Number).toInt()
                        (ply["$cheap"] as? Map<*, *>)?.let { ms += (it["ms"] as Number).toLong(); positions++ }
                        if (n in rechecked) (ply["$deep"] as? Map<*, *>)?.let { ms += (it["ms"] as Number).toLong() }
                    }
                }
                printTally("$cheap then $deep on $name", differ, flips, moves, gap / sides, ms / positions.coerceAtLeast(1))
            }
        }
    }

}
