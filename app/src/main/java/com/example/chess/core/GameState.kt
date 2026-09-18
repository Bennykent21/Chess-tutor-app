package com.example.chess.core

/**
 * History-aware game state.
 *
 * [Position] is immutable and represents one board state. [GameState] adds
 * the move-history information required for repetition and move-count rules.
 */
class GameState private constructor(
  val position: Position,
  private val repetitionCounts: Map<String, Int>
) {

  constructor(position: Position = Position.initial()) : this(
    position = position,
    repetitionCounts = mapOf(position.repetitionKey() to 1)
  )

  /** Number of times the current position has occurred in this game. */
  val currentPositionOccurrences: Int
    get() = repetitionCounts[position.repetitionKey()] ?: 0

  /** Threefold repetition is a claimable draw, not an automatic game end. */
  val canClaimThreefoldRepetition: Boolean
    get() = currentPositionOccurrences >= 3

  /** Fivefold repetition is an automatic draw. */
  val isFivefoldRepetition: Boolean
    get() = currentPositionOccurrences >= 5

  /** The 50-move rule is claimable after 100 half-moves. */
  val canClaimFiftyMoveRule: Boolean
    get() = position.halfmoveClock >= 100

  /** The 75-move rule is automatic after 150 half-moves. */
  val isSeventyFiveMoveDraw: Boolean
    get() = position.halfmoveClock >= 150

  /** History-aware game status. */
  val status: GameStatus
    get() {
      val legalMoves = LegalMoveGenerator.generateLegalMoves(position)
      val inCheck = LegalMoveGenerator.isKingInCheck(position, position.sideToMove)

      // Checkmate takes precedence over the automatic 75-move rule.
      if (legalMoves.isEmpty()) {
        return if (inCheck) GameStatus.CHECKMATE else GameStatus.STALEMATE
      }

      if (isFivefoldRepetition) return GameStatus.DRAW_FIVEFOLD_REPETITION
      if (isSeventyFiveMoveDraw) return GameStatus.DRAW_75_MOVES
      if (LegalMoveGenerator.isInsufficientMaterial(position)) {
        return GameStatus.DRAW_INSUFFICIENT_MATERIAL
      }

      // These are surfaced for the UI/coach layer but do not make isOver true.
      if (canClaimThreefoldRepetition) return GameStatus.DRAW_THREEFOLD_REPETITION
      if (canClaimFiftyMoveRule) return GameStatus.DRAW_50_MOVES
      if (inCheck) return GameStatus.CHECK

      return GameStatus.IN_PROGRESS
    }

  /**
   * Whether the game has ended automatically. Claimable threefold/50-move
   * draws do not end the game until a player actually claims them.
   */
  val isOver: Boolean
    get() = when (status) {
      GameStatus.CHECKMATE,
      GameStatus.STALEMATE,
      GameStatus.DRAW_INSUFFICIENT_MATERIAL,
      GameStatus.DRAW_FIVEFOLD_REPETITION,
      GameStatus.DRAW_75_MOVES -> true
      else -> false
    }

  /**
   * Plays a legal move and returns a new history-aware state.
   * The current GameState itself is not mutated.
   */
  fun play(move: Move): GameState {
    val legalMove = LegalMoveGenerator.generateLegalMoves(position)
      .firstOrNull { it.uci == move.uci }
      ?: throw IllegalArgumentException("Illegal move ${move.uci}")

    val nextPosition = LegalMoveGenerator.makeMove(position, legalMove)
    val key = nextPosition.repetitionKey()
    val nextCounts = repetitionCounts.toMutableMap()
    nextCounts[key] = (nextCounts[key] ?: 0) + 1
    return GameState(nextPosition, nextCounts.toMap())
  }
}
