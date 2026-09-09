package com.example.chess.engine

import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Move
import com.example.chess.core.Piece
import com.example.chess.core.PieceColor
import com.example.chess.core.PieceType
import com.example.chess.core.Position
import com.example.chess.core.Square
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Fast, pure-Kotlin chess evaluator and move selector.
 * Implements alpha-beta minimax search with piece-square positional tables (PST),
 * center control evaluation, pawn mobility, and calibrated ELO candidate selection.
 */
class LocalChessEngine : EngineClient {

  // Piece-Square Positional Tables (Values from White's perspective)
  private val PAWN_TABLE = intArrayOf(
    0,  0,  0,  0,  0,  0,  0,  0,
    50, 50, 50, 50, 50, 50, 50, 50,
    10, 10, 20, 30, 30, 20, 10, 10,
     5,  5, 10, 25, 25, 10,  5,  5,
     0,  0,  0, 20, 20,  0,  0,  0,
     5, -5,-10,  0,  0,-10, -5,  5,
     5, 10, 10,-20,-20, 10, 10,  5,
     0,  0,  0,  0,  0,  0,  0,  0
  )

  private val KNIGHT_TABLE = intArrayOf(
    -50,-40,-30,-30,-30,-30,-40,-50,
    -40,-20,  0,  0,  0,  0,-20,-40,
    -30,  0, 10, 15, 15, 10,  0,-30,
    -30,  5, 15, 20, 20, 15,  5,-30,
    -30,  0, 15, 20, 20, 15,  0,-30,
    -30,  5, 10, 15, 15, 10,  5,-30,
    -40,-20,  0,  5,  5,  0,-20,-40,
    -50,-40,-30,-30,-30,-30,-40,-50
  )

  private val BISHOP_TABLE = intArrayOf(
    -20,-10,-10,-10,-10,-10,-10,-20,
    -10,  0,  0,  0,  0,  0,  0,-10,
    -10,  0,  5, 10, 10,  5,  0,-10,
    -10,  5,  5, 10, 10,  5,  5,-10,
    -10,  0, 10, 10, 10, 10,  0,-10,
    -10, 10, 10, 10, 10, 10, 10,-10,
    -10,  5,  0,  0,  0,  0,  5,-10,
    -20,-10,-10,-10,-10,-10,-10,-20
  )

  private val ROOK_TABLE = intArrayOf(
     0,  0,  0,  0,  0,  0,  0,  0,
     5, 10, 10, 10, 10, 10, 10,  5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
     0,  0,  0,  5,  5,  0,  0,  0
  )

  private val QUEEN_TABLE = intArrayOf(
    -20,-10,-10, -5, -5,-10,-10,-20,
    -10,  0,  0,  0,  0,  0,  0,-10,
    -10,  0,  5,  5,  5,  5,  0,-10,
     -5,  0,  5,  5,  5,  5,  0, -5,
      0,  0,  5,  5,  5,  5,  0, -5,
    -10,  5,  5,  5,  5,  5,  0,-10,
    -10,  0,  5,  0,  0,  0,  0,-10,
    -20,-10,-10, -5, -5,-10,-10,-20
  )

  private val KING_MIDDLE_TABLE = intArrayOf(
    -30,-40,-40,-50,-50,-40,-40,-30,
    -30,-40,-40,-50,-50,-40,-40,-30,
    -30,-40,-40,-50,-50,-40,-40,-30,
    -30,-40,-40,-50,-50,-40,-40,-30,
    -20,-30,-30,-40,-40,-30,-30,-20,
    -10,-20,-20,-20,-20,-20,-20,-10,
     20, 20,  0,  0,  0,  0, 20, 20,
     20, 30, 10,  0,  0, 10, 30, 20
  )

