package com.chesstutor.app.data.network

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.SanReplay

/** A game's moves as UCI, how many there were and where they ended. [incomplete] when a move could not be read. */
data class SanParseOutcome(
    val uciMoves: String,
    val finalFen: String,
    val moveCount: Int,
    val incomplete: Boolean
)

/** Reads the moves out of a PGN or a SAN move list. */
object PgnMoves {
    /**
     * Strips PGN headers, `{...}` block comments, `;...` line comments, and nested `(...)` RAVs.
     */
    fun movesText(pgn: String): String {
        val withoutHeadersAndLineComments = pgn.lineSequence()
            .filter { !it.trim().startsWith("[") && !it.trim().startsWith("%") }
            .map { line -> line.substringBefore(";") }
            .joinToString(" ")

        // Strip {...} comments
        val withoutBraces = withoutBracesComments(withoutHeadersAndLineComments)
        // Strip nested (...) variations
        return withoutNestedVariations(withoutBraces)
    }

    private fun withoutBracesComments(input: String): String {
        val sb = StringBuilder(input.length)
        var depth = 0
        for (ch in input) {
            when (ch) {
                '{' -> depth++
                '}' -> if (depth > 0) depth--
                else -> if (depth == 0) sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun withoutNestedVariations(input: String): String {
        val sb = StringBuilder(input.length)
        var depth = 0
        for (ch in input) {
            when (ch) {
                '(' -> depth++
                ')' -> if (depth > 0) depth--
                else -> if (depth == 0) sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Tokenizes a SAN move stream (handling NAGs `$1`, move numbers `1.` / `1...`,
     * annotations `!`/`?`, and `0-0`/`0-0-0` castling), converts to UCI, and flags
     * `incomplete = true` if any token fails to match before the game result token. [startFen]
     * is the position the game was set up from, or null for the normal start.
     */
    fun toUci(sanStream: String, startFen: String? = null): SanParseOutcome {
        var position = startFen?.let { fen -> Position.tryFromFen(fen).getOrNull() } ?: Position.initial()
        val uciList = mutableListOf<String>()
        var incomplete = false

        val normalized = withoutNestedVariations(withoutBracesComments(sanStream))
            .replace(NAG, " ")
            .replace(MOVE_NUMBER, " ")
            .trim()

        for (rawToken in normalized.split(WHITESPACE)) {
            if (rawToken.isBlank()) continue
            if (rawToken == "1-0" || rawToken == "0-1" || rawToken == "1/2-1/2" || rawToken == "*") break
            // Skip standalone move number fragments or NAGs
            if (rawToken.startsWith("$") || rawToken.all { it.isDigit() || it == '.' }) continue

            val legal = LegalMoveGenerator.generateLegalMoves(position)
            val move = SanReplay.moveFor(position, legal, rawToken)
            if (move == null) {
                incomplete = true
                break
            }
            position = LegalMoveGenerator.makeMove(position, move)
            uciList += move.uci
        }

        return SanParseOutcome(
            uciMoves = uciList.joinToString(" "),
            finalFen = position.toFen(),
            moveCount = (uciList.size + 1) / 2,
            incomplete = incomplete
        )
    }

        private val NAG = Regex("\\$\\d+")
        private val MOVE_NUMBER = Regex("\\d+\\.+")
        private val WHITESPACE = Regex("\\s+")
}
