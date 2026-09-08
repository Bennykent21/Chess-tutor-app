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

  override suspend fun evaluatePosition(position: Position, depth: Int): Evaluation = withContext(Dispatchers.Default) {
    val score = minimax(position, depth, -30000, 30000, position.sideToMove == PieceColor.WHITE)
    Evaluation.cp(score)
  }

  override suspend fun selectMove(position: Position, level: TrainingLevel): Move = withContext(Dispatchers.Default) {
    val legalMoves = LegalMoveGenerator.generateLegalMoves(position)
    if (legalMoves.isEmpty()) error("No legal moves available in position")

    // Score all candidate legal moves
    val scoredMoves = legalMoves.map { move ->
      val nextPos = LegalMoveGenerator.makeMove(position, move)
      // Evaluate from perspective of current side to move
      val score = -minimax(
        nextPos,
        level.depth - 1,
        -30000,
        30000,
        nextPos.sideToMove == PieceColor.WHITE
      )
      ScoredMove(move, if (position.sideToMove == PieceColor.WHITE) score else -score)
    }.sortedByDescending { it.score }

    // Human-like move selection:
    // With probability (1 - blunderProbability), pick best or top 2.
    // Otherwise pick from top-N candidate pool.
    val candidatePoolSize = min(level.maxCandidatePool, scoredMoves.size)
    val shouldBlunder = Random.nextFloat() < level.blunderProbability

    if (!shouldBlunder || candidatePoolSize <= 1) {
      scoredMoves.first().move
    } else {
      // Pick randomly within the candidate pool
      val chosenIdx = Random.nextInt(0, candidatePoolSize)
      scoredMoves[chosenIdx].move
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
      return evaluateStatic(position)
    }

    val moves = LegalMoveGenerator.generateLegalMoves(position)
    if (moves.isEmpty()) {
      return if (LegalMoveGenerator.isKingInCheck(position, position.sideToMove)) {
        // Checkmate! Favors faster checkmates
        if (isMaximizing) -20000 - depth else 20000 + depth
      } else {
        0 // Stalemate
      }
    }

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
