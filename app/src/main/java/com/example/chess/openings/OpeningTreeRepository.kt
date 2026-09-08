package com.example.chess.openings

import com.example.chess.core.Move
import com.example.chess.core.Position

data class OpeningBranch(
  val moveUci: String,
  val moveSan: String,
  val resultingFen: String,
  val openingName: String,
  val ecoCode: String,
  val whiteWinPct: Int,
  val drawPct: Int,
  val blackWinPct: Int,
  val coachExplanation: String,
  val strategicPointers: List<String>
)

object OpeningTreeRepository {
  // Tree indexed by normalized FEN (without halfmove/fullmove clocks)
  private val tree: Map<String, List<OpeningBranch>> = mapOf(
    // Initial Position
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -" to listOf(
      OpeningBranch(
        moveUci = "e2e4",
        moveSan = "e4",
        resultingFen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3",
        openingName = "King's Pawn Opening",
        ecoCode = "B00/C00",
        whiteWinPct = 38,
        drawPct = 32,
        blackWinPct = 30,
        coachExplanation = "The most popular opening move. Seizes central real estate on d5/f5 and liberates both Queen and light-squared Bishop.",
        strategicPointers = listOf("Fosters open tactical games", "Accelerates kingside castling", "Fights for immediate d5 control")
      ),
      OpeningBranch(
        moveUci = "d2d4",
        moveSan = "d4",
        resultingFen = "rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq d3",
        openingName = "Queen's Pawn Opening",
        ecoCode = "A40/D00",
        whiteWinPct = 39,
        drawPct = 35,
        blackWinPct = 26,
        coachExplanation = "A solid, strategic foundation. The d4 pawn is naturally guarded by the Queen, giving White sturdy long-term positional pressure.",
        strategicPointers = listOf("Favors closed, strategic maneuvers", "Leads to Queen's Gambit structures", "Provides safe central footing")
      ),
      OpeningBranch(
        moveUci = "c2c4",
        moveSan = "c4",
        resultingFen = "rnbqkbnr/pppppppp/8/8/2P5/8/PP1PPPPP/RNBQKBNR b KQkq c3",
        openingName = "English Opening",
        ecoCode = "A10",
        whiteWinPct = 37,
        drawPct = 36,
        blackWinPct = 27,
        coachExplanation = "A hypermodern flank approach. Controls the d5 central square without committing the central pawns immediately.",
        strategicPointers = listOf("Controls d5 from the flank", "Transposes easily into d4 systems", "Delays central confrontation")
      ),
      OpeningBranch(
        moveUci = "g1f3",
        moveSan = "Nf3",
        resultingFen = "rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq -",
        openingName = "Réti Opening",
        ecoCode = "A04",
        whiteWinPct = 36,
        drawPct = 38,
        blackWinPct = 26,
        coachExplanation = "Flexible and prophylactic. Develops the kingside knight, stops ...e5, and keeps White's pawn structure adaptable.",
        strategicPointers = listOf("Prevents immediate ...e5", "Maximum flexibility", "Transposition powerhouse")
      )
    ),

    // After 1. e4 e5
    "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6" to listOf(
      OpeningBranch(
        moveUci = "g1f3",
        moveSan = "Nf3",
        resultingFen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq -",
        openingName = "King's Knight Opening",
        ecoCode = "C40",
        whiteWinPct = 39,
        drawPct = 33,
        blackWinPct = 28,
        coachExplanation = "The gold standard developing move. Attacks Black's e5 pawn immediately, forcing Black to react while White prepares kingside castling.",
        strategicPointers = listOf("Active attack on e5", "Rapid kingside mobilization", "Forces Black to defend")
      ),
      OpeningBranch(
        moveUci = "f1c4",
        moveSan = "Bc4",
        resultingFen = "rnbqkbnr/pppp1ppp/8/4p3/2B1P3/8/PPPP1PPP/RNBQK1NR b KQkq -",
        openingName = "Bishop's Opening",
        ecoCode = "C23",
        whiteWinPct = 36,
        drawPct = 32,
        blackWinPct = 32,
        coachExplanation = "Directly targets Black's weakest square (f7) before committing the knight, keeping f2-f4 breaks open.",
        strategicPointers = listOf("Laser on f7 square", "Leaves f-pawn unblocked", "Classical open play")
      ),
      OpeningBranch(
        moveUci = "d2d4",
        moveSan = "d4",
        resultingFen = "rnbqkbnr/pppp1ppp/8/4p3/3PP3/8/PPP2PPP/RNBQKBNR b KQkq d3",
        openingName = "Center Game",
        ecoCode = "C21",
        whiteWinPct = 34,
        drawPct = 30,
        blackWinPct = 36,
        coachExplanation = "Immediate violent collision in the center. Blasts open lines for pieces at the cost of bringing the queen out early after exd4.",
        strategicPointers = listOf("Instant open lines", "Early tactical sharp clashes", "Queen exposed after captures")
      )
    ),

