package com.example.chess.engine

import com.example.chess.core.Move
import com.example.chess.core.PieceColor
import com.example.chess.core.Position

/**
 * Engine Evaluation representing either Centipawns (+150 = +1.50 pawns for White)
 * or Mate in N moves (e.g. +3 for White mate in 3, -2 for Black mate in 2).
 */
data class Evaluation(
  val centipawns: Int? = null,
  val mateInMoves: Int? = null
) {
  val isMate: Boolean get() = mateInMoves != null

  /**
   * Returns formatted human string (e.g. "+1.4", "-0.8", "M2", "-M4")
   */
  fun format(): String {
    if (mateInMoves != null) {
      return if (mateInMoves > 0) "M$mateInMoves" else "-M${kotlin.math.abs(mateInMoves)}"
    }
    val cp = centipawns ?: 0
    val pawns = cp / 100.0
    return if (pawns > 0) "+${String.format("%.1f", pawns)}" else String.format("%.1f", pawns)
  }

  /**
   * Returns evaluation score relative to active player
   */
  fun scoreForSide(color: PieceColor): Float {
    if (mateInMoves != null) {
      val base = if (mateInMoves > 0) 10000f else -10000f
      return if (color == PieceColor.WHITE) base else -base
    }
    val cp = centipawns ?: 0
    val raw = cp / 100f
    return if (color == PieceColor.WHITE) raw else -raw
  }

  companion object {
    val EVEN = Evaluation(centipawns = 0)
    fun cp(value: Int) = Evaluation(centipawns = value)
    fun mate(moves: Int) = Evaluation(mateInMoves = moves)
  }
}

/**
 * Training Levels with calibrated ELO ratings and top-N move probability distribution.
 */
enum class TrainingLevel(
  val elo: Int,
  val title: String,
  val description: String,
  val depth: Int,
  val blunderProbability: Float, // chance of picking a sub-optimal move
  val maxCandidatePool: Int
) {
  BEGINNER_800(
    elo = 800,
    title = "Level 1 (800)",
    description = "Casual learner, misses simple hanging pieces and tactical forks",
    depth = 1,
    blunderProbability = 0.45f,
    maxCandidatePool = 5
  ),
  CASUAL_1000(
    elo = 1000,
    title = "Level 2 (1000)",
    description = "Understands piece values, but leaves occasional tactical openings",
    depth = 2,
    blunderProbability = 0.30f,
    maxCandidatePool = 4
  ),
  INTERMEDIATE_1200(
    elo = 1200,
    title = "Level 3 (1200)",
    description = "Solid fundamentals, struggles with pawn structures and king safety",
    depth = 2,
    blunderProbability = 0.16f,
    maxCandidatePool = 3
  ),
  CLUB_1400(
    elo = 1400,
    title = "Level 4 (1400)",
    description = "Disciplined tactical vision, solid opening development",
    depth = 3,
    blunderProbability = 0.08f,
    maxCandidatePool = 2
  ),
  ADVANCED_1600(
    elo = 1600,
    title = "Level 5 (1600)",
    description = "Sharp attacking calculation, punishes structural mistakes quickly",
    depth = 3,
    blunderProbability = 0.03f,
    maxCandidatePool = 2
  ),
  EXPERT_1800(
    elo = 1800,
    title = "Level 6 (1800)",
    description = "Positional mastery, sharp tactical vision with quiescence calculation",
    depth = 4,
    blunderProbability = 0.0f,
    maxCandidatePool = 1
  )
}

data class ScoredMove(
  val move: Move,
  val score: Int
)

interface EngineClient {
  suspend fun evaluatePosition(position: Position, depth: Int = 4): Evaluation
  suspend fun selectMove(position: Position, level: TrainingLevel): Move
}
