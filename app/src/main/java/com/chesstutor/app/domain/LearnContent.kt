package com.chesstutor.app.domain

enum class LearnCategory(val displayName: String) {
    OPENING("Openings"),
    TACTICS("Tactics"),
    MIDDLEGAME("Middlegame"),
    ENDGAME("Endgame"),
    BLUNDER_PATTERNS("Blunder Patterns")
}

data class LearnTopic(
    val id: String,
    val category: LearnCategory,
    val title: String,
    val subtitle: String,
    val summary: String,
    val keyPrinciples: List<String>,
    val demoFen: String,
    val recommendedMoveUci: String,
    val moveExplanation: String,
    val validation: TopicValidation = TopicValidation.LEGAL_MOVE
)

enum class TopicValidation {
    LEGAL_MOVE,
    CHECKMATE,
    KNIGHT_FORK
}

object LearnCurriculumRepository {
    val topics: List<LearnTopic> = listOf(
        // OPENINGS
        LearnTopic(
            id = "opening_fundamentals",
            category = LearnCategory.OPENING,
            title = "The Golden Rules of Opening",
            subtitle = "Center Control & Rapid Development",
            summary = "The opening is about claiming central space and developing pieces before castling to safety.",
            keyPrinciples = listOf(
                "Control e4 and d4 with pawns",
                "Develop knights before bishops",
                "Castle early to protect the king"
            ),
            demoFen = "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3",
            recommendedMoveUci = "f1c4",
            moveExplanation = "Bc4 targets f7 and prepares rapid castling."
        ),
        LearnTopic(
            id = "opening_italian_game",
            category = LearnCategory.OPENING,
            title = "The Italian Game",
            subtitle = "1. e4 e5 2. Nf3 Nc6 3. Bc4",
            summary = "A classical opening focusing pressure on Black's weak f7 square.",
            keyPrinciples = listOf(
                "Target the vulnerable f7 square",
                "Build a strong center with c3 and d4",
                "Keep d-pawn flexible for development"
            ),
            demoFen = "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4",
            recommendedMoveUci = "c2c3",
            moveExplanation = "3. c3 prepares to seize the full center with d4."
        ),
        LearnTopic(
            id = "opening_queens_gambit",
            category = LearnCategory.OPENING,
            title = "The Queen's Gambit",
            subtitle = "1. d4 d5 2. c4",
            summary = "White offers a wing pawn to establish central dominance.",
            keyPrinciples = listOf(
                "Offer the c-pawn to gain central control",
                "Recapture quickly if Black takes on c4",
                "Control the half-open c-file with rooks"
            ),
            demoFen = "rnbqkbnr/ppp1pppp/8/3p4/2PP4/8/PP2PPPP/RNBQKBNR b KQkq c3 0 2",
            recommendedMoveUci = "e7e6",
            moveExplanation = "2...e6 solidly defends d5 in the Queen's Gambit Declined."
        ),
        LearnTopic(
            id = "opening_sicilian_defense",
            category = LearnCategory.OPENING,
            title = "The Sicilian Defense",
            subtitle = "1. e4 c5: Dynamic Counter-Attack",
            summary = "The combative response fighting for the center from the flank.",
            keyPrinciples = listOf(
                "Fight for d4 from the flank with c5",
                "Trade c-pawn for White's d-pawn",
                "Create counterplay on the half-open c-file"
            ),
            demoFen = "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2",
            recommendedMoveUci = "g1f3",
            moveExplanation = "2. Nf3 prepares the central strike with 3. d4."
        ),
        LearnTopic(
            id = "opening_ruy_lopez",
            category = LearnCategory.OPENING,
            title = "The Ruy Lopez (Spanish Opening)",
            subtitle = "1. e4 e5 2. Nf3 Nc6 3. Bb5",
            summary = "Indirect pressure against the defender of Black's central e5 pawn.",
            keyPrinciples = listOf(
                "Pressure the knight defending the e5 pawn",
                "Preserve the light-squared bishop on c2 or b3",
                "Prepare central expansion with c3 and d4"
            ),
            demoFen = "r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3",
            recommendedMoveUci = "a7a6",
            moveExplanation = "3...a6 questions White's bishop immediately."
        ),

        // TACTICS
        LearnTopic(
            id = "tactics_pin_and_skewer",
            category = LearnCategory.TACTICS,
            title = "Defending a Pinned Knight",
            subtitle = "Paralyzing Enemy Pieces Along Lines",
            summary = "Pins freeze pieces in place; skewers force valuable pieces to abandon defenders.",
            keyPrinciples = listOf(
                "Attack pinned pieces with pawns",
                "Absolute pins freeze pieces before the king",
                "Force high-value targets to flee skewers"
            ),
            demoFen = "r1b1k2r/pppp1ppp/8/4q3/1bP5/2N1P3/PP1Q1PPP/R3KB1R w KQkq - 0 10",
            recommendedMoveUci = "a1c1",
            moveExplanation = "Rc1 adds a defender to the knight on c3 while the bishop on b4 keeps it pinned to the king."
        ),
        LearnTopic(
            id = "tactics_knight_fork",
            category = LearnCategory.TACTICS,
            title = "The Royal Knight Fork",
            subtitle = "Simultaneous Multi-Square Strikes",
            summary = "Knights hop over blockers to attack multiple critical squares simultaneously.",
            keyPrinciples = listOf(
                "Target king and queen simultaneously",
                "Look for undefended pieces on same-color squares",
                "Knight checks cannot be blocked"
            ),
            demoFen = "r1b1k3/pp3ppp/8/8/1n6/2N5/PP3PPP/R3K2R b q - 0 12",
            recommendedMoveUci = "b4c2",
            moveExplanation = "Nc2 forks White's a1 rook and e1 king.",
            validation = TopicValidation.KNIGHT_FORK
        ),
        LearnTopic(
            id = "tactics_discovered_attack",
            category = LearnCategory.TACTICS,
            title = "Discovered Attack",
            subtitle = "Unmasking Hidden Artillery",
            summary = "Moving the front piece unleashes an attack from the piece behind it.",
            keyPrinciples = listOf(
                "Unmask long-range rooks and bishops",
                "The moving piece strikes an extra target",
                "Discovered checks paralyze opposing replies"
            ),
            demoFen = "r1b2rk1/pp3ppp/2n5/1B1p4/3P4/5N2/PP1B1PPP/R2QR1K1 w - - 0 13",
            recommendedMoveUci = "b5c6",
            moveExplanation = "Bxc6 damages pawn structure while clearing lines."
        ),
        LearnTopic(
            id = "tactics_smothered_mate",
            category = LearnCategory.TACTICS,
            title = "The Smothered Mate",
            subtitle = "Suffocation by Knight & Queen Sacrifice",
            summary = "The king is boxed in by its own defenders and mated by a lone knight.",
            keyPrinciples = listOf(
                "Box the enemy king with own pieces",
                "Sacrifice queen to force blocking capture",
                "Knight leaps in for decisive checkmate"
            ),
            demoFen = "6rk/6pp/8/4N3/8/8/8/6K1 w - - 0 1",
            recommendedMoveUci = "e5f7",
            moveExplanation = "Nf7# is a smothered mate: the knight checks while Black's own rook and pawns remove every king escape.",
            validation = TopicValidation.CHECKMATE
        ),

        // MIDDLEGAME
        LearnTopic(
            id = "middlegame_outposts",
            category = LearnCategory.MIDDLEGAME,
            title = "Outposts & Knight Strongholds",
            subtitle = "Dominating the 5th and 6th Ranks",
            summary = "Squares that cannot be attacked by enemy pawns provide dominant homes for knights.",
            keyPrinciples = listOf(
                "Anchor knights on squares immune to pawns",
                "Occupy 5th and 6th rank holes",
                "Support outpost pieces with friendly pawns"
            ),
            demoFen = "r4rk1/pp1b1ppp/2n1pn2/3p4/3P4/2N1PN2/PP2BPPP/R2Q1RK1 w - - 0 11",
            recommendedMoveUci = "c3a4",
            moveExplanation = "Na4 routes the knight to the uncontested c5 outpost."
        ),
        LearnTopic(
            id = "middlegame_open_files",
            category = LearnCategory.MIDDLEGAME,
            title = "Open Files & the 7th Rank",
            subtitle = "Rook Penetration & Infiltration",
            summary = "Rooks control open files to infiltrate the 7th rank and attack enemy pawns.",
            keyPrinciples = listOf(
                "Double rooks on open files",
                "Infiltrate the 7th rank to target pawns",
                "Restrict enemy king behind pawn walls"
            ),
            demoFen = "3r2k1/pp3ppp/2p5/8/8/2P1R3/PP3PPP/6K1 w - - 0 22",
            recommendedMoveUci = "e3e7",
            moveExplanation = "Re7 infiltrates the 7th rank directly."
        ),
        LearnTopic(
            id = "middlegame_pawn_breaks",
            category = LearnCategory.MIDDLEGAME,
            title = "Pawn Breaks & Structure",
            subtitle = "Unlocking Closed Centers & Creating Lines",
            summary = "Pawn breaks force trades that open files and diagonals for waiting pieces.",
            keyPrinciples = listOf(
                "Advance pawns to unlock closed lines",
                "Attack the base of the pawn chain",
                "Support break squares with rooks"
            ),
            demoFen = "r1b2rk1/ppq1bppp/2n1p3/3pP3/3P4/2PB1N2/P4PPP/R1BQ1RK1 w - - 0 12",
            recommendedMoveUci = "d3h7",
            moveExplanation = "Bxh7+ initiates the classic Greek Gift sacrifice."
        ),

        // ENDGAME
        LearnTopic(
            id = "endgame_king_activity",
            category = LearnCategory.ENDGAME,
            title = "King Activity in the Endgame",
            subtitle = "From Hiding to Attacking Weapon",
            summary = "With queens off the board, the king becomes an active attacker and blocker.",
            keyPrinciples = listOf(
                "March king to the center in endgames",
                "Escort passed pawns to promotion",
                "Block enemy king from infiltration"
            ),
            demoFen = "8/5pk1/4p1p1/8/8/4P1P1/5PK1/8 w - - 0 35",
            recommendedMoveUci = "g2f3",
            moveExplanation = "Kf3 centralizes the king toward the action."
        ),
        LearnTopic(
            id = "endgame_opposition",
            category = LearnCategory.ENDGAME,
            title = "The Opposition & Key Squares",
            subtitle = "The Universal King & Pawn Blueprint",
            summary = "Controlling the square between kings forces the opponent to yield ground.",
            keyPrinciples = listOf(
                "Face enemy king with odd square gap",
                "Force opposing king to yield ground",
                "Seize 6th rank ahead of passed pawn"
            ),
            demoFen = "8/8/8/4k3/8/4K3/8/8 w - - 0 1",
            recommendedMoveUci = "e3d3",
            moveExplanation = "Kd3 takes the opposition, forcing Black to step aside."
        ),
        LearnTopic(
            id = "endgame_lucena_bridge",
            category = LearnCategory.ENDGAME,
            title = "Lucena Position: Building the Bridge",
            subtitle = "Winning Rook & Pawn vs. Rook",
            summary = "The definitive rook endgame technique to promote a 7th-rank passed pawn.",
            keyPrinciples = listOf(
                "Cut off defending king by one file",
                "Lift rook to 4th rank",
                "Shield king from checks with the rook"
            ),
            demoFen = "4K3/4P2k/8/8/8/8/r7/3R4 w - - 0 1",
            recommendedMoveUci = "d1d4",
            moveExplanation = "Rd4 sets up the 4th-rank bridge to block rook checks."
        ),
        LearnTopic(
            id = "endgame_philidor_defense",
            category = LearnCategory.ENDGAME,
            title = "Philidor Defense (Rook Endgames)",
            subtitle = "Holding the Draw in Rook Endgames",
            summary = "Keep the defending rook on the 6th rank until the pawn pushes, then check from behind.",
            keyPrinciples = listOf(
                "Hold rook on 6th rank blockade",
                "Drop rook to back rank when pawn pushes",
                "Deliver endless vertical checks from behind"
            ),
            demoFen = "7k/8/8/4P3/8/2r5/4K3/3R4 b - - 0 1",
            recommendedMoveUci = "c3c6",
            moveExplanation = "Rc6 establishes the classic 6th-rank blockade."
        ),

        // BLUNDER PATTERNS (§4.3: Merged into single screen with sections)
        LearnTopic(
            id = "lesson_mate_1",
            category = LearnCategory.BLUNDER_PATTERNS,
            title = "Missed Mate in 1",
            subtitle = "Decisive Tactical Blindness",
            summary = "Failing to play an immediate checkmate allows the opponent to escape.",
            keyPrinciples = listOf(
                "Scan for immediate checkmate deliveries",
                "Look for king with zero legal escapes",
                "Execute decisive mate without delay"
            ),
            demoFen = "r1bqkb1r/pppp1ppp/2n5/4p3/2B1n3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 4",
            recommendedMoveUci = "f3f7",
            moveExplanation = "Qxf7# delivers immediate checkmate on the weak f7 square.",
            validation = TopicValidation.CHECKMATE
        ),
        LearnTopic(
            id = "lesson_hanging_piece",
            category = LearnCategory.BLUNDER_PATTERNS,
            title = "Hanging Major Pieces",
            subtitle = "Board Awareness & Piece Protection",
            summary = "Leaving pieces undefended on active files is the primary rating sink under 1500.",
            keyPrinciples = listOf(
                "Check if every active piece is defended",
                "Avoid leaving rooks on open files unprotected",
                "Count attackers vs defenders before moving"
            ),
            demoFen = "r1bqk2r/pppp1ppp/2n2n2/4p3/1b2P3/2NP1N2/PPP2PPP/R1BQKB1R w KQkq - 2 5",
            recommendedMoveUci = "c1d2",
            moveExplanation = "Bd2 breaks the pin and defends the c3 knight."
        ),
        LearnTopic(
            id = "lesson_back_rank",
            category = LearnCategory.BLUNDER_PATTERNS,
            title = "Back-Rank Checkmate & Luft",
            subtitle = "Trapped by Friendly Pawns",
            summary = "When pawns block the king's escape, an infiltrating rook delivers checkmate.",
            keyPrinciples = listOf(
                "Create an escape square (luft) for king",
                "Guard vulnerable back ranks with rooks",
                "Infiltrate undefended enemy 1st or 8th ranks"
            ),
            demoFen = "6k1/5ppp/8/8/8/8/5PPP/4R1K1 w - - 0 1",
            recommendedMoveUci = "e1e8",
            moveExplanation = "Re8# delivers decisive back-rank mate.",
            validation = TopicValidation.CHECKMATE
        ),
        LearnTopic(
            id = "lesson_overworked_piece",
            category = LearnCategory.BLUNDER_PATTERNS,
            title = "Overworked Defenders",
            subtitle = "Tactical Pressure & Dual Burden",
            summary = "A piece trying to defend two separate critical squares collapses under dual pressure.",
            keyPrinciples = listOf(
                "Identify pieces defending multiple threats",
                "Overload defender by attacking both targets",
                "Defenders collapse under dual pressure"
            ),
            demoFen = "2r3k1/5ppp/8/3q4/8/8/5PPP/2R3K1 w - - 0 1",
            recommendedMoveUci = "c1c8",
            moveExplanation = "Rxc8# exploits the overworked back rank."
        ),
        LearnTopic(
            id = "lesson_interpose_fail",
            category = LearnCategory.BLUNDER_PATTERNS,
            title = "Failing to Interpose",
            subtitle = "King Safety & Defense Calculation",
            summary = "Stepping the king into worse danger instead of blocking with a defended piece.",
            keyPrinciples = listOf(
                "Block checks with lowest-value piece",
                "Calculate whether capture or blocking is safer",
                "Avoid retreating king into open attacks"
            ),
            demoFen = "r1bqk2r/ppp2ppp/2n5/1B1p4/1b1Pn3/2N2N2/PPP2PPP/R1BQK2R w KQkq - 0 7",
            recommendedMoveUci = "c1d2",
            moveExplanation = "Bd2 blocks the check and shields the king safely."
        )
    )
}