  companion object {
    private val OPENING_BOOK: Map<String, List<String>> = mapOf(
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -" to listOf("e2e4", "d2d4", "c2c4", "g1f3"),
      "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3" to listOf("e7e5", "c7c5", "e7e6", "c7c6"),
      "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6" to listOf("g1f3", "f2f4", "d2d4", "b1c3"),
      "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq -" to listOf("b8c6", "g8f6", "d7d6"),
      "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq -" to listOf("f1b5", "f1c4", "d2d4"),
      "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6" to listOf("g1f3", "b1c3", "c2c3", "d2d4"),
      "rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq -" to listOf("d7d6", "b8c6", "e7e6"),
      "rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq d3" to listOf("d7d5", "g8f6", "e7e6"),
      "rnbqkbnr/ppp1pppp/8/3p4/3P4/8/PPP1PPPP/RNBQKBNR w KQkq d6" to listOf("c2c4", "g1f3", "e2e3", "c1f4"),
      "rnbqkbnr/ppp1pppp/8/3p4/2PP4/8/PP2PPPP/RNBQKBNR b KQkq c3" to listOf("e7e6", "c7c6", "d5c4"),
      "rnbqkb1r/pppppppp/5n2/8/3P4/8/PPP1PPPP/RNBQKBNR w KQkq -" to listOf("c2c4", "g1f3", "c1g5"),
      "rnbqkbnr/pppppppp/8/8/2P5/8/PP1PPPPP/RNBQKBNR b KQkq c3" to listOf("e7e5", "c7c5", "g8f6", "e7e6"),
      "rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq -" to listOf("d7d5", "g8f6", "c7c5")
    )
  }

  override suspend fun evaluatePosition(position: Position, depth: Int): Evaluation = withContext(Dispatchers.Default) {
    val score = minimax(position, depth, -30000, 30000, position.sideToMove == PieceColor.WHITE)
    Evaluation.cp(score)
  }

  override suspend fun selectMove(position: Position, level: TrainingLevel): Move = withContext(Dispatchers.Default) {
    val legalMoves = LegalMoveGenerator.generateLegalMoves(position)
    if (legalMoves.isEmpty()) error("No legal moves available in position")

    // Check opening book for instant, natural opening play
    if (level.elo >= 1000) {
      val fenKey = position.toFen().split(" ").take(4).joinToString(" ")
      val bookReplies = OPENING_BOOK[fenKey]
      if (!bookReplies.isNullOrEmpty()) {
        val matchingLegal = bookReplies.mapNotNull { uci -> legalMoves.find { it.uci == uci } }
        if (matchingLegal.isNotEmpty()) {
          return@withContext matchingLegal.random()
        }
      }
    }

    val isWhite = position.sideToMove == PieceColor.WHITE

    // Score all candidate legal moves accurately from current player's perspective
    val scoredMoves = legalMoves.map { move ->
      val nextPos = LegalMoveGenerator.makeMove(position, move)
      // If White moves, next position is Black to move (isMaximizing = false).
      // White wants to maximize evaluation from White's perspective.
      // If Black moves, next position is White to move (isMaximizing = true).
      // Black wants to minimize evaluation from White's perspective (maximize -eval).
      val nextEval = minimax(
        nextPos,
        (level.depth - 1).coerceAtLeast(0),
        -30000,
        30000,
        !isWhite
      )
      val playerPerspectiveScore = if (isWhite) nextEval else -nextEval
      ScoredMove(move, playerPerspectiveScore)
    }.sortedByDescending { it.score }

    // Human-like move selection calibrated to training level
    val candidatePoolSize = min(level.maxCandidatePool, scoredMoves.size)
    val shouldBlunder = Random.nextFloat() < level.blunderProbability

    if (!shouldBlunder || candidatePoolSize <= 1) {
      scoredMoves.first().move
    } else {
      val chosenIdx = Random.nextInt(0, candidatePoolSize)
      scoredMoves[chosenIdx].move
    }
  }

  private fun orderMoves(position: Position, moves: List<Move>): List<Move> {
    return moves.sortedByDescending { move ->
      var score = 0
      val targetPiece = position.pieceAt(move.to)
      val movingPiece = position.pieceAt(move.from)
      if (targetPiece != null) {
        // MVV-LVA: Most Valuable Victim - Least Valuable Attacker
        score += (targetPiece.type.value * 10) - (movingPiece?.type?.value ?: 0)
      } else if (move.isEnPassant) {
        score += 900
      }
      if (move.promotion != null) {
        score += move.promotion.value
      }
      score
    }
  }

