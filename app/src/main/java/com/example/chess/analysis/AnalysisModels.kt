package com.example.chess.analysis

import com.example.chess.core.Move
import com.example.chess.core.Piece
import com.example.chess.core.PieceColor
import com.example.chess.core.PieceType
import com.example.chess.core.Position
import com.example.chess.core.Square
import com.example.chess.engine.Evaluation

/**
 * Standard classification of a chess move based on evaluation change
 */
enum class MoveClassification(val label: String, val badgeText: String) {
  BEST_MOVE("Best Move", "★ Best"),
  EXCELLENT("Excellent", "✓ Good"),
  INACCURACY("Inaccuracy", "?! Inaccuracy"),
  MISTAKE("Mistake", "? Mistake"),
  BLUNDER("Blunder", "?? Blunder"),
  BOOK("Book Move", "📖 Book")
}

/**
 * Extracted structural & tactical features of a position.
 */
data class PositionFeatures(
  val whiteMaterialValue: Int,
  val blackMaterialValue: Int,
  val hangingPieces: List<Pair<Square, Piece>>,
  val attackedCenterSquares: Map<Square, Int>, // count of attacks on d4, e4, d5, e5
  val isKingExposed: Boolean,
  val materialDifferentialPawns: Float // e.g. +2.0 = White up 2 pawns
)

/**
 * Result of evaluating a move within a game.
 */
data class AnalyzedMove(
  val moveIndex: Int,
  val move: Move,
  val playerColor: PieceColor,
  val positionBefore: Position,
  val positionAfter: Position,
  val evalBefore: Evaluation,
  val evalAfter: Evaluation,
  val bestMove: Move,
  val classification: MoveClassification,
  val explanation: String
)

object BlunderClassifier {

  /**
   * Classifies a move based on centipawn loss and game state.
   */
  fun classify(
    playerColor: PieceColor,
    evalBefore: Evaluation,
    evalAfter: Evaluation,
    isBestMove: Boolean
  ): MoveClassification {
    if (isBestMove) return MoveClassification.BEST_MOVE

    val scoreBefore = evalBefore.scoreForSide(playerColor)
    val scoreAfter = evalAfter.scoreForSide(playerColor)
    val delta = scoreBefore - scoreAfter // positive means player lost evaluation

    // Decisive advantage filter: If player was winning by +7.0 and is now +5.0, it's not a blunder
    if (scoreBefore >= 6.0f && scoreAfter >= 4.5f) {
      return MoveClassification.EXCELLENT
    }

    return when {
      delta <= 0.15f -> MoveClassification.BEST_MOVE
      delta <= 0.35f -> MoveClassification.EXCELLENT
      delta <= 0.80f -> MoveClassification.INACCURACY
      delta <= 2.00f -> MoveClassification.MISTAKE
      else -> MoveClassification.BLUNDER
    }
  }

  /**
   * Generates a pedagogical explanation for a blunder or mistake based on concrete features.
   */
  fun generateExplanation(
    playerColor: PieceColor,
    move: Move,
    positionBefore: Position,
    positionAfter: Position,
    classification: MoveClassification,
    bestAlternative: Move
  ): String {
    val movedPiece = positionBefore.pieceAt(move.from)
    val capturedPiece = positionBefore.pieceAt(move.to)

    // Check if player left a piece hanging
    val enemyColor = playerColor.opposite()
    val hangingPiece = positionAfter.pieceAt(move.to)

    return when (classification) {
      MoveClassification.BEST_MOVE, MoveClassification.BOOK -> {
        "Maintains piece harmony, activates ${movedPiece?.type?.name?.lowercase() ?: "piece"}, and controls critical central squares."
      }
      MoveClassification.INACCURACY -> {
        "Slightly passive continuation. Playing ${bestAlternative.uci} would have applied more central pressure."
      }
      MoveClassification.MISTAKE -> {
        "Gives up momentum. Allowed opponent to gain active piece coordination. Better was ${bestAlternative.uci}."
      }
      MoveClassification.BLUNDER -> {
        if (capturedPiece == null && hangingPiece != null) {
          "Leaves the ${movedPiece?.type?.name?.lowercase()} on ${move.to.algebraic} vulnerable without sufficient tactical defense. Better was ${bestAlternative.uci}."
        } else {
          "Severe tactical concession. Swings the advantage directly to your opponent. Strongest move was ${bestAlternative.uci}."
        }
      }
      MoveClassification.EXCELLENT -> "Solid move preserving your positional advantages."
    }
  }
}
