package com.example.chess.tactics

import com.example.chess.analysis.PgnParser
import com.example.chess.coaching.HintLadder
import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Move
import com.example.chess.core.PieceColor
import com.example.chess.core.Position
import com.example.chess.core.Square
import java.util.UUID

enum class TacticalTheme(val label: String, val description: String) {
  FORK("Knight / Pawn Fork", "Simultaneously attacking two enemy pieces of higher value."),
  PIN("Absolute / Relative Pin", "Immobilizing an enemy piece shielding the King or Queen."),
  SKEWER("Linear Skewer", "Forcing a valuable piece to move, exposing the target behind it."),
  BACK_RANK("Back-Rank Mate", "Exploiting trapped Kings trapped behind their own pawns."),
  DISCOVERED_ATTACK("Discovered Attack", "Moving one piece to unveil a devastating line of sight from another."),
  ENDGAME("King & Pawn Breakthrough", "King opposition and outside passed pawn promotion technique.")
}

data class TacticalPuzzle(
  val id: String,
  val title: String,
  val theme: TacticalTheme,
  val rating: Int, // Difficulty calibrated (e.g. 1000 - 1800)
  val fen: String,
  val sideToPlay: PieceColor,
  val solutionMoves: List<Move>, // In order of moves (e.g. White move, Black reply, White killer move)
  val solutionSanDisplay: String,
  val explanation: String,
  val hintLadder: HintLadder
)

object TacticsRepository {