    // After 1. e4 e5 2. Nf3 Nc6
    "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3" to listOf(
      OpeningBranch(
        moveUci = "f1c4",
        moveSan = "Bc4",
        resultingFen = "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3",
        openingName = "Italian Game (Giuoco Piano)",
        ecoCode = "C50",
        whiteWinPct = 38,
        drawPct = 34,
        blackWinPct = 28,
        coachExplanation = "The Italian Game. White pressures f7, prepares quick O-O, and plans c3 followed by d4 to establish a classical pawn duo.",
        strategicPointers = listOf("Control of the c4-f7 diagonal", "c3 and d4 pawn center plan", "Balanced and pedagogical")
      ),
      OpeningBranch(
        moveUci = "f1b5",
        moveSan = "Bb5",
        resultingFen = "r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3",
        openingName = "Ruy Lopez (Spanish Opening)",
        ecoCode = "C60",
        whiteWinPct = 40,
        drawPct = 36,
        blackWinPct = 24,
        coachExplanation = "The 'Spanish Torture'. White pins and attacks the defender of e5 (Nc6), asserting enduring positional pressure.",
        strategicPointers = listOf("Undermines defender of e5", "Long-term positional squeeze", "Richest theory in chess history")
      ),
      OpeningBranch(
        moveUci = "d2d4",
        moveSan = "d4",
        resultingFen = "r1bqkbnr/pppp1ppp/2n5/4p3/3PP3/5N2/PPP2PPP/RNBQKB1R b KQkq d3 0 3",
        openingName = "Scotch Game",
        ecoCode = "C45",
        whiteWinPct = 37,
        drawPct = 35,
        blackWinPct = 28,
        coachExplanation = "Forces central resolution on move 3. Gains open diagonals for both bishops while Black is forced to trade pawns.",
        strategicPointers = listOf("Eliminates Black's e5 pawn", "Open dynamic piece play", "Easy for White to learn")
      )
    ),

    // After 1. e4 c5 (Sicilian Defense)
    "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6" to listOf(
      OpeningBranch(
        moveUci = "g1f3",
        moveSan = "Nf3",
        resultingFen = "rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq -",
        openingName = "Open Sicilian (Main Line)",
        ecoCode = "B27",
        whiteWinPct = 38,
        drawPct = 30,
        blackWinPct = 32,
        coachExplanation = "Prepares the d2-d4 central thrust to rip open the board. Leads to the sharpest, highest-stakes positions in modern chess.",
        strategicPointers = listOf("Fights for d4 break", "Asymmetrical dynamic pawn structures", "Attacking chances on both flanks")
      ),
      OpeningBranch(
        moveUci = "c2c3",
        moveSan = "c3",
        resultingFen = "rnbqkbnr/pp1ppppp/8/2p5/4P3/2P5/PP1P1PPP/RNBQKBNR b KQkq -",
        openingName = "Alapin Sicilian",
        ecoCode = "B22",
        whiteWinPct = 36,
        drawPct = 36,
        blackWinPct = 28,
        coachExplanation = "The anti-Sicilian weapon. Intends to build a massive classical pawn center with d4, avoiding sharp Sicilian theory.",
        strategicPointers = listOf("Solid e4+d4 pawn center", "Blunts Black's wing counterplay", "Low theoretical risk")
      )
    ),

    // After 1. d4 d5 (Queen's Pawn Game)
    "rnbqkbnr/ppp1pppp/8/3p4/3P4/8/PPP1PPPP/RNBQKBNR w KQkq d6" to listOf(
      OpeningBranch(
        moveUci = "c2c4",
        moveSan = "c4",
        resultingFen = "rnbqkbnr/ppp1pppp/8/3p4/2PP4/8/PP2PPPP/RNBQKBNR b KQkq c3",
        openingName = "Queen's Gambit",
        ecoCode = "D06",
        whiteWinPct = 40,
        drawPct = 37,
        blackWinPct = 23,
        coachExplanation = "The premier strategic chess opening. White offers a flank pawn to deflect Black's center pawn on d5 and build a dominant e4/d4 center.",
        strategicPointers = listOf("Fights to dominate center with e4", "Temporary pawn sacrifice (easily recovered)", "Classic Karpov/Kasparov weapon")
      ),
      OpeningBranch(
        moveUci = "g1f3",
        moveSan = "Nf3",
        resultingFen = "rnbqkbnr/ppp1pppp/8/3p4/3P4/5N2/PPP1PPPP/RNBQKB1R b KQkq -",
        openingName = "Queen's Pawn: Zukertort / London Setup",
        ecoCode = "D02",
        whiteWinPct = 38,
        drawPct = 38,
        blackWinPct = 24,
        coachExplanation = "Ultra-solid foundation. White develops smoothly with Bf4, e3, and c3 to establish the impenetrable London/Colle pyramid.",
        strategicPointers = listOf("Extremely safe king structure", "Easy to play without memorization", "High win rate for amateurs")
      )
    ),

