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
   * Returns a 0.0 to 1.0 fraction representing White's advantage for the Eval Bar.
   * 0.5 = dead equal, 1.0 = White crushing/mate, 0.0 = Black crushing/mate.
   */
  fun winningPercentageWhite(): Float {
    if (mateInMoves != null) {
      return if (mateInMoves > 0) 0.98f else 0.02f
    }
    val cp = centipawns ?: 0
    // Standard chess engine sigmoid: 1 / (1 + 10^(-cp / 400))
    val exponent = -cp.toDouble() / 400.0
    val winRate = 1.0 / (1.0 + Math.pow(10.0, exponent))
    return winRate.toFloat().coerceIn(0.04f, 0.96f)
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
 * Adjustable Stockfish Engine Profile with calibrated skill level,
 * depth, candidate move selection, and human-like inaccuracy chance.
 */
data class StockfishProfile(
  val elo: Int,
  val title: String,
  val category: String,
  val depth: Int,
  val blunderProbability: Float,
  val maxCandidatePool: Int
) {
  companion object {
    val PRESETS = BotStrength.presets.map { preset ->
      StockfishProfile(
        elo = preset.rating,
        title = "${preset.name} (${preset.key })",
        category = preset.key,
        depth = when {
          preset.rating <= 600 -> 1
          preset.rating <= 1300 -> 2
          preset.rating <= 1600 -> 3
          preset.rating <= 2400 -> 4
          else -> 5
        },
        blunderProbability = when {
          preset.rating <= 250 -> 0.70f
          preset.rating <= 600 -> 0.45f
          preset.rating <= 1000 -> 0.28f
          preset.rating <= 1300 -> 0.15f
          preset.rating <= 1600 -> 0.08f
          preset.rating <= 2000 -> 0.02f
          else -> 0.0f
        },
        maxCandidatePool = when {
          preset.rating <= 250 -> 8
          preset.rating <= 600 -> 6
          preset.rating <= 1000 -> 4
          preset.rating <= 1300 -> 3
          preset.rating <= 1600 -> 2
          else -> 1
        }
      )
    )

    fun forElo(elo: Int): StockfishProfile {
      val clamped = elo.coerceIn(250, 3200)
      return PRESETS.minByOrNull { kotlin.math.abs(it.elo - clamped) }
        ?: PRESETS[3]
    }
  }
}

enum class MoveQuality(val label: String, val badge: String) {
  BEST("Best Move", "★"),
  EXCELLENT("Excellent", "✦"),
  GOOD("Good", "✓"),
  INACCURACY("Inaccuracy", "?!"),
  MISTAKE("Mistake", "?"),
  BLUNDER("Blunder", "??")
}

data class MoveAnalysisResult(
  val quality: MoveQuality,
  val playedMove: Move,
  val bestMove: Move,
  val evalBefore: Evaluation,
  val evalAfter: Evaluation,
  val evalDiffCentipawns: Int,
  val explanation: String
)

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
