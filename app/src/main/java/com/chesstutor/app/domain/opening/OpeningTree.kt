package com.chesstutor.app.domain.opening

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.repetitionKey
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * One named line of the opening database (lichess-org/chess-openings, CC0), for example
 * "Sicilian Defense: Najdorf Variation, English Attack" with its moves from the start.
 */
data class Opening(val eco: String, val name: String, val moves: List<String>) {
    /** "Sicilian Defense". */
    val family: String get() = name.substringBefore(':').trim()

    /** "Najdorf Variation, English Attack", or "" for a family's own line. */
    val variation: String get() = name.substringAfter(':', "").trim()

    /**
     * True for this opening itself and every line named under it, including names that only
     * add a word ("King's Gambit" covers "King's Gambit Accepted").
     */
    fun isWithin(selection: String): Boolean =
        name == selection || name.startsWith("$selection:") || name.startsWith("$selection,") || name.startsWith("$selection ")
}

/** A move the book plays from a position; [weight] is how many named lines go through it. */
data class BookMove(val uci: String, val weight: Int)

/** Where a game stands against the book. */
data class OpeningMatch(
    /** The deepest named opening the game reached, or null before the first named position. */
    val opening: Opening?,
    /** How many of the game's moves were book moves. */
    val bookPlies: Int,
    /** The ply (0-based) of the first move that left the book, or null while still in it. */
    val leftBookAt: Int?,
)

/**
 * The opening database as a tree of positions. Lines that reach the same position by a
 * different move order (transpositions) meet at the same node, so book moves and names
 * follow the position, not the move order. Positions are keyed by [repetitionKey].
 *
 * Built once from the bundled `openings.tsv.gz` (tools/build_openings.py); every line is
 * replayed with the app's own move generator.
 */
