package com.chesstutor.app.engine

data class UciInfoLine(
    val depth: Int? = null,
    val centipawns: Int? = null,
    val mateInMoves: Int? = null,
    val pv: List<String> = emptyList(),
)

object UciProtocol {
    fun parseInfoLine(line: String): UciInfoLine? {
        if (!line.startsWith("info")) return null
        val tokens = line.trim().split(Regex("\\s+"))
        var depth: Int? = null
        var cp: Int? = null
        var mate: Int? = null
        var pv: List<String> = emptyList()

        var i = 0
        while (i < tokens.size) {
            when (tokens[i]) {
                "depth" -> depth = tokens.getOrNull(i + 1)?.toIntOrNull()
                "cp" -> cp = tokens.getOrNull(i + 1)?.toIntOrNull()
                "mate" -> mate = tokens.getOrNull(i + 1)?.toIntOrNull()
                "pv" -> {
                    pv = tokens.drop(i + 1)
                    i = tokens.size
                }
            }
            i++
        }

        if (depth == null && cp == null && mate == null && pv.isEmpty()) return null
        return UciInfoLine(depth, cp, mate, pv)
    }

    fun parseBestMove(line: String): String? {
        if (!line.startsWith("bestmove")) return null
        val move = line.trim().split(Regex("\\s+")).getOrNull(1) ?: return null
        return move.takeUnless { it == "(none)" }
    }
}