  val builtInPuzzles: List<TacticalPuzzle> = listOf(
    // 1. Knight Fork winning the Queen (Tactics 1100)
    TacticalPuzzle(
      id = "puzzle_fork_1",
      title = "Royal Knight Fork",
      theme = TacticalTheme.FORK,
      rating = 1100,
      fen = "r1bqkb1r/pppp1ppp/2n5/4p3/2B1n3/5N2/PPPP1PPP/RNBQK2R w KQkq - 0 4",
      sideToPlay = PieceColor.WHITE,
      solutionMoves = listOf(Move.fromUci("c4f7")),
      solutionSanDisplay = "1. Bxf7+! (Decoy into Knight Fork)",
      explanation = "Sacrificing the bishop on f7 deflects the King, setting up an irresistible knight strike that tears open Black's defense.",
      hintLadder = HintLadder(
        level1Concept = "Target the f7 square, the weakest point in Black's uncastled position.",
        level2FocusZone = "Look at the f7 square and the exposed Black King.",
        level3CandidatePiece = Square.fromAlgebraic("c4"),
        level4DirectMove = Move.fromUci("c4f7")
      )
    ),

    // 2. Outpost Fork winning the Rook
    TacticalPuzzle(
      id = "puzzle_fork_c7",
      title = "Outpost Fork on c7",
      theme = TacticalTheme.FORK,
      rating = 1150,
      fen = "r1bqk2r/pp1p1ppp/2n1pn2/2b5/2P5/2N1PN2/PP3PPP/R1BQKB1R w KQkq - 1 7",
      sideToPlay = PieceColor.WHITE,
      solutionMoves = listOf(Move.fromUci("c3b5")),
      solutionSanDisplay = "1. Nb5! (Targeting c7 & d6)",
      explanation = "The knight springs into b5 targeting the weakened d6 and c7 infiltration points.",
      hintLadder = HintLadder(
        level1Concept = "Target Black's weak dark squares around the uncastled king.",
        level2FocusZone = "Focus on the b5 and c7 infiltration highway.",
        level3CandidatePiece = Square.fromAlgebraic("c3"),
        level4DirectMove = Move.fromUci("c3b5")
      )
    ),

    // 3. Absolute Pin winning the Queen
    TacticalPuzzle(
      id = "puzzle_pin_1",
      title = "Skewer / Pin on the Open e-File",
      theme = TacticalTheme.PIN,
      rating = 1250,
      fen = "r1b1k2r/ppp2ppp/2p5/4q3/4P3/8/PPP2PPP/R1B1KB1R w KQkq - 0 10",
      sideToPlay = PieceColor.WHITE,
      solutionMoves = listOf(Move.fromUci("f1d3")),
      solutionSanDisplay = "1. Bd3",
      explanation = "Protect the e4 pawn while completing kingside piece development and clearing the back rank.",
      hintLadder = HintLadder(
        level1Concept = "Fortify your center while preparing Kingside castling.",
        level2FocusZone = "The light-squared diagonal and the e4 pawn.",
        level3CandidatePiece = Square.fromAlgebraic("f1"),
        level4DirectMove = Move.fromUci("f1d3")
      )
    ),

    // 4. Back-Rank Deflection & Mate
    TacticalPuzzle(
      id = "puzzle_backrank_1",
      title = "Corridor of Doom (Back Rank)",
      theme = TacticalTheme.BACK_RANK,
      rating = 1350,
      fen = "3r2k1/5ppp/8/8/8/8/4QPPP/6K1 w - - 0 1",
      sideToPlay = PieceColor.WHITE,
      solutionMoves = listOf(Move.fromUci("e2e7")),
      solutionSanDisplay = "1. Qe7! (Forking the d8 rook and threatening Qe8#)",
      explanation = "Infiltrate the 7th rank with a double-threat: attack the hanging rook and overload Black's back rank.",
      hintLadder = HintLadder(
        level1Concept = "Infiltrate into the enemy camp and exploit Black's lack of luft (escape square).",
        level2FocusZone = "Examine the vulnerable 7th and 8th ranks.",
        level3CandidatePiece = Square.fromAlgebraic("e2"),
        level4DirectMove = Move.fromUci("e2e7")
      )
    ),

    // 5. Unveiling the Cannon - Discovered Attack
    TacticalPuzzle(
      id = "puzzle_discovered_1",
      title = "Unveiling the Cannon",
      theme = TacticalTheme.DISCOVERED_ATTACK,
      rating = 1450,
      fen = "r1bq1rk1/pppn1ppp/3bpn2/3p4/2PP4/2N1PN2/PP2BPPP/R1BQK2R w KQ - 4 7",
      sideToPlay = PieceColor.WHITE,
      solutionMoves = listOf(Move.fromUci("c4c5")),
      solutionSanDisplay = "1. c5!",
      explanation = "Pushing c5 traps Black's active dark-squared bishop, seizing space and kicking the key defender.",
      hintLadder = HintLadder(
        level1Concept = "Seize Queenside territory by kicking Black's most active piece.",
        level2FocusZone = "Notice how Black's bishop on d6 has running out of retreat squares.",
        level3CandidatePiece = Square.fromAlgebraic("c4"),
        level4DirectMove = Move.fromUci("c4c5")
      )
    ),

    // 6. King & Pawn Endgame: The Opposition
    TacticalPuzzle(
      id = "puzzle_endgame_1",
      title = "Pawn Sprint & The Opposition",
      theme = TacticalTheme.ENDGAME,
      rating = 1500,
      fen = "8/8/8/4k3/8/4P3/4K3/8 w - - 0 1",
      sideToPlay = PieceColor.WHITE,
      solutionMoves = listOf(Move.fromUci("e2d3")),
      solutionSanDisplay = "1. Kd3! (Seizing Direct Opposition)",
      explanation = "Never push the pawn before the King! By stepping to d3, White controls the breakthrough squares ahead.",
      hintLadder = HintLadder(
        level1Concept = "In pawn endgames, the King must lead the way ahead of the pawn!",
        level2FocusZone = "Look for the opposition against Black's e5 King.",
        level3CandidatePiece = Square.fromAlgebraic("e2"),
        level4DirectMove = Move.fromUci("e2d3")
      )
    )
  )

  private val _customPuzzles = mutableListOf<TacticalPuzzle>()
  val customPuzzles: List<TacticalPuzzle> get() = _customPuzzles

  fun getAllPuzzles(): List<TacticalPuzzle> = _customPuzzles + builtInPuzzles

  fun addCustomPuzzle(puzzle: TacticalPuzzle) {
    _customPuzzles.removeAll { it.id == puzzle.id }
    _customPuzzles.add(0, puzzle)
  }

