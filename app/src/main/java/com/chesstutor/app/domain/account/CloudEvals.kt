package com.chesstutor.app.domain.account

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Position
import com.squareup.moshi.Moshi

/**
 * Deep evaluations someone already computed, looked up instead of searched (D86): Lichess keeps
 * them for millions of positions, which covers the opening of most games. Null when the
 * position is not there or the lookup fails; a review never depends on it.
 */
fun interface CloudEvals {
    suspend fun lookup(ply: Int, fen: String): PlyEval?

    companion object {
        private val adapter = Moshi.Builder().build().adapter(Any::class.java)

        /**
         * Lichess's cloud-eval answer for [fen] (scores from White's side, castling written as the
         * king taking its rook) as the app's [PlyEval], or null when it has no usable line.
         */
        fun parseLichess(ply: Int, fen: String, json: String): PlyEval? {
            val root = runCatching { adapter.fromJson(json) as? Map<*, *> }.getOrNull() ?: return null
            val pvs = (root["pvs"] as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()
            val first = pvs.firstOrNull() ?: return null
            val line = appMoves(fen, (first["moves"] as? String).orEmpty().split(' ').filter { it.isNotBlank() })
            val best = line.firstOrNull() ?: return null
            val second = pvs.getOrNull(1)
            return PlyEval(
                ply = ply,
                centipawns = (first["cp"] as? Number)?.toInt(),
                mate = (first["mate"] as? Number)?.toInt(),
                bestMove = best,
                line = line.take(GameAnalyzer.MAX_LINE),
                depth = (root["depth"] as? Number)?.toInt(),
                secondCentipawns = (second?.get("cp") as? Number)?.toInt(),
                secondMate = (second?.get("mate") as? Number)?.toInt(),
            )
        }

        /** The line in the app's own move spelling, cut where a move does not fit the position. */
        private fun appMoves(fen: String, uci: List<String>): List<String> {
            var position = runCatching { Position.fromFen(fen) }.getOrNull() ?: return emptyList()
            val out = mutableListOf<String>()
            for (text in uci) {
                val legal = LegalMoveGenerator.generateLegalMoves(position)
                val move = legal.firstOrNull { it.uci == text }
                    // e1h1 is short castling, e1a1 long: the castling move from that square towards that side.
                    ?: legal.firstOrNull { it.isCastling && it.from.algebraic == text.take(2) && (text[2] > text[0]) == (it.to.algebraic[0] > it.from.algebraic[0]) }
                    ?: break
                out += move.uci
                position = LegalMoveGenerator.makeMove(position, move)
            }
            return out
        }
    }
}
