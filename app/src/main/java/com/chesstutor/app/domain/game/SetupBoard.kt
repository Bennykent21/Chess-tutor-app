package com.chesstutor.app.domain.game

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.Square

/**
 * The "From a position" editor's board (plan 6.1): pieces placed by hand, the side to move,
 * and the checks that make it playable. Castling rights follow from where kings and rooks
 * stand; there is never an en passant square.
 */
data class SetupBoard(val squares: List<Piece?>, val sideToMove: PieceColor = PieceColor.WHITE) {
    init {
        require(squares.size == 64)
    }

    fun pieceAt(square: Square): Piece? = squares[square.index]

    /** Puts [piece] on [square] (null clears it). A side has one king, so a new king replaces the old one. */
    fun place(square: Square, piece: Piece?): SetupBoard {
        val next = squares.toMutableList()
        if (piece?.type == PieceType.KING) {
            next.indices.filter { next[it] == piece }.forEach { next[it] = null }
        }
        next[square.index] = if (next[square.index] == piece) null else piece
        return copy(squares = next)
    }

    fun withSideToMove(color: PieceColor): SetupBoard = copy(sideToMove = color)

    fun toFen(): String {
        val board = (7 downTo 0).joinToString("/") { rank ->
            buildString {
                var empty = 0
                for (file in 0..7) {
                    val piece = squares[rank * 8 + file]
                    if (piece == null) {
                        empty++
                    } else {
                        if (empty > 0) {
                            append(empty)
                            empty = 0
                        }
                        append(piece.fenChar)
                    }
                }
                if (empty > 0) append(empty)
            }
        }
        return "$board ${if (sideToMove == PieceColor.WHITE) "w" else "b"} ${castling()} - 0 1"
    }

    private fun castling(): String {
        fun at(square: String, type: PieceType, color: PieceColor) =
            squares[Square.fromAlgebraic(square).index] == Piece(type, color)
        val rights = buildString {
            if (at("e1", PieceType.KING, PieceColor.WHITE)) {
                if (at("h1", PieceType.ROOK, PieceColor.WHITE)) append('K')
                if (at("a1", PieceType.ROOK, PieceColor.WHITE)) append('Q')
            }
            if (at("e8", PieceType.KING, PieceColor.BLACK)) {
                if (at("h8", PieceType.ROOK, PieceColor.BLACK)) append('k')
                if (at("a8", PieceType.ROOK, PieceColor.BLACK)) append('q')
            }
        }
        return rights.ifEmpty { "-" }
    }

    /** Why this position cannot be played, in plain words, or null when it can. */
    fun problem(): String? {
        val kings = PieceColor.entries.associateWith { color -> squares.count { it == Piece(PieceType.KING, color) } }
        if (kings[PieceColor.WHITE] != 1 || kings[PieceColor.BLACK] != 1) return "Each side needs exactly one king."
        val backRankPawn = squares.indices.any { i -> squares[i]?.type == PieceType.PAWN && (i / 8 == 0 || i / 8 == 7) }
        if (backRankPawn) return "Pawns cannot stand on the first or last rank."
        val position = runCatching { Position.fromFen(toFen()) }.getOrNull() ?: return "This position is not valid."
        if (LegalMoveGenerator.isKingInCheck(position, sideToMove.opposite())) {
            return "The side not to move is in check. Change who moves first."
        }
        if (LegalMoveGenerator.generateLegalMoves(position).isEmpty()) {
            return if (LegalMoveGenerator.isKingInCheck(position, sideToMove)) "That is already checkmate." else "That is already stalemate."
        }
        return null
    }

    companion object {
        val START: SetupBoard = fromFen(Position.STARTING_FEN)!!

        /** Just the two kings on their home squares. */
        val KINGS_ONLY: SetupBoard = SetupBoard(List(64) { null })
            .place(Square.fromAlgebraic("e1"), Piece(PieceType.KING, PieceColor.WHITE))
            .place(Square.fromAlgebraic("e8"), Piece(PieceType.KING, PieceColor.BLACK))

        /** Reads the board and side to move of a FEN; null if it cannot be read. */
        fun fromFen(fen: String): SetupBoard? = runCatching {
            val parts = fen.trim().split(Regex("\\s+"))
            val rows = parts[0].split('/')
            require(rows.size == 8)
            val squares = MutableList<Piece?>(64) { null }
            rows.forEachIndexed { row, text ->
                var file = 0
                for (c in text) {
                    if (c.isDigit()) file += c.digitToInt() else {
                        squares[(7 - row) * 8 + file] = Piece.fromFenChar(c) ?: error("bad piece $c")
                        file++
                    }
                }
                require(file == 8)
            }
            SetupBoard(squares, if (parts.getOrNull(1) == "b") PieceColor.BLACK else PieceColor.WHITE)
        }.getOrNull()
    }
}
