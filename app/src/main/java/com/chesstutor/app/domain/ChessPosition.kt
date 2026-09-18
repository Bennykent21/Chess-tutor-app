package com.chesstutor.app.domain

import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Move
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

            val san = computeSan(move, pieceType)

            MoveChoice(
                from = move.from.algebraic,
                to = move.to.algebraic,
                san = san,
                piece = pieceChar,
                promotion = promoChar
            )
        }
    }

    /**
     * Generates Standard Algebraic Notation (SAN) for a legal move.
     *
     * Handles:
     * - normal moves
     * - captures
     * - pawn captures
     * - castling
     * - promotion
     * - check
     * - checkmate
     * - piece disambiguation
     *
     * Example:
     *     Nbd2
     *     R1e2
     *     Qxd5+
     */
    private fun computeSan(move: Move, pieceType: PieceType): String {
        val nextPos = LegalMoveGenerator.makeMove(currentPosition, move)

        val opponentInCheck = LegalMoveGenerator.isKingInCheck(
            nextPos,
            nextPos.sideToMove
        )

        val opponentHasNoMoves =
            LegalMoveGenerator.generateLegalMoves(nextPos).isEmpty()

        val suffix = when {
            opponentInCheck && opponentHasNoMoves -> "#"
            opponentInCheck -> "+"
            else -> ""
        }

        if (move.isCastling) {
            val isKingside = move.to.file > move.from.file
            return (if (isKingside) "O-O" else "O-O-O") + suffix
        }

        val target = move.to.algebraic

        val isCapture =
            currentPosition.pieceAt(move.to) != null || move.isEnPassant

        val promoSuffix =
            if (move.promotion != null) {
                "=${move.promotion.notation.uppercaseChar()}"
            } else {
                ""
            }

        if (pieceType == PieceType.PAWN) {
            return if (isCapture) {
                "${move.from.fileChar}x$target$promoSuffix$suffix"
            } else {
                "$target$promoSuffix$suffix"
            }
        }

        val piecePrefix = pieceType.notation.uppercaseChar().toString()

        val disambiguation = computeDisambiguation(
            move = move,
            pieceType = pieceType
        )

        val captureChar = if (isCapture) "x" else ""

        return "$piecePrefix$disambiguation$captureChar$target$promoSuffix$suffix"
    }

    /**
     * Determines the SAN disambiguation required when more than one
     * piece of the same type can legally move to the same destination.
     *
     * Examples:
     *
     *     Nbd2
     *     Nfd2
     *
     * or, when files are identical:
     *
     *     R1e2
     *     R3e2
     */
    private fun computeDisambiguation(
        move: Move,
        pieceType: PieceType
    ): String {
        val allLegalMoves =
            LegalMoveGenerator.generateLegalMoves(currentPosition)

        val competingMoves = allLegalMoves.filter { candidate ->
            candidate != move &&
                    candidate.to == move.to &&
                    currentPosition.pieceAt(candidate.from)?.type == pieceType
        }

        if (competingMoves.isEmpty()) {
            return ""
        }

        val sameFile = competingMoves.any {
            it.from.file == move.from.file
        }

        val sameRank = competingMoves.any {
            it.from.rank == move.from.rank
        }

        return when {
            !sameFile -> {
                move.from.fileChar.toString()
            }

            !sameRank -> {
                move.from.rank.toString()
            }

            else -> {
                move.from.algebraic
            }
        }
    }

    companion object {
        const val STARTING_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    }
}