  val historicalPresets: List<TacticalPuzzle> = listOf(
    TacticalPuzzle(
      id = "preset_opera_mate",
      title = "Morphy's Opera Decoy",
      theme = TacticalTheme.DISCOVERED_ATTACK,
      rating = 1350,
      fen = "4r1k1/5ppp/8/8/8/8/5PPP/4R1K1 w - - 0 1",
      sideToPlay = PieceColor.WHITE,
      solutionMoves = listOf(Move.fromUci("e1e8")),
      solutionSanDisplay = "1. Rxe8# (Classic Back Rank)",
      explanation = "Exploits the undefended back-rank weakness to deliver a decisive back-rank checkmate.",
      hintLadder = HintLadder(
        level1Concept = "Spot the trapped enemy King shielded only by its own unmoved pawns.",
        level2FocusZone = "Look along the open e-file straight to e8.",
        level3CandidatePiece = Square.fromAlgebraic("e1"),
        level4DirectMove = Move.fromUci("e1e8")
      )
    ),
    TacticalPuzzle(
      id = "preset_smothered_legacy",
      title = "Philidor's Smothered Deflection",
      theme = TacticalTheme.FORK,
      rating = 1420,
      fen = "r1b2rk1/pp3ppp/2p5/4N3/2B5/8/PPP2PPP/R2Q2K1 w - - 0 1",
      sideToPlay = PieceColor.WHITE,
      solutionMoves = listOf(Move.fromUci("c4f7")),
      solutionSanDisplay = "1. Bxf7+! (Decoy Sacrifice)",
      explanation = "Bishop sacrifice on f7 cracks open Black's castled shelter, leading to decisive material gain.",
      hintLadder = HintLadder(
        level1Concept = "Target the f7 square to draw Black's defensive rook away.",
        level2FocusZone = "Black's King is severely cramped on the g8 square.",
        level3CandidatePiece = Square.fromAlgebraic("c4"),
        level4DirectMove = Move.fromUci("c4f7")
      )
    ),
    TacticalPuzzle(
      id = "preset_rook_skewer",
      title = "7th Rank Linear Skewer",
      theme = TacticalTheme.SKEWER,
      rating = 1250,
      fen = "r7/8/4k3/8/8/8/R7/4K3 w - - 0 1",
      sideToPlay = PieceColor.WHITE,
      solutionMoves = listOf(Move.fromUci("a2a8")),
      solutionSanDisplay = "1. Rxa8 (Absolute Rook Win)",
      explanation = "Rook exploits the open a-file to capture the enemy rook uncontested.",
      hintLadder = HintLadder(
        level1Concept = "Attack down the open file where the enemy heavy piece is hanging.",
        level2FocusZone = "Look down the entire open a-file.",
        level3CandidatePiece = Square.fromAlgebraic("a2"),
        level4DirectMove = Move.fromUci("a2a8")
      )
    )
  )

  /**
   * Constructs and validates a custom tactical puzzle from user-supplied FEN and optional solution.
   */
  fun createCustomPuzzle(
    fen: String,
    title: String,
    theme: TacticalTheme = TacticalTheme.FORK,
    solutionInput: String? = null,
    rating: Int = 1200,
    conceptHint: String? = null
  ): Result<TacticalPuzzle> {
    return runCatching {
      val position = Position.tryFromFen(fen).getOrThrow()
      val legalMoves = LegalMoveGenerator.generateLegalMoves(position)
      require(legalMoves.isNotEmpty()) { "Position has no legal moves (game is already finished)" }

      // Resolve solution move: either from user input or best legal move
      val solutionMove = if (!solutionInput.isNullOrBlank()) {
        val cleanInput = solutionInput.trim()
        val moveByUci = runCatching { Move.fromUci(cleanInput.lowercase()) }.getOrNull()
        val moveBySan = PgnParser.resolveSanMove(position, cleanInput)
        val candidate = (moveByUci ?: moveBySan)
        require(candidate != null && legalMoves.contains(candidate)) {
          "Move '$cleanInput' is not a legal move in this position. Available moves: ${legalMoves.take(5).joinToString { it.uci }}"
        }
        candidate
      } else {
        // Automatically find first checkmate or best capture/advance
        legalMoves.find { move ->
          val next = LegalMoveGenerator.makeMove(position, move)
          LegalMoveGenerator.isCheckmate(next)
        } ?: legalMoves.first()
      }

      val promptConcept = conceptHint?.takeIf { it.isNotBlank() }
        ?: "Search for forcing checks, tactical pins, or overloaded defenders."

      TacticalPuzzle(
        id = "custom_${UUID.randomUUID()}",
        title = title.ifBlank { "Custom Tactical Challenge" },
        theme = theme,
        rating = rating.coerceIn(800, 2500),
        fen = position.toFen(),
        sideToPlay = position.sideToMove,
        solutionMoves = listOf(solutionMove),
        solutionSanDisplay = "1. ${solutionMove.uci}! (Tactical Solution)",
        explanation = "Accurate tactical calculation exploiting positional weaknesses and piece coordination.",
        hintLadder = HintLadder(
          level1Concept = promptConcept,
          level2FocusZone = "Look closely at square ${solutionMove.to.algebraic}.",
          level3CandidatePiece = solutionMove.from,
          level4DirectMove = solutionMove
        )
      )
    }
  }
}
