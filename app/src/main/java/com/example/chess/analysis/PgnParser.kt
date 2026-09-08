package com.example.chess.analysis

import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Move
import com.example.chess.core.PieceColor
import com.example.chess.core.PieceType
import com.example.chess.core.Position
import com.example.chess.core.Square

data class ParsedPgnMove(
  val san: String,
  val move: Move,
  val positionAfter: Position,
  val moveNumber: Int,
  val color: PieceColor
)

data class ParsedPgnGame(
  val white: String,
  val black: String,
  val result: String,
  val event: String,
  val date: String,
  val moves: List<ParsedPgnMove>
)

object PgnParser {

  fun parse(pgnText: String): ParsedPgnGame {
    val headers = mutableMapOf<String, String>()
    val moveTokens = mutableListOf<String>()

    val lines = pgnText.lines()
    var readingMoves = false
    val movesTextBuilder = StringBuilder()

    for (line in lines) {
      val trimmed = line.trim()
      if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
        val content = trimmed.removeSurrounding("[", "]")
        val spaceIdx = content.indexOf(' ')
        if (spaceIdx > 0) {
          val key = content.substring(0, spaceIdx).trim()
          val value = content.substring(spaceIdx + 1).trim().removeSurrounding("\"")
          headers[key] = value
        }
      } else if (trimmed.isNotEmpty()) {
        readingMoves = true
        movesTextBuilder.append(" ").append(trimmed)
      }
    }

    val cleanMovesText = movesTextBuilder.toString()
      .replace(Regex("\\{[^}]*\\}"), "") // remove {comments}
      .replace(Regex(";.*$"), "") // remove ;comments
      .replace(Regex("\\d+\\.\\.\\."), "") // remove 1...
      .replace(Regex("\\d+\\."), " ") // remove 1. 2.
      .replace("$", "")
      .replace("!", "")
      .replace("?", "")
      .replace("+", "")
      .replace("#", "")

    val rawTokens = cleanMovesText.split("\\s+".toRegex()).filter { it.isNotBlank() }

    var currentPos = Position.fromFen(Position.STARTING_FEN)
    val parsedMoves = mutableListOf<ParsedPgnMove>()
    var moveIndex = 1

    for (rawToken in rawTokens) {
      if (rawToken in listOf("1-0", "0-1", "1/2-1/2", "*")) break

      val resolvedMove = resolveSanMove(currentPos, rawToken)
      if (resolvedMove != null) {
        val color = currentPos.sideToMove
        currentPos = LegalMoveGenerator.makeMove(currentPos, resolvedMove)
        parsedMoves.add(
          ParsedPgnMove(
            san = rawToken,
            move = resolvedMove,
            positionAfter = currentPos,
            moveNumber = if (color == PieceColor.WHITE) moveIndex else moveIndex++,
            color = color
          )
        )
      }
    }

    return ParsedPgnGame(
      white = headers["White"] ?: "White",
      black = headers["Black"] ?: "Black",
      result = headers["Result"] ?: "*",
      event = headers["Event"] ?: "Game",
      date = headers["Date"] ?: "Unknown",
      moves = parsedMoves
    )
  }

  /**
   * Resolves a SAN string (e.g. "Nf3", "e4", "O-O", "exd5", "Qxd4", "Nbd7", "e8=Q")
   * against the legal moves of the given position.
   */
  fun resolveSanMove(position: Position, rawSan: String): Move? {
    val legalMoves = LegalMoveGenerator.generateLegalMoves(position)
    val clean = rawSan.replace("+", "").replace("#", "").replace("!", "").replace("?", "").trim()

    // 1. Castling
    if (clean == "O-O" || clean == "0-0") {
      return legalMoves.find { it.from.file == 4 && it.to.file == 6 }
    }
    if (clean == "O-O-O" || clean == "0-0-0") {
      return legalMoves.find { it.from.file == 4 && it.to.file == 2 }
    }

    // 2. Promotion check
    var targetPromo: PieceType? = null
    var moveBody = clean
    if (clean.contains("=")) {
      val parts = clean.split("=")
      moveBody = parts[0]
      targetPromo = when (parts.getOrNull(1)?.firstOrNull()?.uppercaseChar()) {
        'Q' -> PieceType.QUEEN
        'R' -> PieceType.ROOK
        'B' -> PieceType.BISHOP
        'N' -> PieceType.KNIGHT
        else -> null
      }
    }

    // Target square is always the last 2 characters of moveBody
    if (moveBody.length < 2) return null
    val destSquareStr = moveBody.takeLast(2)
    val destSquare = try {
      Square.fromAlgebraic(destSquareStr)
    } catch (_: Exception) {
      return null
    }

    // Determine moving piece type
    val firstChar = moveBody[0]
    val pieceType = if (firstChar.isUpperCase()) {
      when (firstChar) {
        'N' -> PieceType.KNIGHT
        'B' -> PieceType.BISHOP
        'R' -> PieceType.ROOK
        'Q' -> PieceType.QUEEN
        'K' -> PieceType.KING
        else -> PieceType.PAWN
      }
    } else {
      PieceType.PAWN
    }

    // Disambiguation info (between piece prefix and destination)
    val prefixEnd = if (firstChar.isUpperCase()) 1 else 0
    val suffixStart = moveBody.length - 2
    val disambiguation = if (suffixStart > prefixEnd) {
      moveBody.substring(prefixEnd, suffixStart).replace("x", "")
    } else ""

    val candidates = legalMoves.filter { move ->
      if (move.to != destSquare) return@filter false
      val piece = position.pieceAt(move.from) ?: return@filter false
      if (piece.type != pieceType) return@filter false
      if (targetPromo != null && move.promotion != targetPromo) return@filter false

      if (disambiguation.isNotEmpty()) {
        when (disambiguation.length) {
          1 -> {
            val char = disambiguation[0]
            if (char in 'a'..'h') {
              if (move.from.fileChar != char) return@filter false
            } else if (char in '1'..'8') {
              if (move.from.rankChar != char) return@filter false
            }
          }
          2 -> {
            if (move.from.algebraic != disambiguation) return@filter false
          }
        }
      }
      true
    }

    return candidates.firstOrNull()
  }
}