class OpeningTree private constructor(
    val openings: List<Opening>,
    private val edges: Map<String, Map<String, Int>>,
    private val named: Map<String, Opening>,
    val skippedLines: Int,
) {
    val isEmpty: Boolean get() = openings.isEmpty()

    /** Every position the book knows, including the start. */
    fun isInBook(key: String): Boolean = key in edges || key in named

    /** Book moves from [key], most travelled first. */
    fun bookMoves(key: String): List<BookMove> = sortedMoves(edges[key].orEmpty())

    /** The named opening whose line ends exactly at [key]. */
    fun openingAt(key: String): Opening? = named[key]

    /** Names a game given as UCI moves from the starting position. */
    fun match(moves: List<String>): OpeningMatch {
        var position = Position.initial()
        var opening: Opening? = null
        moves.forEachIndexed { ply, uci ->
            position = play(position, uci) ?: return OpeningMatch(opening, ply, ply)
            // Judged by position, so a book position reached by an unusual move order still counts.
            val key = position.repetitionKey()
            if (!isInBook(key)) return OpeningMatch(opening, ply, ply)
            named[key]?.let { opening = it }
        }
        return OpeningMatch(opening, moves.size, null)
    }

    /** Families with their lines, largest family first. */
    val families: List<OpeningFamily> by lazy {
        openings.groupBy { it.family }
            .map { (name, lines) -> OpeningFamily(name, lines.sortedWith(compareBy({ it.moves.size }, { it.name }))) }
            .sortedWith(compareByDescending<OpeningFamily> { it.lines.size }.thenBy { it.name })
    }

    /** The lines inside [selections] (see [Opening.isWithin]). */
    fun linesWithin(selections: Collection<String>): List<Opening> =
        openings.filter { line -> selections.any(line::isWithin) }

    companion object {
        val EMPTY = OpeningTree(emptyList(), emptyMap(), emptyMap(), 0)

        fun build(openings: List<Opening>, skipped: Int = 0): OpeningTree {
            val (edges, named, bad) = edgesOf(openings)
            val playable = if (bad.isEmpty()) openings else openings.filterNot { it in bad }
            return OpeningTree(playable, edges, named, skipped + bad.size)
        }

        /** Parses `eco <TAB> name <TAB> uci` lines; malformed or illegal lines are skipped and counted. */
        fun parse(lines: Sequence<String>): OpeningTree {
            val openings = ArrayList<Opening>()
            var skipped = 0
            for (line in lines) {
                if (line.isBlank()) continue
                val cols = line.split('\t')
                val moves = cols.getOrNull(2)?.trim()?.split(' ')?.filter { it.isNotEmpty() }.orEmpty()
                if (cols.size < 3 || cols[1].isBlank() || moves.isEmpty()) skipped++
                else openings += Opening(cols[0].trim(), cols[1].trim(), moves)
            }
            return build(openings, skipped)
        }

        fun fromGzipStream(stream: InputStream): OpeningTree =
            GZIPInputStream(stream).bufferedReader(Charsets.UTF_8).useLines { parse(it) }

        /** Reads gzip'd or plain text (asset packaging may unpack the file), by its first bytes. */
        fun fromStream(stream: InputStream): OpeningTree {
            val buffered = if (stream.markSupported()) stream else stream.buffered()
            buffered.mark(2)
            val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b
            buffered.reset()
            return if (gzip) fromGzipStream(buffered) else buffered.bufferedReader(Charsets.UTF_8).useLines { parse(it) }
        }

        /** The side whose move defines a line, for example White for the Italian, Black for the Sicilian. */
        fun chooserOf(line: Opening): PieceColor = if (line.moves.size % 2 == 1) PieceColor.WHITE else PieceColor.BLACK

        /** Book edges (position key to move to weight) of [lines] alone. */
        internal fun edgesFor(lines: List<Opening>): Map<String, Map<String, Int>> = edgesOf(lines).first

        internal fun sortedMoves(moves: Map<String, Int>): List<BookMove> =
            moves.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { BookMove(it.key, it.value) }

        /** Replays [lines]: edges with weights, the named end positions, and lines that do not replay. */
        private fun edgesOf(lines: List<Opening>): Triple<Map<String, Map<String, Int>>, Map<String, Opening>, Set<Opening>> {
            // Lines share long prefixes, so each position is generated once and cached.
            val cache = HashMap<String, Pair<Position, String>>()
            val edges = HashMap<String, HashMap<String, Int>>()
            val named = HashMap<String, Opening>()
            val bad = HashSet<Opening>()
            val start = Position.initial()
            for (line in lines) {
                var position = start
                var key = start.repetitionKey()
                var path = ""
                val seenEdges = ArrayList<Pair<String, String>>(line.moves.size)
                var ok = true
                for (uci in line.moves) {
                    path = if (path.isEmpty()) uci else "$path $uci"
                    val next = cache[path] ?: play(position, uci)?.let { it to it.repetitionKey() }?.also { cache[path] = it }
                    if (next == null) { ok = false; break }
                    seenEdges += key to uci
                    position = next.first
                    key = next.second
                }
                if (!ok) { bad += line; continue }
                // A line counts once per edge even if it passes the same position twice.
                seenEdges.distinct().forEach { (from, uci) ->
                    val moves = edges.getOrPut(from) { HashMap() }
                    moves[uci] = (moves[uci] ?: 0) + 1
                }
                named.putIfAbsent(key, line)
            }
            return Triple(edges, named, bad)
        }

        private fun play(position: Position, uci: String): Position? =
            LegalMoveGenerator.generateLegalMoves(position).firstOrNull { it.uci == uci }
                ?.let { LegalMoveGenerator.makeMove(position, it) }
    }
}

/** An opening family ("Sicilian Defense") and all its named lines, shortest first. */
data class OpeningFamily(val name: String, val lines: List<Opening>) {
    /** The family's defining line: its own entry, or else its shortest line. */
    val root: Opening get() = lines.firstOrNull { it.variation.isEmpty() } ?: lines.first()
}
