package com.example.chess.core

/**
 * Piece Color (White / Black)
 */
enum class PieceColor {
  WHITE,
  BLACK;

  fun opposite(): PieceColor = if (this == WHITE) BLACK else WHITE
}

/**
 * Standard piece types in chess
 */
enum class PieceType(val notation: Char, val value: Int) {
  PAWN('P', 100),
  KNIGHT('N', 320),
  BISHOP('B', 330),
  ROOK('R', 500),
  QUEEN('Q', 900),
  KING('K', 20000)
}

/**
 * An immutable Chess Piece with Color and Type
 */
data class Piece(val type: PieceType, val color: PieceColor) {
  val fenChar: Char
    get() = if (color == PieceColor.WHITE) type.notation.uppercaseChar() else type.notation.lowercaseChar()

  companion object {
    fun fromFenChar(c: Char): Piece? {
      val isWhite = c.isUpperCase()
      val color = if (isWhite) PieceColor.WHITE else PieceColor.BLACK
      val type = when (c.uppercaseChar()) {
        'P' -> PieceType.PAWN
        'N' -> PieceType.KNIGHT
        'B' -> PieceType.BISHOP
        'R' -> PieceType.ROOK
        'Q' -> PieceType.QUEEN
        'K' -> PieceType.KING
        else -> return null
      }
      return Piece(type, color)
    }
  }
}

/**
 * Square on a standard 8x8 chessboard.
 * index 0 = a1, 1 = b1, ..., 7 = h1, ..., 56 = a8, 63 = h8.
 * file: 0..7 (a..h)
 * rank: 0..7 (1..8)
 */
@JvmInline
value class Square(val index: Int) {
  init {
    require(index in 0..63) { "Square index must be 0..63, was $index" }
  }

  val file: Int get() = index % 8
  val rank: Int get() = index / 8

  val fileChar: Char get() = ('a' + file)
  val rankChar: Char get() = ('1' + rank)

  val algebraic: String get() = "$fileChar$rankChar"

  val isLightSquare: Boolean get() = (file + rank) % 2 != 0

  companion object {
    fun of(file: Int, rank: Int): Square {
      require(file in 0..7 && rank in 0..7) { "Invalid coords: file=$file, rank=$rank" }
      return Square(rank * 8 + file)
    }

    fun fromAlgebraic(s: String): Square {
      require(s.length == 2) { "Invalid algebraic square string: $s" }
      val file = s[0] - 'a'
      val rank = s[1] - '1'
      return of(file, rank)
    }

    val NONE = Square(63) // placeholder sentinel if needed
  }
}

/**
 * Move representation.
 */
data class Move(
  val from: Square,
  val to: Square,
  val promotion: PieceType? = null,
  val isEnPassant: Boolean = false,
  val isCastling: Boolean = false
) {
  val uci: String
    get() = "${from.algebraic}${to.algebraic}${promotion?.notation?.lowercaseChar() ?: ""}"

  val san: String
    get() = uci

  companion object {
    fun fromUci(uci: String): Move {
      require(uci.length in 4..5) { "Invalid UCI move: $uci" }
      val from = Square.fromAlgebraic(uci.substring(0, 2))
      val to = Square.fromAlgebraic(uci.substring(2, 4))
      val promo = if (uci.length == 5) {
        when (uci[4].lowercaseChar()) {
          'q' -> PieceType.QUEEN
          'r' -> PieceType.ROOK
          'b' -> PieceType.BISHOP
          'n' -> PieceType.KNIGHT
          else -> null
        }
      } else null
      return Move(from, to, promo)
    }
  }
}

/**
 * Castling rights bitmask / boolean flags
 */
data class CastlingRights(
  val whiteKingside: Boolean = true,
  val whiteQueenside: Boolean = true,
  val blackKingside: Boolean = true,
  val blackQueenside: Boolean = true
) {
  val fenString: String
    get() {
      val sb = StringBuilder()
      if (whiteKingside) sb.append('K')
      if (whiteQueenside) sb.append('Q')
      if (blackKingside) sb.append('k')
      if (blackQueenside) sb.append('q')
      return if (sb.isEmpty()) "-" else sb.toString()
    }

  companion object {
    fun fromFen(fenPart: String): CastlingRights {
      return CastlingRights(
        whiteKingside = fenPart.contains('K'),
        whiteQueenside = fenPart.contains('Q'),
        blackKingside = fenPart.contains('k'),
        blackQueenside = fenPart.contains('q')
      )
    }

    val NONE = CastlingRights(false, false, false, false)
  }
}

enum class GameStatus {
  IN_PROGRESS,
  CHECK,
  CHECKMATE,
  STALEMATE,
  DRAW_INSUFFICIENT_MATERIAL,
  DRAW_50_MOVES
}
