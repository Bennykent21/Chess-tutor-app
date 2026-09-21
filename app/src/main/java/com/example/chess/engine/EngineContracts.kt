package com.example.chess.engine

import com.example.chess.core.Move
import com.example.chess.core.PieceColor
import com.example.chess.core.Position

data class Evaluation(
  val centipawns: Int? = null,
  val mateInMoves: Int? = null
) {
  val isMate: Boolean get() = mateInMoves != null

  fun format(): String {
    val mate = mateInMoves
    if (mate != null) {
      return if (mate > 0) "M$mate" else "-M${kotlin.math.abs(mate)}"
    }
    val cp = centipawns ?: 0
    val pawns = cp / 100.0
    return if (pawns > 0) "+${String.format("%.1f", pawns)}" else String.format("%.1f", pawns)
  }

  fun winningPercentageWhite(): Float {
    val mate = mateInMoves
    if (mate != null) return if (mate > 0) 0.98f else 0.02f
    val cp = centipawns ?: 0
    val exponent = -cp.toDouble() / 400.0
    val winRate = 1.0 / (1.0 + Math.pow(10.0, exponent))
    return winRate.toFloat().coerceIn(0.04f, 0.96f)
  }

  fun scoreForSide(color: PieceColor): Float {
    val mate = mateInMoves
    if (mate != null) {
      val base = if (mate > 0) 10000f else -10000f
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

data class StockfishProfile(
  val elo: Int,
  val title: String,
  val category: String,
  val depth: Int,
  val blunderProbability: Float,
  val maxCandidatePool: Int
) {
  companion object {
    val PRESETS: List<StockfishProfile> = BotStrength.presets.map { preset ->
      StockfishProfile(
        elo = preset.rating,
        title = "${preset.name} (${preset.key})",
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
    }

    fun forElo(elo: Int): StockfishProfile {
      val clamped = elo.coerceIn(250, 3200)
      return PRESETS.minByOrNull { profile -> kotlin.math.abs(profile.elo - clamped) }
        ?: PRESETS.first()
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

enum class TrainingLevel(
  val elo: Int,
  val title: String,
  val description: String,
  val depth: Int,
  val blunderProbability: Float,
  val maxCandidatePool: Int
) {
  BEGINNER_250(
    elo = 250,
    title = "Level 1 (250)",
    description = "Frequent simple mistakes",
    depth = 1,
    blunderProbability = 0.70f,
    maxCandidatePool = 8
  ),
  CASUAL_600(
    elo = 600,
    title = "Level 2 (600)",
    description = "Misses many basic tactics",
    depth = 1,
    blunderProbability = 0.45f,
    maxCandidatePool = 6
  ),
  INTERMEDIATE_1000(
    elo = 1000,
    title = "Level 3 (1000)",
    description = "Understands fundamentals",
    depth = 2,
    blunderProbability = 0.28f,
    maxCandidatePool = 4
  ),
  CLUB_1300(
    elo = 1300,
    title = "Level 4 (1300)",
    description = "Solid club-level challenge",
    depth = 2,
    blunderProbability = 0.15f,
    maxCandidatePool = 3
  ),
  ADVANCED_1600(
    elo = 1600,
    title = "Level 5 (1600)",
    description = "Strong practical play",
    depth = 3,
    blunderProbability = 0.08f,
    maxCandidatePool = 2
  ),
  EXPERT_2000(
    elo = 2000,
    title = "Level 6 (2000)",
    description = "Punishes most inaccuracies",
    depth = 4,
    blunderProbability = 0.02f,
    maxCandidatePool = 1
  ),
  MASTER_2400(
    elo = 2400,
    title = "Level 7 (2400)",
    description = "Very strong tactical play",
    depth = 4,
    blunderProbability = 0.0f,
    maxCandidatePool = 1
  ),
  GRANDMASTER_3200(
    elo = 3200,
    title = "Level 8 (3200)",
    description = "Maximum preset strength",
    depth = 5,
    blunderProbability = 0.0f,
    maxCandidatePool = 1
  );

  companion object {
    fun forElo(elo: Int): TrainingLevel {
      val clamped = elo.coerceIn(250, 3200)
      return entries.minByOrNull { kotlin.math.abs(it.elo - clamped) } ?: BEGINNER_250
    }
  }
}

data class ScoredMove(
  val move: Move,
  val score: Int
)

interface EngineClient {
  suspend fun evaluatePosition(position: Position, depth: Int = 4): Evaluation
  suspend fun selectMove(position: Position, level: TrainingLevel): Move
}
