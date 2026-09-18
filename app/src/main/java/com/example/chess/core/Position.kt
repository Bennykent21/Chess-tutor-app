package com.example.chess.core

/**
 * Immutable Chess Position representing the full state of a game.
 */
data class Position(
  val squares: Array<Piece?>,
  val sideToMove: PieceColor = PieceColor.WHITE,
  val castlingRights: CastlingRights = CastlingRights(),
  val enPassantSquare: Square? = null,
  val halfmoveClock: Int = 0,
  val fullmoveNumber: Int = 1
) {
  init {
    require(squares.size == 64) { "Board must have 64 squares" }
  }

  fun pieceAt(square: Square): Piece? = squares[square.index]
  fun pieceAt(file: Int, rank: Int): Piece? = squares[rank * 8 + file]

  fun findKing(color: PieceColor): Square {
    for (i in 0 until 64) {
      val piece = squares[i]
      if (piece != null && piece.type == PieceType.KING && piece.color == color) {
        return Square(i)
      }
    }
    error("King not found on board for color $color")
  }

  /**
   * Generates standard FEN string for this position
   */
  fun toFen(): String {
    val sb = StringBuilder()
    // 1. Piece placement rank 8 down to rank 1
    for (rank in 7 downTo 0) {
      var emptyCount = 0
      for (file in 0..7) {
        val piece = pieceAt(file, rank)
        if (piece == null) {
          emptyCount++
        } else {
          if (emptyCount > 0) {
            sb.append(emptyCount)
            emptyCount = 0
          }
          sb.append(piece.fenChar)
        }
      }
      if (emptyCount > 0) {
        sb.append(emptyCount)
      }
      if (rank > 0) sb.append('/')
    }

    // 2. Active color
    sb.append(' ').append(if (sideToMove == PieceColor.WHITE) 'w' else 'b')

    // 3. Castling rights
    sb.append(' ').append(castlingRights.fenString)

    // 4. En passant target square
    sb.append(' ').append(enPassantSquare?.algebraic ?: "-")

    // 5. Halfmove clock
    sb.append(' ').append(halfmoveClock)

    // 6. Fullmove number
    sb.append(' ').append(fullmoveNumber)

    return sb.toString()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Position
    if (!squares.contentEquals(other.squares)) return false
    if (sideToMove != other.sideToMove) return false
    if (castlingRights != other.castlingRights) return false
    if (enPassantSquare != other.enPassantSquare) return false
    if (halfmoveClock != other.halfmoveClock) return false
    if (fullmoveNumber != other.fullmoveNumber) return false

    return true
  }

  override fun hashCode(): Int {
    var result = squares.contentHashCode()
    result = 31 * result + sideToMove.hashCode()
    result = 31 * result + castlingRights.hashCode()
    result = 31 * result + (enPassantSquare?.hashCode() ?: 0)
    result = 31 * result + halfmoveClock
    result = 31 * result + fullmoveNumber
    return result
  }

  companion object {
    const val STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    fun initial(): Position = fromFen(STARTING_FEN)

    /**
     * Safely validates and parses an arbitrary FEN string, ensuring syntax and king presence.
     */
    fun tryFromFen(fen: String): Result<Position> {
      return runCatching {
        val trimmed = fen.trim()
        require(trimmed.isNotBlank()) { "FEN string cannot be blank" }
        val pos = fromFen(trimmed)
        pos.findKing(PieceColor.WHITE)
        pos.findKing(PieceColor.BLACK)
        pos
      }
    }

    fun fromFen(fen: String): Position {
      val parts = fen.trim().split(Regex("\\s+"))
      require(parts.size == 6) { "FEN must contain exactly 6 fields" }

      val boardArray = arrayOfNulls<Piece>(64)
      val ranks = parts[0].split('/')
      require(ranks.size == 8) { "Invalid FEN board ranks count: ${ranks.size}" }

      for (r in 0..7) {
        val rankIdx = 7 - r
        var fileIdx = 0
        for (char in ranks[r]) {
          if (char.isDigit()) {
            val empty = char.digitToInt()
            require(empty in 1..8) { "Invalid empty-square count in rank ${8 - r}: $char" }
            fileIdx += empty
          } else {
            require(fileIdx in 0..7) { "Rank ${8 - r} contains too many squares" }
            val piece = Piece.fromFenChar(char)
              ?: error("Invalid piece char in FEN: $char")
            boardArray[rankIdx * 8 + fileIdx] = piece
            fileIdx++
          }
          require(fileIdx <= 8) { "Rank ${8 - r} contains too many squares" }
        }
        require(fileIdx == 8) { "Rank ${8 - r} does not equal 8 squares" }
      }

      val activeColor = when (parts[1]) {
        "w" -> PieceColor.WHITE
        "b" -> PieceColor.BLACK
        else -> error("Invalid active color in FEN: ${parts[1]}")
      }

      val castlingField = parts[2]
      require(
        castlingField == "-" ||
          castlingField.all { it in "KQkq" } &&
          castlingField.toSet().size == castlingField.length
      ) { "Invalid castling rights in FEN: $castlingField" }
      val castling = CastlingRights.fromFen(castlingField)

      val epField = parts[3]
      val epSquare = if (epField == "-") {
        null
      } else {
        require(epField.length == 2 && epField[0] in 'a'..'h') {
          "Invalid en passant square in FEN: $epField"
        }
        require(
          (activeColor == PieceColor.WHITE && epField[1] == '6') ||
            (activeColor == PieceColor.BLACK && epField[1] == '3')
        ) { "Invalid en passant rank in FEN: $epField" }
        Square.fromAlgebraic(epField)
      }

      val halfmove = parts[4].toIntOrNull()
        ?: error("Invalid halfmove clock in FEN: ${parts[4]}")
      require(halfmove >= 0) { "Halfmove clock cannot be negative" }

      val fullmove = parts[5].toIntOrNull()
        ?: error("Invalid fullmove number in FEN: ${parts[5]}")
      require(fullmove >= 1) { "Fullmove number must be at least 1" }

      val position = Position(
        squares = boardArray,
        sideToMove = activeColor,
        castlingRights = castling,
        enPassantSquare = epSquare,
        halfmoveClock = halfmove,
        fullmoveNumber = fullmove
      )

      position.findKing(PieceColor.WHITE)
      position.findKing(PieceColor.BLACK)

      return position
    }
  }
}
