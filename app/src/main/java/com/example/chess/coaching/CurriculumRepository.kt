package com.example.chess.coaching

import com.example.chess.core.Move
import com.example.chess.core.Square

/**
 * Pre-authored pedagogical masterclasses for major openings, middlegame patterns, and endgames.
 * Zero ongoing API cost, instantaneous, mathematically and theoretically accurate.
 */
object CurriculumRepository {

  val allLessons: List<CurriculumLesson> = listOf(
    // 1. Italian Game (C50)
    CurriculumLesson(
      id = "italian_game",
      title = "Italian Game: Giuoco Piano",
      ecoCode = "C50",
      category = "OPENINGS",
      difficultyLevel = "BEGINNER",
      summary = "Classic open game focusing on rapid kingside development and immediate pressure against Black's vulnerable f7 square.",
      keyTakeaway = "Prioritize piece activity and keep the d4 square contested before launching kingside strikes.",
      steps = listOf(
        LessonStep(
          stepIndex = 1,
          startingFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
          playedMove = Move.fromUci("e2e4"),
          conceptTitle = "Seizing Central Real Estate",
          explanation = "White stakes an immediate claim to the center (d5 and f5 squares) while opening vital diagonal pathways for the Queen and light-squared Bishop.",
          hintLadder = HintLadder(
            level1Concept = "Which pawn advance opens lines for two pieces at once?",
            level2FocusZone = "Center files (e and d)",
            level3CandidatePiece = Square.fromAlgebraic("e2"),
            level4DirectMove = Move.fromUci("e2e4")
          ),
          recommendedArrow = Pair(Square.fromAlgebraic("e2"), Square.fromAlgebraic("e4")),
          highlightedSquares = listOf(Square.fromAlgebraic("d5"), Square.fromAlgebraic("f5"))
        ),
        LessonStep(
          stepIndex = 2,
          startingFen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2",
          playedMove = Move.fromUci("g1f3"),
          conceptTitle = "Developing with Tempo",
          explanation = "Never develop aimlessly. Nf3 simultaneously attacks Black's e5 pawn and prepares short kingside castling.",
          hintLadder = HintLadder(
            level1Concept = "How do you attack Black's undefended e5 pawn while developing a minor piece?",
            level2FocusZone = "Kingside knight development",
            level3CandidatePiece = Square.fromAlgebraic("g1"),
            level4DirectMove = Move.fromUci("g1f3")
          ),
          recommendedArrow = Pair(Square.fromAlgebraic("g1"), Square.fromAlgebraic("f3")),
          highlightedSquares = listOf(Square.fromAlgebraic("e5"))
        ),
        LessonStep(
          stepIndex = 3,
          startingFen = "r1bqkbnr/pppp1ppp/2n6/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 1 3",
          playedMove = Move.fromUci("f1c4"),
          conceptTitle = "The Italian Bishop on c4",
          explanation = "The defining move of the Italian Game! The Bishop targets Black's f7 square—the only pawn guarded solely by Black's King.",
          hintLadder = HintLadder(
            level1Concept = "Where can your light-squared Bishop create the strongest geometric crossfire?",
            level2FocusZone = "Targeting Black's weakest square (f7)",
            level3CandidatePiece = Square.fromAlgebraic("f1"),
            level4DirectMove = Move.fromUci("f1c4")
          ),
          recommendedArrow = Pair(Square.fromAlgebraic("c4"), Square.fromAlgebraic("f7")),
          highlightedSquares = listOf(Square.fromAlgebraic("f7"))
        )
      )
    ),

    // 2. Sicilian Defense (B20)
    CurriculumLesson(
      id = "sicilian_defense",
      title = "Sicilian Defense: The Asymmetric Fight",
      ecoCode = "B20",
      category = "OPENINGS",
      difficultyLevel = "INTERMEDIATE",
      summary = "Black's most combative response to 1.e4, trading a flank c-pawn for White's central d-pawn to create dynamic queen-side counterplay.",
      keyTakeaway = "Concede space early in exchange for long-term open c-file counterattacks and pawn superiority in the center.",
      steps = listOf(
        LessonStep(
          stepIndex = 1,
          startingFen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1",
          playedMove = Move.fromUci("c7c5"),
          conceptTitle = "Imbalancing the Center (1...c5)",
          explanation = "Instead of copying White with 1...e5, Black claims the d4 square with a flank pawn, setting up an open c-file trade.",
          hintLadder = HintLadder(
            level1Concept = "How do you control d4 without symmetry?",
            level2FocusZone = "Queenside bishop's pawn",
            level3CandidatePiece = Square.fromAlgebraic("c7"),
            level4DirectMove = Move.fromUci("c7c5")
          ),
          recommendedArrow = Pair(Square.fromAlgebraic("c7"), Square.fromAlgebraic("c5")),
          highlightedSquares = listOf(Square.fromAlgebraic("d4"))
        )
      )
    ),

    // 3. Queen's Gambit (D06)
    CurriculumLesson(
      id = "queens_gambit",
      title = "Queen's Gambit: Classical Mastery",
      ecoCode = "D06",
      category = "OPENINGS",
      difficultyLevel = "INTERMEDIATE",
      summary = "White offers a wing c-pawn to deflect Black's central d5-pawn and achieve complete dominance over e4 and d4.",
      keyTakeaway = "A pseudo-gambit: White can always regain the c4 pawn with Qa4+ or e3/Bxc4 with superior center control.",
      steps = listOf(
        LessonStep(
          stepIndex = 1,
          startingFen = "rnbqkbnr/ppp1pppp/8/3p4/2PP4/8/PP2PPPP/RNBQKBNR b KQkq - 0 2",
          playedMove = Move.fromUci("e7e6"),
          conceptTitle = "The Declined Defense (2...e6)",
          explanation = "Black solidifies d5 with the e-pawn, creating an unshakeable central pyramid while refusing to surrender central control.",
          hintLadder = HintLadder(
            level1Concept = "Reinforce d5 solidly without letting White capture the center.",
            level2FocusZone = "The e-pawn support diagonal",
            level3CandidatePiece = Square.fromAlgebraic("e7"),
            level4DirectMove = Move.fromUci("e7e6")
          ),
          recommendedArrow = Pair(Square.fromAlgebraic("e7"), Square.fromAlgebraic("e6")),
          highlightedSquares = listOf(Square.fromAlgebraic("d5"))
        )
      )
    ),

    // 4. Endgame: King + Pawn Opposition
    CurriculumLesson(
      id = "endgame_opposition",
      title = "Endgame Mastery: The Rule of Opposition",
      ecoCode = "END-01",
      category = "ENDGAME",
      difficultyLevel = "INTERMEDIATE",
      summary = "When two kings face each other with one square between them, the player NOT having to move holds 'The Opposition'.",
      keyTakeaway = "Use vertical and diagonal opposition to shoulder the enemy King away from your pawn's promotion path.",
      steps = listOf(
        LessonStep(
          stepIndex = 1,
          startingFen = "8/8/4k3/8/4K3/4P3/8/8 w - - 0 1",
          playedMove = Move.fromUci("e4d4"),
          conceptTitle = "Outflanking the Defense",
          explanation = "When holding the opposition, step around Black's king to pave an escort corridor for your pawn to promote.",
          hintLadder = HintLadder(
            level1Concept = "Step to the side to carve an outflanking path for your e-pawn.",
            level2FocusZone = "Queenside step around Black's king",
            level3CandidatePiece = Square.fromAlgebraic("e4"),
            level4DirectMove = Move.fromUci("e4d4")
          ),
          recommendedArrow = Pair(Square.fromAlgebraic("e4"), Square.fromAlgebraic("d4")),
          highlightedSquares = listOf(Square.fromAlgebraic("d5"), Square.fromAlgebraic("e5"))
        )
      )
    )
  )
}
