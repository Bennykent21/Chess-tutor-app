package com.example.chess.core

/** Produces Standard Algebraic Notation for legal moves. */
object SanFormatter {

  fun format(position: Position, move: Move): String {
    val canonicalMove = LegalMoveGenerator.generateLegalMoves(position)
      .firstOrNull { it.uci == move.uci }
      ?: throw IllegalArgumentException("Illegal move " + move.uci)
    val movingPiece = position.pieceAt(canonicalMove.from)
      ?: error("No piece at source square " + canonicalMove.from.algebraic)
    val nextPosition = LegalMoveGenerator.makeMove(position, canonicalMove)
    val opponent = nextPosition.sideToMove
    val opponentInCheck = LegalMoveGenerator.isKingInCheck(nextPosition, opponent)
    val opponentHasNoMoves = LegalMoveGenerator.generateLegalMoves(nextPosition).isEmpty()
    val suffix = when {
      opponentInCheck && opponentHasNoMoves -> "#"
      opponentInCheck -> "+"
      else -> ""
    }
    if (canonicalMove.isCastling) return (if (canonicalMove.to.file > canonicalMove.from.file) "O-O" else "O-O-O") + suffix
    val isCapture = position.pieceAt(canonicalMove.to) != null || canonicalMove.isEnPassant
    val promotion = canonicalMove.promotion?.let { "=" + it.notation } ?: ""
    if (movingPiece.type == PieceType.PAWN) {
      return if (isCapture) canonicalMove.from.fileChar.toString() + "x" + canonicalMove.to.algebraic + promotion + suffix
      else canonicalMove.to.algebraic + promotion + suffix
    }
    val disambiguation = disambiguation(position, canonicalMove, movingPiece.type)
    val capture = if (isCapture) "x" else ""
    return movingPiece.type.notation.toString() + disambiguation + capture + canonicalMove.to.algebraic + promotion + suffix
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
