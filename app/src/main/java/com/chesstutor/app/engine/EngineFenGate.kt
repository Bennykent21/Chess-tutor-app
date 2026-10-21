package com.chesstutor.app.engine

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position

/**
 * Checks a FEN before it is sent to native Stockfish.
 *
 * Stockfish 19 validates every `position` command strictly and **terminates the process**
 * (after printing `info string CRITICAL ERROR`) when a FEN is invalid. One bad position
 * would therefore kill the engine, so anything Stockfish would reject must be caught here.
 * The rules mirror Stockfish 19's `Position::set`:
 * - the FEN parses (six fields, eight ranks of eight squares, valid fields, one king each,
 *   no pawn on the first or eighth rank: [Position.tryFromFen]);
 * - at most 32 pieces;
 * - the side that is NOT to move is not in check ("king can be captured");
 * - halfmove clock 0..32767 and game ply within 100000.
 */
object EngineFenGate {
    const val MAX_PIECES = 32
    const val MAX_RULE50 = 32767
    const val MAX_GAME_PLY = 100_000

    /** Returns null when Stockfish will accept [fen], otherwise a plain-language reason. */
    fun rejectionReason(fen: String): String? {
        val position = Position.tryFromFen(fen).getOrElse { error ->
            return "Invalid FEN: ${error.message ?: "could not be parsed"}"
        }
        val pieceCount = position.squares.count { it != null }
        if (pieceCount > MAX_PIECES) return "More than $MAX_PIECES pieces on the board"
        if (position.halfmoveClock > MAX_RULE50) return "Halfmove clock is out of range"
        val gamePly = 2L * (position.fullmoveNumber - 1) + if (position.sideToMove == PieceColor.BLACK) 1 else 0
        if (gamePly > MAX_GAME_PLY) return "Move number is out of range"
        val waitingSide = position.sideToMove.opposite()
        if (LegalMoveGenerator.isKingInCheck(position, waitingSide)) {
            return "The side not to move is in check"
        }
        return null
    }

    fun isAcceptable(fen: String): Boolean = rejectionReason(fen) == null
}
