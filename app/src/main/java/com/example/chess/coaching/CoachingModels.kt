package com.example.chess.coaching

import com.example.chess.core.Move
import com.example.chess.core.PieceColor
import com.example.chess.core.Position
import com.example.chess.core.Square

/**
 * 4-Level Hint Ladder:
 * Level 1: Concept ("What changed in the position?")
 * Level 2: Focus Zone ("Look closely at the f7 square")
 * Level 3: Candidate Piece ("Consider activating your light-squared Bishop")
 * Level 4: Concrete Move ("Bxf7+ wins the f-pawn and exposes Black's king")
 */
data class HintLadder(
  val level1Concept: String,
  val level2FocusZone: String,
  val level3CandidatePiece: Square,
  val level4DirectMove: Move
)

/**
 * The single source of truth for all coaching interactions.
 */
data class CoachingState(
  val title: String = "Lesson",
  val conceptTitle: String = "Center Control",
  val explanationText: String = "",
  val position: Position = Position.initial(),
  val sideToPlay: PieceColor = PieceColor.WHITE,
  val targetMove: Move? = null,
  val recommendedArrow: Pair<Square, Square>? = null,
  val threatArrow: Pair<Square, Square>? = null,
  val highlightedSquares: Set<Square> = emptySet(),
  val hintLevel: Int = 0, // 0 = none, 1..4
  val currentHintText: String? = null,
  val hintLadder: HintLadder? = null,
  val isCompleted: Boolean = false,
  val canPracticeInArena: Boolean = true,
  val practiceFen: String? = null
)

/**
 * Curriculum Lesson Model
 */
data class CurriculumLesson(
  val id: String,
  val title: String,
  val ecoCode: String,
  val category: String, // "OPENING", "MIDDLEGAME", "ENDGAME"
  val difficultyLevel: String, // "BEGINNER", "INTERMEDIATE", "ADVANCED"
  val summary: String,
  val keyTakeaway: String,
  val steps: List<LessonStep>
)

data class LessonStep(
  val stepIndex: Int,
  val startingFen: String,
  val playedMove: Move,
  val conceptTitle: String,
  val explanation: String,
  val hintLadder: HintLadder,
  val recommendedArrow: Pair<Square, Square>? = null,
  val highlightedSquares: List<Square> = emptyList()
)
