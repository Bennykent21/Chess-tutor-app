package com.chesstutor.app.domain.puzzle

import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * An in-memory set of puzzles, sorted by rating. Build it with [fromGzipStream] (the bundled
 * `puzzles.tsv.gz` asset) or [fromText] (tests, tooling).
 */
class PuzzlePack(puzzles: List<Puzzle>, val skippedLines: Int = 0) {

    val puzzles: List<Puzzle> = puzzles.sortedWith(compareBy({ it.rating }, { it.id }))

    val size: Int get() = puzzles.size
    val isEmpty: Boolean get() = puzzles.isEmpty()

    private val byId: Map<String, Puzzle> by lazy { this.puzzles.associateBy { it.id } }

    fun get(id: String): Puzzle? = byId[id]

    /** Primary themes present in the pack, most puzzles first. */
    val themes: List<String> by lazy {
        this.puzzles.groupingBy { it.primaryTheme }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
    }

    fun countFor(theme: String): Int = puzzles.count { it.primaryTheme == theme }

    companion object {
        val EMPTY = PuzzlePack(emptyList())

        /** Parses TSV lines; malformed lines are skipped and counted in [skippedLines]. */
        fun parse(lines: Sequence<String>): PuzzlePack {
            val parsed = ArrayList<Puzzle>()
            var skipped = 0
            val seen = HashSet<String>()
            for (line in lines) {
                if (line.isBlank()) continue
                val puzzle = PuzzleParser.parseLine(line)
                if (puzzle == null || !seen.add(puzzle.id)) skipped++ else parsed += puzzle
            }
            return PuzzlePack(parsed, skipped)
        }

        fun fromText(text: String): PuzzlePack = parse(text.lineSequence())

        /** Reads a gzip-compressed TSV pack, as produced by tools/build_puzzle_pack.py. */
        fun fromGzipStream(stream: InputStream): PuzzlePack =
            GZIPInputStream(stream).bufferedReader(Charsets.UTF_8).useLines { parse(it) }

        /** Reads either gzip-compressed or plain text TSV pack by inspecting header magic bytes. */
        fun fromStream(stream: InputStream): PuzzlePack {
            val buffered = if (stream.markSupported()) stream else stream.buffered()
            buffered.mark(4)
            val b1 = buffered.read()
            val b2 = buffered.read()
            buffered.reset()
            return if (b1 == 0x1f && b2 == 0x8b) {
                fromGzipStream(buffered)
            } else {
                buffered.bufferedReader(Charsets.UTF_8).useLines { parse(it) }
            }
        }
    }
}
