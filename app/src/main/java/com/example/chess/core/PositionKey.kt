package com.example.chess.core

/**
 * Canonical identity used for repetition detection.
 *
 * Halfmove/fullmove clocks are deliberately excluded because they do not
 * affect whether two positions have the same legal moves. En-passant is only
 * included when the side to move has an actual legal en-passant capture.
 */
fun Position.repetitionKey(): String {
  val board = buildString {
    for (rank in 7 downTo 0) {
      var empty = 0
      for (file in 0..7) {
        val piece = pieceAt(file, rank)
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
      if (rank > 0) append('/')
    }
  }

  val effectiveEnPassant = if (
    enPassantSquare != null &&
    LegalMoveGenerator.generateLegalMoves(this).any { it.isEnPassant }
  ) {
    enPassantSquare.algebraic
  } else {
    "-"
  }

  val activeColor = if (sideToMove == PieceColor.WHITE) "w" else "b"
  return "$board $activeColor ${castlingRights.fenString} $effectiveEnPassant"
}
