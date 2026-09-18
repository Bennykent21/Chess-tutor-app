package com.example.chess.core

/** Produces Standard Algebraic Notation for legal moves. */
object SanFormatter {

  fun format(position: Position, move: Move): String {
    val movingPiece = position.pieceAt(move.from)
      ?: error("No piece at source square " + move.from.algebraic)
    val nextPosition = LegalMoveGenerator.makeMove(position, move)
    val opponent = nextPosition.sideToMove
    val opponentInCheck = LegalMoveGenerator.isKingInCheck(nextPosition, opponent)
    val opponentHasNoMoves = LegalMoveGenerator.generateLegalMoves(nextPosition).isEmpty()
    val suffix = when {
      opponentInCheck && opponentHasNoMoves -> "#"
      opponentInCheck -> "+"
      else -> ""
    }
    if (move.isCastling) return (if (move.to.file > move.from.file) "O-O" else "O-O-O") + suffix
    val isCapture = position.pieceAt(move.to) != null || move.isEnPassant
    val promotion = move.promotion?.let { "=" + it.notation } ?: ""
    if (movingPiece.type == PieceType.PAWN) {
      return if (isCapture) move.from.fileChar.toString() + "x" + move.to.algebraic + promotion + suffix
      else move.to.algebraic + promotion + suffix
    }
    val disambiguation = disambiguation(position, move, movingPiece.type)
    val capture = if (isCapture) "x" else ""
    return movingPiece.type.notation.toString() + disambiguation + capture + move.to.algebraic + promotion + suffix
  }

  private fun disambiguation(position: Position, move: Move, pieceType: PieceType): String {
    val competitors = LegalMoveGenerator.generateLegalMoves(position).filter { candidate ->
      candidate != move && candidate.to == move.to && position.pieceAt(candidate.from)?.type == pieceType
    }
    if (competitors.isEmpty()) return ""
    val sameFile = competitors.any { it.from.file == move.from.file }
    val sameRank = competitors.any { it.from.rank == move.from.rank }
    return when {
      !sameFile -> move.from.fileChar.toString()
      !sameRank -> move.from.rankChar.toString()
      else -> move.from.algebraic
    }
  }
}
