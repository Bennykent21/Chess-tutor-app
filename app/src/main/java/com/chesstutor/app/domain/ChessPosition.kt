package com.chesstutor.app.domain

import com.example.chess.core.GameState
import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.SanFormatter
import com.example.chess.core.PieceColor
import com.example.chess.core.PieceType
import com.example.chess.core.Position
import com.example.chess.core.Square

class ChessPosition(fen: String? = null) {

    private var gameState: GameState = if (fen != null) {
        val position = Position.tryFromFen(fen).getOrElse {
            throw IllegalArgumentException("Invalid FEN: $fen")
        }
        GameState(position)
    } else {
        GameState()
    }

    val internalPosition: Position
        get() = gameState.position

    val fen: String
        get() = gameState.position.toFen()

    val sideToMove: Char
        get() = if (gameState.position.sideToMove == PieceColor.WHITE) 'w' else 'b'

    val isCheck: Boolean
        get() = LegalMoveGenerator.isKingInCheck(
            gameState.position,
            gameState.position.sideToMove
        )

    /** History-aware game status, including repetition and move-count draws. */
    val gameStatus
        get() = gameState.status

    /** Number of occurrences of the current repetition position. */
    val currentPositionOccurrences: Int
        get() = gameState.currentPositionOccurrences

    /** Threefold repetition is claimable but does not automatically end play. */
    val canClaimThreefoldRepetition: Boolean
        get() = gameState.canClaimThreefoldRepetition

    /** The 50-move rule is claimable but does not automatically end play. */
    val canClaimFiftyMoveRule: Boolean
        get() = gameState.canClaimFiftyMoveRule

    /** Fivefold repetition automatically ends the game. */
    val isFivefoldRepetition: Boolean
        get() = gameState.isFivefoldRepetition

    /** The 75-move rule automatically ends the game unless the last move checkmated. */
    val isSeventyFiveMoveDraw: Boolean
        get() = gameState.isSeventyFiveMoveDraw && gameState.status == com.example.chess.core.GameStatus.DRAW_75_MOVES

    val legalMoves: List<MoveChoice>
        get() = computeLegalMoves()

    val isCheckmate: Boolean
        get() = gameState.status == com.example.chess.core.GameStatus.CHECKMATE

    val isStalemate: Boolean
        get() = gameState.status == com.example.chess.core.GameStatus.STALEMATE

    val isOver: Boolean
        get() = gameState.isOver

    /**
     * Every returned move is verified, by playing it out, to end in
     * checkmate. This is an exhaustive check over legal one-move continuations.
     */
    val matesInOne: List<MoveChoice>
        get() = if (isOver) {
            emptyList()
        } else {
            legalMoves.filter { moveChoice ->
                val testPos = ChessPosition(fen)
                testPos.play(moveChoice) && testPos.isCheckmate
            }
        }

    /** Identifies pieces of the side to move that are currently hanging. */
    val hangingPieces: List<HangingPiece>
        get() = TacticalAnalysis.findHangingPieces(this)

    /** Identifies tactical moves that execute a geometric fork. */
    val forks: List<ForkTactic>
        get() = TacticalAnalysis.findForks(this)

    fun play(move: MoveChoice): Boolean {
        return play(move.uci)
    }

    fun play(uci: String): Boolean {
        val parsedMove = runCatching { com.example.chess.core.Move.fromUci(uci) }.getOrNull()
            ?: return false

        return runCatching {
            gameState = gameState.play(parsedMove)
            true
        }.getOrDefault(false)
    }

    fun pieceAt(squareAlgebraic: String): Char? {
        if (squareAlgebraic.length != 2) return null

        val sq = runCatching {
            Square.fromAlgebraic(squareAlgebraic)
        }.getOrNull() ?: return null

        val piece = gameState.position.pieceAt(sq) ?: return null
        return piece.type.notation.lowercaseChar()
    }

    private fun computeLegalMoves(): List<MoveChoice> {
        val position = gameState.position
        return LegalMoveGenerator.generateLegalMoves(position).map { move ->
            val piece = position.pieceAt(move.from)
            val pieceType = piece?.type ?: PieceType.PAWN
            val pieceChar = pieceType.notation.lowercaseChar()
            val promoChar = move.promotion?.notation?.lowercaseChar()

            MoveChoice(
                from = move.from.algebraic,
                to = move.to.algebraic,
                san = SanFormatter.format(position, move),
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
