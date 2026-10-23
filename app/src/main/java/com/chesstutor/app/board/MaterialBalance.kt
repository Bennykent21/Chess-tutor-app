package com.chesstutor.app.board

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position

/**
 * Captured pieces and the material lead, as shown beside the players in a chess app: each side
 * lists the opponent's pieces it has taken, and the side ahead shows "+N" in pawns.
 */
data class MaterialBalance(
    /** Black pieces White has taken, cheapest first. */
    val takenByWhite: List<PieceType>,
    /** White pieces Black has taken, cheapest first. */
    val takenByBlack: List<PieceType>,
    /** White's material minus Black's, in pawns (1, 3, 3, 5, 9). */
    val whiteLead: Int,
) {
    fun takenBy(color: PieceColor): List<PieceType> = if (color == PieceColor.WHITE) takenByWhite else takenByBlack

    /** The lead of [color], or null when it is not ahead. */
    fun leadOf(color: PieceColor): Int? {
        val lead = if (color == PieceColor.WHITE) whiteLead else -whiteLead
        return lead.takeIf { it > 0 }
    }

    companion object {
        private val ORDER = listOf(PieceType.PAWN, PieceType.KNIGHT, PieceType.BISHOP, PieceType.ROOK, PieceType.QUEEN)

        fun points(type: PieceType): Int = when (type) {
            PieceType.PAWN -> 1
            PieceType.KNIGHT, PieceType.BISHOP -> 3
            PieceType.ROOK -> 5
            PieceType.QUEEN -> 9
            PieceType.KING -> 0
        }

        /**
         * What changed between [start] and [now]. Works from any starting position. A promoted
         * pawn is not shown as taken: its new piece makes up for the missing pawn.
         */
        fun of(start: Position, now: Position): MaterialBalance {
            fun counts(position: Position, color: PieceColor): Map<PieceType, Int> =
                position.squares.filterNotNull().filter { it.color == color }.groupingBy { it.type }.eachCount()

            fun taken(color: PieceColor): List<PieceType> {
                val before = counts(start, color)
                val after = counts(now, color)
                // Pieces beyond the starting count came from promotions, each using up a pawn.
                val promoted = ORDER.drop(1).sumOf { type -> ((after[type] ?: 0) - (before[type] ?: 0)).coerceAtLeast(0) }
                return ORDER.flatMap { type ->
                    val missing = (before[type] ?: 0) - (after[type] ?: 0) - if (type == PieceType.PAWN) promoted else 0
                    List(missing.coerceAtLeast(0)) { type }
                }
            }

            fun material(color: PieceColor) = now.squares.filterNotNull().filter { it.color == color }.sumOf { points(it.type) }
            return MaterialBalance(
                takenByWhite = taken(PieceColor.BLACK),
                takenByBlack = taken(PieceColor.WHITE),
                whiteLead = material(PieceColor.WHITE) - material(PieceColor.BLACK),
            )
        }
    }
}
