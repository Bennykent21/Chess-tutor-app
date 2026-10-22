package com.chesstutor.app.domain.opening

import com.chesstutor.app.core.PieceColor

/**
 * The repertoire tree (plan 6.1): the book moves a bot may play, as positions mapped to
 * allowed moves with weights. It is the opening tree cut down to the openings picked for
 * the bot, one set for when it plays White and one for Black. Repertoires are not stored
 * as a tree: a bot keeps only its picked opening names and this is rebuilt from them.
 */
class Repertoire private constructor(
    private val white: Map<String, Map<String, Int>>,
    private val black: Map<String, Map<String, Int>>,
) {
    /** Book moves for a bot playing [side] in the position [key], most travelled first. */
    fun movesFor(side: PieceColor, key: String): List<BookMove> =
        OpeningTree.sortedMoves((if (side == PieceColor.WHITE) white else black)[key].orEmpty())

    fun covers(side: PieceColor): Boolean = (if (side == PieceColor.WHITE) white else black).isNotEmpty()

    companion object {
        /** No book: the bot thinks for itself from the first move. */
        val NONE = Repertoire(emptyMap(), emptyMap())

        /**
         * The lines of [tree] named by [whiteOpenings] and [blackOpenings]. A name covers its
         * own line and every line named under it ("Sicilian Defense" covers the Najdorf).
         */
        fun of(tree: OpeningTree, whiteOpenings: Collection<String>, blackOpenings: Collection<String>): Repertoire =
            Repertoire(
                white = OpeningTree.edgesFor(tree.linesWithin(whiteOpenings)),
                black = OpeningTree.edgesFor(tree.linesWithin(blackOpenings)),
            )

        /** Exactly one line, for either side (Opening Battle's "the bot plays this line"). */
        fun line(moves: List<String>): Repertoire {
            val edges = OpeningTree.edgesFor(listOf(Opening("", "line", moves)))
            return Repertoire(edges, edges)
        }
    }
}
