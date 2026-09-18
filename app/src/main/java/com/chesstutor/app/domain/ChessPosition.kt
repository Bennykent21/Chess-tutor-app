package com.chesstutor.app.domain

import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.SanFormatter
import com.example.chess.core.PieceColor
import com.example.chess.core.PieceType
import com.example.chess.core.Position
import com.example.chess.core.Square

class ChessPosition(fen: String? = null) {

    private var currentPosition: Position = if (fen != null) {
        Position.tryFromFen(fen).getOrElse {
            throw IllegalArgumentException("Invalid FEN: $fen")
        }
    } else {
        Position.initial()
    }

    val internalPosition: Position
        get() = currentPosition

    val fen: String
        get() = currentPosition.toFen()

    val sideToMove: Char
        get() = if (currentPosition.sideToMove == PieceColor.WHITE) 'w' else 'b'

    val isCheck: Boolean
        get() = LegalMoveGenerator.isKingInCheck(
            currentPosition,
            currentPosition.sideToMove
        )

    val legalMoves: List<MoveChoice> by lazy {
        computeLegalMoves()
    }

    val isCheckmate: Boolean
        get() = isCheck && legalMoves.isEmpty()

    val isStalemate: Boolean
        get() = !isCheck && legalMoves.isEmpty()

    val isOver: Boolean
        get() = legalMoves.isEmpty()

    /**
     * Every returned move is verified, by playing it out, to end in
     * checkmate. This is not a general evaluation — it's an
     * exhaustive check over legal one-move continuations.
     */
    val matesInOne: List<MoveChoice> by lazy {
        if (isOver) {
            emptyList()
        } else {
            legalMoves.filter { moveChoice ->
                val testPos = ChessPosition(fen)
                testPos.play(moveChoice) && testPos.isCheckmate
            }
        }
    }

    /**
     * Identifies pieces of the side to move that are currently hanging
     * (undefended or vulnerable via Static Exchange Evaluation).
     */
    val hangingPieces: List<HangingPiece> by lazy {
        TacticalAnalysis.findHangingPieces(this)
    }

    /**
     * Identifies tactical moves in this position that execute a geometric fork
     * (simultaneous double attack against 2+ significant enemy pieces).
     */
    val forks: List<ForkTactic> by lazy {
        TacticalAnalysis.findForks(this)
    }

    fun play(move: MoveChoice): Boolean {
        val allLegal = LegalMoveGenerator.generateLegalMoves(currentPosition)

        val matched = allLegal.firstOrNull { it.uci == move.uci }
            ?: return false

        currentPosition = LegalMoveGenerator.makeMove(currentPosition, matched)
        return true
    }

    fun play(uci: String): Boolean {
        val allLegal = LegalMoveGenerator.generateLegalMoves(currentPosition)

        val matched = allLegal.firstOrNull { it.uci == uci }
            ?: return false

        currentPosition = LegalMoveGenerator.makeMove(currentPosition, matched)
        return true
    }

    fun pieceAt(squareAlgebraic: String): Char? {
        if (squareAlgebraic.length != 2) return null

        val sq = runCatching {
            Square.fromAlgebraic(squareAlgebraic)
        }.getOrNull() ?: return null

        val piece = currentPosition.pieceAt(sq) ?: return null

        return piece.type.notation.lowercaseChar()
    }

    private fun computeLegalMoves(): List<MoveChoice> {
        val moves = LegalMoveGenerator.generateLegalMoves(currentPosition)

        return moves.map { move ->
            val piece = currentPosition.pieceAt(move.from)
            val pieceType = piece?.type ?: PieceType.PAWN
            val pieceChar = pieceType.notation.lowercaseChar()
            val promoChar = move.promotion?.notation?.lowercaseChar()

            val san = SanFormatter.format(currentPosition, move)

            MoveChoice(
                from = move.from.algebraic,
                to = move.to.algebraic,
                san = san,
                piece = pieceChar,
                promotion = promoChar
            )
        }
    }


    companion object {
        const val STARTING_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    }
}
