package com.chesstutor.app.domain

import com.chesstutor.app.core.GameState
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.SanFormatter
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.Square

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
        get() = gameState.isSeventyFiveMoveDraw && gameState.status == com.chesstutor.app.core.GameStatus.DRAW_75_MOVES

    private var cachedLegalMoves: List<MoveChoice>? = null
    private var cachedForks: List<ForkTactic>? = null

    val legalMoves: List<MoveChoice>
        get() = cachedLegalMoves ?: computeLegalMoves().also { cachedLegalMoves = it }

    val isCheckmate: Boolean
        get() = gameState.status == com.chesstutor.app.core.GameStatus.CHECKMATE

    val isStalemate: Boolean
        get() = gameState.status == com.chesstutor.app.core.GameStatus.STALEMATE

    val isOver: Boolean
        get() = gameState.isOver

    /** Identifies tactical moves that execute a geometric fork. */
    val forks: List<ForkTactic>
        get() = cachedForks ?: TacticalAnalysis.findForks(this).also { cachedForks = it }

    fun play(move: MoveChoice): Boolean {
        return play(move.uci)
    }

    fun play(uci: String): Boolean {
        val parsedMove = runCatching { com.chesstutor.app.core.Move.fromUci(uci) }.getOrNull()
            ?: return false

        return runCatching {
            gameState = gameState.play(parsedMove)
            cachedLegalMoves = null
            cachedForks = null
            true
        }.getOrDefault(false)
    }

    /**
     * Converts a legal UCI move in the current position to Standard Algebraic Notation (SAN).
     * Returns null if the UCI move is blank, terminal ("0000"), or not legal in this position.
     */
    fun toSan(uci: String): String? {
        val trimmed = uci.trim()
        if (trimmed.isEmpty() || trimmed == "0000") return null
        val parsedMove = runCatching { com.chesstutor.app.core.Move.fromUci(trimmed) }.getOrNull()
            ?: return null
        return runCatching {
            SanFormatter.format(gameState.position, parsedMove)
        }.getOrNull()
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

            MoveChoice.fromCoreMove(
                move = move,
                san = SanFormatter.format(position, move),
                piece = pieceChar
            )
        }
    }

    companion object {
        const val STARTING_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        fun replayUciToSan(uciMoves: String, startFen: String = STARTING_FEN): List<String> {
            val tokens = uciMoves.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.isEmpty()) return emptyList()
            val pos = runCatching { ChessPosition(startFen) }.getOrNull() ?: return emptyList()
            val sanList = mutableListOf<String>()
            for (uci in tokens) {
                val san = pos.toSan(uci) ?: break
                if (!pos.play(uci)) break
                sanList += san
            }
            return sanList
        }
    }
}
