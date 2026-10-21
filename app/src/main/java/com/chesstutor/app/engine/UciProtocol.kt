package com.chesstutor.app.engine

/**
 * One parsed UCI `info` line.
 *
 * Scores are exactly as the engine printed them, which for Stockfish means relative to the
 * side to move. [multiPv] is 1 unless the engine runs with MultiPV > 1.
 */
data class UciInfoLine(
    val depth: Int? = null,
    val centipawns: Int? = null,
    val mateInMoves: Int? = null,
    val pv: List<String> = emptyList(),
    val multiPv: Int = 1,
    val nodes: Long? = null,
    val nps: Long? = null,
    val timeMs: Long? = null,
    /** True when the score is only a lower or upper bound (an unfinished aspiration search). */
    val isBound: Boolean = false,
)

/** The engine's final answer: a move, or none when the side to move has no legal moves. */
data class UciBestMove(val moveUci: String?)

object UciProtocol {
    private val whitespace = Regex("\\s+")

    fun parseInfoLine(line: String): UciInfoLine? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("info")) return null
        // "info string ..." carries free text, never search numbers.
        if (trimmed.startsWith("info string")) return null
        val tokens = trimmed.split(whitespace)
        var depth: Int? = null
        var cp: Int? = null
        var mate: Int? = null
        var pv: List<String> = emptyList()
        var multiPv = 1
        var nodes: Long? = null
        var nps: Long? = null
        var timeMs: Long? = null
        var bound = false

        var i = 1
        while (i < tokens.size) {
            when (tokens[i]) {
                "depth" -> depth = tokens.getOrNull(i + 1)?.toIntOrNull()
                "cp" -> cp = tokens.getOrNull(i + 1)?.toIntOrNull()
                "mate" -> mate = tokens.getOrNull(i + 1)?.toIntOrNull()
                "multipv" -> multiPv = tokens.getOrNull(i + 1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                "nodes" -> nodes = tokens.getOrNull(i + 1)?.toLongOrNull()
                "nps" -> nps = tokens.getOrNull(i + 1)?.toLongOrNull()
                "time" -> timeMs = tokens.getOrNull(i + 1)?.toLongOrNull()
                "lowerbound", "upperbound" -> bound = true
                "pv" -> {
                    pv = tokens.drop(i + 1)
                    i = tokens.size
                }
            }
            i++
        }

        if (depth == null && cp == null && mate == null && pv.isEmpty() && nps == null && nodes == null) {
            return null
        }
        return UciInfoLine(depth, cp, mate, pv, multiPv, nodes, nps, timeMs, bound)
    }

    /** Text after `info string `, or null when [line] is not an info string. */
    fun parseInfoString(line: String): String? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("info string")) return null
        return trimmed.removePrefix("info string").trim()
    }

    /**
     * Parses a `bestmove` line. Returns null when [line] is not a bestmove line, and a
     * [UciBestMove] with a null move for `bestmove (none)` (checkmate or stalemate).
     */
    fun parseBestMoveLine(line: String): UciBestMove? {
        val tokens = line.trim().split(whitespace)
        if (tokens.firstOrNull() != "bestmove") return null
        val move = tokens.getOrNull(1)?.takeUnless { it == "(none)" || it == "0000" || it.isBlank() }
        return UciBestMove(move)
    }

    fun parseBestMove(line: String): String? {
        if (!line.startsWith("bestmove")) return null
        val move = line.trim().split(whitespace).getOrNull(1) ?: return null
        return move.takeUnless { it == "(none)" }
    }
}