  private fun quiescence(
    position: Position,
    alpha: Int,
    beta: Int,
    isMaximizing: Boolean
  ): Int {
    val standPat = evaluateStatic(position)
    var curAlpha = alpha
    var curBeta = beta

    if (isMaximizing) {
      if (standPat >= curBeta) return curBeta
      if (standPat > curAlpha) curAlpha = standPat

      val legalMoves = LegalMoveGenerator.generateLegalMoves(position)
      val captures = legalMoves.filter { position.pieceAt(it.to) != null || it.isEnPassant }
      if (captures.isEmpty()) return standPat

      for (move in orderMoves(position, captures)) {
        val nextPos = LegalMoveGenerator.makeMove(position, move)
        val score = quiescence(nextPos, curAlpha, curBeta, false)
        if (score >= curBeta) return curBeta
        if (score > curAlpha) curAlpha = score
      }
      return curAlpha
    } else {
      if (standPat <= curAlpha) return curAlpha
      if (standPat < curBeta) curBeta = standPat

      val legalMoves = LegalMoveGenerator.generateLegalMoves(position)
      val captures = legalMoves.filter { position.pieceAt(it.to) != null || it.isEnPassant }
      if (captures.isEmpty()) return standPat

      for (move in orderMoves(position, captures)) {
        val nextPos = LegalMoveGenerator.makeMove(position, move)
        val score = quiescence(nextPos, curAlpha, curBeta, true)
        if (score <= curAlpha) return curAlpha
        if (score < curBeta) curBeta = score
      }
      return curBeta
    }
  }

  private fun minimax(
    position: Position,
    depth: Int,
    alpha: Int,
    beta: Int,
    isMaximizing: Boolean
  ): Int {
    if (depth <= 0) {
      return quiescence(position, alpha, beta, isMaximizing)
    }

    val rawMoves = LegalMoveGenerator.generateLegalMoves(position)
    if (rawMoves.isEmpty()) {
      return if (LegalMoveGenerator.isKingInCheck(position, position.sideToMove)) {
        // Checkmate! Favors faster checkmates
        if (isMaximizing) -20000 - depth else 20000 + depth
      } else {
        0 // Stalemate
      }
    }

    val moves = orderMoves(position, rawMoves)
    var currentAlpha = alpha
    var currentBeta = beta

    if (isMaximizing) {
      var maxEval = -30000
      for (move in moves) {
        val nextPos = LegalMoveGenerator.makeMove(position, move)
        val evaluation = minimax(nextPos, depth - 1, currentAlpha, currentBeta, false)
        maxEval = max(maxEval, evaluation)
        currentAlpha = max(currentAlpha, evaluation)
        if (currentBeta <= currentAlpha) break
      }
      return maxEval
    } else {
      var minEval = 30000
      for (move in moves) {
        val nextPos = LegalMoveGenerator.makeMove(position, move)
        val evaluation = minimax(nextPos, depth - 1, currentAlpha, currentBeta, true)
        minEval = min(minEval, evaluation)
        currentBeta = min(currentBeta, evaluation)
        if (currentBeta <= currentAlpha) break
      }
      return minEval
    }
  }

  /**
   * Static heuristic evaluation of position in centipawns (from White's perspective)
   */
  fun evaluateStatic(position: Position): Int {
    var score = 0
    for (i in 0 until 64) {
      val piece = position.squares[i] ?: continue
      val sq = Square(i)
      val pValue = piece.type.value
      val pstScore = getPstScore(piece, sq)

      if (piece.color == PieceColor.WHITE) {
        score += (pValue + pstScore)
      } else {
        score -= (pValue + pstScore)
      }
    }
    return score
  }

  private fun getPstScore(piece: Piece, square: Square): Int {
    val rank = if (piece.color == PieceColor.WHITE) square.rank else 7 - square.rank
    val index = (7 - rank) * 8 + square.file

    return when (piece.type) {
      PieceType.PAWN -> PAWN_TABLE[index]
      PieceType.KNIGHT -> KNIGHT_TABLE[index]
      PieceType.BISHOP -> BISHOP_TABLE[index]
      PieceType.ROOK -> ROOK_TABLE[index]
      PieceType.QUEEN -> QUEEN_TABLE[index]
      PieceType.KING -> KING_MIDDLE_TABLE[index]
    }
  }
}
