package com.chesstutor.app.core

import kotlin.math.abs

/**
 * Pure, high-performance legal move generator and rules validator for chess.
 * Handles checks, pins, en passant, castling, promotion, and terminal states.
 */
object LegalMoveGenerator {

  // Offsets for sliding pieces
  private val ROOK_DELTAS = arrayOf(Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1))
  private val BISHOP_DELTAS = arrayOf(Pair(1, 1), Pair(1, -1), Pair(-1, 1), Pair(-1, -1))
  private val QUEEN_DELTAS = ROOK_DELTAS + BISHOP_DELTAS

  // Knight jump offsets
  private val KNIGHT_DELTAS = arrayOf(
    Pair(1, 2), Pair(2, 1), Pair(2, -1), Pair(1, -2),
    Pair(-1, -2), Pair(-2, -1), Pair(-2, 1), Pair(-1, 2)
  )

  // King offsets
  private val KING_DELTAS = QUEEN_DELTAS

  /**
   * Generates all strictly legal moves for the current side to move.
   */
  fun generateLegalMoves(position: Position): List<Move> {
    val pseudoMoves = generatePseudoLegalMoves(position, position.sideToMove)
    val legalMoves = mutableListOf<Move>()
    val color = position.sideToMove

    for (move in pseudoMoves) {
      val nextPos = applyMoveFast(position, move)
      // If King is not in check in next position, move is legal
      val kingSquare = nextPos.findKing(color)
      if (!isSquareAttacked(nextPos, kingSquare, color.opposite())) {
        legalMoves.add(move)
      }
    }

    return legalMoves
  }

  /**
   * Checks if the given square is attacked by any piece of attackerColor.
   */
  fun isSquareAttacked(position: Position, square: Square, attackerColor: PieceColor): Boolean {
    val file = square.file
    val rank = square.rank

    // 1. Attacked by pawns
    val pawnPushedRank = if (attackerColor == PieceColor.WHITE) rank - 1 else rank + 1
    if (pawnPushedRank in 0..7) {
      if (file - 1 >= 0) {
        val p = position.pieceAt(file - 1, pawnPushedRank)
        if (p?.color == attackerColor && p.type == PieceType.PAWN) return true
      }
      if (file + 1 <= 7) {
        val p = position.pieceAt(file + 1, pawnPushedRank)
        if (p?.color == attackerColor && p.type == PieceType.PAWN) return true
      }
    }

    // 2. Attacked by Knights
    for ((df, dr) in KNIGHT_DELTAS) {
      val nf = file + df
      val nr = rank + dr
      if (nf in 0..7 && nr in 0..7) {
        val p = position.pieceAt(nf, nr)
        if (p?.color == attackerColor && p.type == PieceType.KNIGHT) return true
      }
    }

    // 3. Attacked along straight lines (Rook / Queen)
    for ((df, dr) in ROOK_DELTAS) {
      var cf = file + df
      var cr = rank + dr
      while (cf in 0..7 && cr in 0..7) {
        val p = position.pieceAt(cf, cr)
        if (p != null) {
          if (p.color == attackerColor && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) {
            return true
          }
          break // Ray blocked
        }
        cf += df
        cr += dr
      }
    }

    // 4. Attacked along diagonals (Bishop / Queen)
    for ((df, dr) in BISHOP_DELTAS) {
      var cf = file + df
      var cr = rank + dr
      while (cf in 0..7 && cr in 0..7) {
        val p = position.pieceAt(cf, cr)
        if (p != null) {
          if (p.color == attackerColor && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) {
            return true
          }
          break // Ray blocked
        }
        cf += df
        cr += dr
      }
    }

    // 5. Attacked by King (1 step)
    for ((df, dr) in KING_DELTAS) {
      val kf = file + df
      val kr = rank + dr
      if (kf in 0..7 && kr in 0..7) {
        val p = position.pieceAt(kf, kr)
        if (p?.color == attackerColor && p.type == PieceType.KING) return true
      }
    }

    return false
  }

  fun isKingInCheck(position: Position, color: PieceColor = position.sideToMove): Boolean {
    val kingSquare = position.findKing(color)
    return isSquareAttacked(position, kingSquare, color.opposite())
  }

  fun isCheckmate(position: Position): Boolean = getGameStatus(position) == GameStatus.CHECKMATE

  fun getGameStatus(position: Position): GameStatus {
    val legalMoves = generateLegalMoves(position)
    val inCheck = isKingInCheck(position, position.sideToMove)

    if (legalMoves.isEmpty()) {
      return if (inCheck) GameStatus.CHECKMATE else GameStatus.STALEMATE
    }

    if (position.halfmoveClock >= 150) return GameStatus.DRAW_75_MOVES
    if (isInsufficientMaterial(position)) return GameStatus.DRAW_INSUFFICIENT_MATERIAL
    if (position.halfmoveClock >= 100) return GameStatus.DRAW_50_MOVES
    if (inCheck) return GameStatus.CHECK

    return GameStatus.IN_PROGRESS
  }

  fun isInsufficientMaterial(position: Position): Boolean {
    val nonKings = mutableListOf<Pair<Piece, Square>>()

    for (i in 0 until 64) {
      val piece = position.squares[i] ?: continue
      if (piece.type != PieceType.KING) {
        nonKings += piece to Square(i)
      }
    }

    // Any pawn, rook, or queen means checkmate/stalemate may still be possible.
    if (nonKings.any { it.first.type in setOf(PieceType.PAWN, PieceType.ROOK, PieceType.QUEEN) }) {
      return false
    }

    // K vs K
    if (nonKings.isEmpty()) return true

    // K+B vs K or K+N vs K
    if (nonKings.size == 1) {
      return nonKings[0].first.type == PieceType.BISHOP ||
        nonKings[0].first.type == PieceType.KNIGHT
    }

    // K+B vs K+B is dead only when both bishops live on the same color complex.
    if (nonKings.size == 2 &&
      nonKings.all { it.first.type == PieceType.BISHOP }
    ) {
      val first = nonKings[0].second
      val second = nonKings[1].second
      return first.isLightSquare == second.isLightSquare
    }

    // K+N vs K+N, K+B vs K+N, and positions with multiple minors
    // are not classified as dead here; proving all dead positions is a
    // separate rule-engine concern.
    return false
  }
  /**
   * Applies a legal move to a position, returning the new resulting Position.
   */
  /** Where the king and rook start and land in a castling move. */
  data class CastleSquares(val kingFrom: Square, val kingTo: Square, val rookFrom: Square, val rookTo: Square) {
    val kingside: Boolean get() = kingTo.file == 6
  }

  /**
   * The squares of [move] when it castles, else null: the king two files sideways in standard
   * chess, or the king onto its own rook (always so in Chess960).
   */
  fun castleSquares(position: Position, move: Move): CastleSquares? {
    val king = position.pieceAt(move.from) ?: return null
    if (king.type != PieceType.KING) return null
    val rank = move.from.rank
    val target = position.pieceAt(move.to)
    val kingside: Boolean
    val rookFrom: Square
    if (target != null && target.color == king.color && target.type == PieceType.ROOK) {
      kingside = move.to.file > move.from.file
      rookFrom = move.to
    } else if (!position.castlingRights.chess960 && abs(move.to.file - move.from.file) == 2) {
      kingside = move.to.file == 6
      rookFrom = Square.of(if (kingside) 7 else 0, rank)
    } else {
      return null
    }
    return CastleSquares(move.from, Square.of(if (kingside) 6 else 2, rank), rookFrom, Square.of(if (kingside) 5 else 3, rank))
  }

  fun makeMove(position: Position, move: Move): Position {
    val movingPiece = position.pieceAt(move.from)
      ?: error("No piece at source square ${move.from.algebraic}")
    val isPawn = movingPiece.type == PieceType.PAWN
    val target = position.pieceAt(move.to)
    val isCapture = (target != null && target.color != movingPiece.color) || move.isEnPassant

    val newSquares = position.squares.clone()
    newSquares[move.from.index] = null

    var nextEnPassant: Square? = null

    // Handle pawn logic (advancement, promotion, en passant)
    if (isPawn) {
      if (move.isEnPassant) {
        // Remove captured pawn behind target square
        val capturedPawnRank = if (movingPiece.color == PieceColor.WHITE) move.to.rank - 1 else move.to.rank + 1
        val capturedSquare = Square.of(move.to.file, capturedPawnRank)
        newSquares[capturedSquare.index] = null
      }

      // Check double step for setting en passant target
      if (abs(move.to.rank - move.from.rank) == 2) {
        val epRank = if (movingPiece.color == PieceColor.WHITE) move.from.rank + 1 else move.from.rank - 1
        nextEnPassant = Square.of(move.from.file, epRank)
      }

      // Promotion
      if (move.promotion != null) {
        newSquares[move.to.index] = Piece(move.promotion, movingPiece.color)
      } else {
        newSquares[move.to.index] = movingPiece
      }
    } else if (movingPiece.type == PieceType.KING) {
      val castle = castleSquares(position, move)
      if (castle == null) {
        newSquares[move.to.index] = movingPiece
      } else {
        // Both leave their squares first: in Chess960 the king may land where the rook stood.
        val rook = newSquares[castle.rookFrom.index]
        newSquares[castle.rookFrom.index] = null
        newSquares[castle.kingTo.index] = movingPiece
        newSquares[castle.rookTo.index] = rook
      }
    } else {
      newSquares[move.to.index] = movingPiece
    }

    // Update castling rights
    var castling = position.castlingRights
    if (movingPiece.type == PieceType.KING) {
      castling = if (movingPiece.color == PieceColor.WHITE) {
        castling.copy(whiteKingside = false, whiteQueenside = false)
      } else {
        castling.copy(blackKingside = false, blackQueenside = false)
      }
    }

    // If rooks move or get captured
    val queenside = castling.queensideFile
    val kingside = castling.kingsideFile
    if (move.from.index == queenside || move.to.index == queenside) castling = castling.copy(whiteQueenside = false)
    if (move.from.index == kingside || move.to.index == kingside) castling = castling.copy(whiteKingside = false)
    if (move.from.index == 56 + queenside || move.to.index == 56 + queenside) castling = castling.copy(blackQueenside = false)
    if (move.from.index == 56 + kingside || move.to.index == 56 + kingside) castling = castling.copy(blackKingside = false)

    val nextHalfmove = if (isPawn || isCapture) 0 else position.halfmoveClock + 1
    val nextFullmove = if (position.sideToMove == PieceColor.BLACK) position.fullmoveNumber + 1 else position.fullmoveNumber

    return Position(
      squares = newSquares,
      sideToMove = position.sideToMove.opposite(),
      castlingRights = castling,
      enPassantSquare = nextEnPassant,
      halfmoveClock = nextHalfmove,
      fullmoveNumber = nextFullmove
    )
  }

  private fun applyMoveFast(position: Position, move: Move): Position {
    return makeMove(position, move)
  }

  private fun generatePseudoLegalMoves(position: Position, color: PieceColor): List<Move> {
    val moves = mutableListOf<Move>()

    for (i in 0 until 64) {
      val piece = position.squares[i] ?: continue
      if (piece.color != color) continue
      val square = Square(i)

      when (piece.type) {
        PieceType.PAWN -> generatePawnMoves(position, square, color, moves)
        PieceType.KNIGHT -> generateKnightMoves(position, square, color, moves)
        PieceType.BISHOP -> generateSlidingMoves(position, square, color, BISHOP_DELTAS, moves)
        PieceType.ROOK -> generateSlidingMoves(position, square, color, ROOK_DELTAS, moves)
        PieceType.QUEEN -> generateSlidingMoves(position, square, color, QUEEN_DELTAS, moves)
        PieceType.KING -> generateKingMoves(position, square, color, moves)
      }
    }

    return moves
  }

  private fun generatePawnMoves(
    position: Position,
    from: Square,
    color: PieceColor,
    outMoves: MutableList<Move>
  ) {
    val forwardDir = if (color == PieceColor.WHITE) 1 else -1
    val startRank = if (color == PieceColor.WHITE) 1 else 6
    val promoRank = if (color == PieceColor.WHITE) 7 else 0

    val nextRank = from.rank + forwardDir

    // 1. Single advance
    if (nextRank in 0..7 && position.pieceAt(from.file, nextRank) == null) {
      val targetSq = Square.of(from.file, nextRank)
      if (nextRank == promoRank) {
        addPromotionMoves(from, targetSq, outMoves)
      } else {
        outMoves.add(Move(from, targetSq))

        // 2. Double advance from initial rank
        val doubleRank = from.rank + (2 * forwardDir)
        if (from.rank == startRank && position.pieceAt(from.file, doubleRank) == null) {
          outMoves.add(Move(from, Square.of(from.file, doubleRank)))
        }
      }
    }

    // 3. Captures
    for (df in arrayOf(-1, 1)) {
      val targetFile = from.file + df
      if (targetFile in 0..7 && nextRank in 0..7) {
        val targetSq = Square.of(targetFile, nextRank)
        val occupant = position.pieceAt(targetSq)

        if (occupant != null && occupant.color == color.opposite() && occupant.type != PieceType.KING) {
          if (nextRank == promoRank) {
            addPromotionMoves(from, targetSq, outMoves)
          } else {
            outMoves.add(Move(from, targetSq))
          }
        } else if (position.enPassantSquare == targetSq) {
          // En passant capture
          outMoves.add(Move(from, targetSq, isEnPassant = true))
        }
      }
    }
  }

  private fun addPromotionMoves(from: Square, to: Square, outMoves: MutableList<Move>) {
    outMoves.add(Move(from, to, promotion = PieceType.QUEEN))
    outMoves.add(Move(from, to, promotion = PieceType.ROOK))
    outMoves.add(Move(from, to, promotion = PieceType.BISHOP))
    outMoves.add(Move(from, to, promotion = PieceType.KNIGHT))
  }

  private fun generateKnightMoves(
    position: Position,
    from: Square,
    color: PieceColor,
    outMoves: MutableList<Move>
  ) {
    for ((df, dr) in KNIGHT_DELTAS) {
      val nf = from.file + df
      val nr = from.rank + dr
      if (nf in 0..7 && nr in 0..7) {
        val targetSq = Square.of(nf, nr)
        val occupant = position.pieceAt(targetSq)
        if (occupant == null || (occupant.color != color && occupant.type != PieceType.KING)) {
          outMoves.add(Move(from, targetSq))
        }
      }
    }
  }

  private fun generateSlidingMoves(
    position: Position,
    from: Square,
    color: PieceColor,
    deltas: Array<Pair<Int, Int>>,
    outMoves: MutableList<Move>
  ) {
    for ((df, dr) in deltas) {
      var cf = from.file + df
      var cr = from.rank + dr
      while (cf in 0..7 && cr in 0..7) {
        val targetSq = Square.of(cf, cr)
        val occupant = position.pieceAt(targetSq)
        if (occupant == null) {
          outMoves.add(Move(from, targetSq))
        } else {
          if (occupant.color != color && occupant.type != PieceType.KING) {
            outMoves.add(Move(from, targetSq))
          }
          break // Ray stops when hitting a piece
        }
        cf += df
        cr += dr
      }
    }
  }

  private fun generateKingMoves(
    position: Position,
    from: Square,
    color: PieceColor,
    outMoves: MutableList<Move>
  ) {
    // 1-step King moves
    for ((df, dr) in KING_DELTAS) {
      val kf = from.file + df
      val kr = from.rank + dr
      if (kf in 0..7 && kr in 0..7) {
        val targetSq = Square.of(kf, kr)
        val occupant = position.pieceAt(targetSq)
        if (occupant == null || (occupant.color != color && occupant.type != PieceType.KING)) {
          outMoves.add(Move(from, targetSq))
        }
      }
    }

    // Castling: the king ends on g or c and the rook on f or d, in standard chess and Chess960.
    val rank = if (color == PieceColor.WHITE) 0 else 7
    val rights = position.castlingRights
    if (from.rank != rank || (!rights.chess960 && from.file != 4)) return
    val enemyColor = color.opposite()
    for (kingside in booleanArrayOf(true, false)) {
      val allowed = when {
        color == PieceColor.WHITE && kingside -> rights.whiteKingside
        color == PieceColor.WHITE -> rights.whiteQueenside
        kingside -> rights.blackKingside
        else -> rights.blackQueenside
      }
      if (!allowed) continue
      val rookFile = if (kingside) rights.kingsideFile else rights.queensideFile
      if ((rookFile > from.file) != kingside) continue
      if (position.pieceAt(rookFile, rank) != Piece(PieceType.ROOK, color)) continue
      val kingTo = if (kingside) 6 else 2
      val rookTo = if (kingside) 5 else 3
      // Every square either piece crosses or lands on is empty, apart from the two of them.
      val low = minOf(from.file, kingTo, rookFile, rookTo)
      val high = maxOf(from.file, kingTo, rookFile, rookTo)
      if ((low..high).any { f -> f != from.file && f != rookFile && position.pieceAt(f, rank) != null }) continue
      // The king may not castle out of, through, or into check.
      val path = if (kingTo >= from.file) from.file..kingTo else kingTo..from.file
      if (path.any { f -> isSquareAttacked(position, Square.of(f, rank), enemyColor) }) continue
      val to = if (rights.chess960) Square.of(rookFile, rank) else Square.of(kingTo, rank)
      outMoves.add(Move(from, to, isCastling = true))
    }
  }
}