    // After 1. d4 Nf6 (Indian Defenses)
    "rnbqkb1r/pppppppp/5n2/8/3P4/8/PPP1PPPP/RNBQKBNR w KQkq -" to listOf(
      OpeningBranch(
        moveUci = "c2c4",
        moveSan = "c4",
        resultingFen = "rnbqkb1r/pppppppp/5n2/8/2PP4/8/PP2PPPP/RNBQKBNR b KQkq c3",
        openingName = "Indian Defenses (Main Line)",
        ecoCode = "E00/A50",
        whiteWinPct = 38,
        drawPct = 38,
        blackWinPct = 24,
        coachExplanation = "White seizes the maximum central territory before committing kingside pieces, preparing Nc3 and e4.",
        strategicPointers = listOf("Fights for total central command", "Transposes to King's Indian or Nimzo-Indian", "High theoretical depth")
      ),
      OpeningBranch(
        moveUci = "c1f4",
        moveSan = "Bf4",
        resultingFen = "rnbqkb1r/pppppppp/5n2/8/3P1B2/8/PPP1PPPP/RN1QKBNR b KQkq -",
        openingName = "London System",
        ecoCode = "D00",
        whiteWinPct = 39,
        drawPct = 36,
        blackWinPct = 25,
        coachExplanation = "The modern club player favorite. Develops the 'bad bishop' outside the pawn chain before playing e3.",
        strategicPointers = listOf("Bishop developed outside pawn chain", "Rock-solid pyramid pawn structure", "Deadly e5 knight outposts")
      )
    ),

    // After 1. e4 e6 (French Defense)
    "rnbqkbnr/pppp1ppp/4p3/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq -" to listOf(
      OpeningBranch(
        moveUci = "d2d4",
        moveSan = "d4",
        resultingFen = "rnbqkbnr/pppp1ppp/4p3/8/3PP3/8/PPP2PPP/RNBQKBNR b KQkq d3",
        openingName = "French Defense: Main Line",
        ecoCode = "C00",
        whiteWinPct = 39,
        drawPct = 34,
        blackWinPct = 27,
        coachExplanation = "White claims full central control with e4+d4. Black will strike back at White's center with ...d5.",
        strategicPointers = listOf("Occupy the full center with pawns", "Black will counter with d5", "Creates tension on e4")
      )
    ),

    // After 1. e4 c6 (Caro-Kann Defense)
    "rnbqkbnr/pp1ppppp/2p5/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq -" to listOf(
      OpeningBranch(
        moveUci = "d2d4",
        moveSan = "d4",
        resultingFen = "rnbqkbnr/pp1ppppp/2p5/8/3PP3/8/PPP2PPP/RNBQKBNR b KQkq d3",
        openingName = "Caro-Kann: Classical Foundation",
        ecoCode = "B10",
        whiteWinPct = 38,
        drawPct = 36,
        blackWinPct = 26,
        coachExplanation = "White establishes the ideal two-pawn center. Unlike the French Defense, Black will be able to develop the light-squared bishop outside the pawn chain.",
        strategicPointers = listOf("Dominant pawn center", "Black plays d5 next move", "Solid, positional gameplay")
      )
    )
  )

  fun getBranchesForPosition(position: Position): List<OpeningBranch> {
    val fenParts = position.toFen().split(" ")
    val boardOnly = fenParts[0]
    val boardAndColor = "${fenParts[0]} ${fenParts.getOrNull(1) ?: "w"}"

    // Match either exact board+color or partial board representation
    val matchEntry = tree.entries.find { (key, _) ->
      val keyParts = key.split(" ")
      val keyBoard = keyParts[0]
      val keyBoardAndColor = "${keyParts[0]} ${keyParts.getOrNull(1) ?: "w"}"
      keyBoardAndColor == boardAndColor || keyBoard == boardOnly
    }
    return matchEntry?.value ?: emptyList()
  }
}
