package com.example.chess.assessment

import com.example.chess.core.Move

data class AssessmentPosition(
  val id: String,
  val fen: String,
  val topic: String,
  val question: String,
  val bestMoveUci: String,
  val plausibleAlternativeUci: String,
  val bestMoveConcept: String,
  val alternativeRefutation: String,
  val ratingWeight: Int
)

object AssessmentRepository {
  val placementQuestions: List<AssessmentPosition> = listOf(
    AssessmentPosition(
      id = "assess_1_opening",
      fen = "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3",
      topic = "Opening Principle: Harmony & Development",
      question = "White to move. Black just played 2...Nc6 defending e5. What is the most principled classical developing move?",
      bestMoveUci = "f1c4", // Bc4 Italian Game
      plausibleAlternativeUci = "d1e2", // Qe2 blocks bishop
      bestMoveConcept = "Bc4 targets the vulnerable f7 square, develops a piece, and prepares kingside castling.",
      alternativeRefutation = "Qe2 blocks the f1 bishop from developing naturally and wastes queen tempo early.",
      ratingWeight = 200
    ),
    AssessmentPosition(
      id = "assess_2_tactics",
      fen = "r1b1k2r/pppp1ppp/2n5/4p3/2B1n3/5N2/PPPP1PPP/RNBQK2R w KQkq - 0 6",
      topic = "Tactical Punishment: Free Piece or Counter-attack",
      question = "Black's knight took e4. How does White regain initiative and capitalize on kingside control?",
      bestMoveUci = "c4f7", // Bxf7+ deflection / check
      plausibleAlternativeUci = "d2d3",
      bestMoveConcept = "Bxf7+ strips Black's king of castling rights immediately.",
      alternativeRefutation = "d3 is slow and allows Black's knight to comfortably retreat to f6 or c5.",
      ratingWeight = 250
    ),
    AssessmentPosition(
      id = "assess_3_defense",
      fen = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2N2N2/PPPP1PPP/R1BQK2R w KQkq - 4 5",
      topic = "Positional Safety: King Safety First",
      question = "All minor pieces are mobilized. What is White's top priority before launching central combat?",
      bestMoveUci = "e1g1", // O-O castling
      plausibleAlternativeUci = "a2a3",
      bestMoveConcept = "Castling connects the rooks, safeguards the king, and prepares central pawn breaks like d4.",
      alternativeRefutation = "a3 is a passive flank waste of time when king safety is unresolved in an open game.",
      ratingWeight = 200
    ),
    AssessmentPosition(
      id = "assess_4_fork",
      fen = "r1bqk2r/pppp1ppp/8/4n3/2B1P3/2N5/PPPP1PPP/R1BQK2R w KQkq - 0 7",
      topic = "Calculation: Defending Hanging Pieces",
      question = "Black knight on e5 attacks your bishop on c4. How do you save the bishop or counter-attack?",
      bestMoveUci = "c4b3",
      plausibleAlternativeUci = "c4f1",
      bestMoveConcept = "Bb3 preserves bishop pressure on the a2-g8 diagonal while retreating to safety.",
      alternativeRefutation = "Bf1 is overly passive and buries the bishop back onto the home square.",
      ratingWeight = 250
    ),
    AssessmentPosition(
      id = "assess_5_endgame",
      fen = "8/5pk1/6p1/7p/7P/5KP1/8/8 w - - 1 45",
      topic = "Endgame Mastery: The Principle of Opposition",
      question = "Equal pawn endgame. White to move. How does White secure king opposition and control key infiltration squares?",
      bestMoveUci = "f3e4", // Ke4 stepping forward
      plausibleAlternativeUci = "f3f2", // Kf2 backwards retreat
      bestMoveConcept = "Ke4 takes the key rank and forces Black's king to commit to one side of the board.",
      alternativeRefutation = "Kf2 surrenders space and lets Black's king advance into White's territory.",
      ratingWeight = 300
    )
  )

  fun calculateRating(correctIndices: Set<Int>): Int {
    var score = 800
    correctIndices.forEach { idx ->
      score += placementQuestions.getOrNull(idx)?.ratingWeight ?: 150
    }
    return score.coerceIn(800, 2000)
  }
}
