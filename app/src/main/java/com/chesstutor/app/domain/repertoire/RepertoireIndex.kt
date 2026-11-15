package com.chesstutor.app.domain.repertoire

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.repetitionKey

/**
 * How to reach each position of a [RepertoireBook] from the start: the likeliest move order
 * (where lines transpose, the one through the more common replies), so a card can show the
 * moves that led to it and name the opponent's last move ("what do you play against
 * 3...Nf6?"). Built once per book.
 */
class RepertoireIndex(val book: RepertoireBook) {
    private val paths: Map<String, List<String>>

    init {
        val found = HashMap<String, List<String>>()
        var level = listOf(Position.initial() to emptyList<String>())
        while (level.isNotEmpty()) {
            val next = ArrayList<Triple<Position, List<String>, Double>>()
            for ((position, path) in level) {
                val key = position.repetitionKey()
                if (key in found) continue
                val node = book.nodes[key] ?: continue
                found[key] = path
                val moves = if (node.own) listOfNotNull(node.move) else node.replies.map { it.uci }
                for (uci in moves) {
                    val child = play(position, uci) ?: continue
                    next += Triple(child, path + uci, book.nodes[child.repetitionKey()]?.reach ?: 0.0)
                }
            }
            // The likeliest way in comes first, so it wins where move orders meet.
            level = next.sortedByDescending { it.third }.map { it.first to it.second }
        }
        paths = found
    }

    /** UCI moves from the start to [key], or null when the tree does not reach it. */
    fun pathTo(key: String): List<String>? = paths[key]

    fun positionOf(key: String): Position = Position.tryFromFen("$key 0 1").getOrThrow()

    /** The move that led to [key] (for a card, the opponent's reply). */
    fun lastMove(key: String): String? = paths[key]?.lastOrNull()

    /** The node after [uci] from [key], if the tree has one. */
    fun child(key: String, uci: String): RepertoireNode? =
        play(positionOf(key), uci)?.let { book.at(it) }

    companion object {
        fun play(position: Position, uci: String): Position? =
            LegalMoveGenerator.generateLegalMoves(position).firstOrNull { it.uci == uci }
                ?.let { LegalMoveGenerator.makeMove(position, it) }
    }
}
