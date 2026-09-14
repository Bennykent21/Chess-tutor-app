package com.chesstutor.app.domain

enum class LearnCategory(val displayName: String) {
    OPENING("Opening Principles & Repertoire"),
    MIDDLEGAME("Middlegame Strategy & Planning"),
    ENDGAME("Endgame Technique & Essential Blueprints")
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
    val moveExplanation: String
)

object LearnCurriculumRepository {
    val topics: List<LearnTopic> = listOf(
        // OPENINGS
        LearnTopic(
            id = "opening_fundamentals",
            category = LearnCategory.OPENING,
            title = "The Golden Rules of Opening",
            subtitle = "Center Control & Rapid Development",
            summary = "The opening is not about launching premature attacks. It is about claiming real estate in the center, getting your minor pieces into active squares, and tucking your king into safety before the center blows open.",
            keyPrinciples = listOf(
                "Stake a claim in the center immediately with e4 or d4.",
                "Develop knights before bishops to retain flexible options.",
                "Never move the same piece twice in the opening without a concrete tactical reason.",
                "Castle early (typically before move 10) to connect your rooks and secure your king."
            ),
            demoFen = "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3",
            recommendedMoveUci = "f1c4",
            moveExplanation = "Bc4 develops the bishop to an active diagonal exerting direct pressure against Black's f7 weakness while preparing rapid kingside castling."
        ),
        LearnTopic(
            id = "opening_italian_game",
            category = LearnCategory.OPENING,
            title = "The Italian Game (Giuoco Piano)",
            subtitle = "1. e4 e5 2. Nf3 Nc6 3. Bc4",
            summary = "One of the oldest and most principled openings in chess. White establishes central tension and develops the light-squared bishop to target the sensitive f7 square before Black has castled.",
            keyPrinciples = listOf(
                "Focus pressure on the f7 square, which is defended solely by Black's king.",
                "Build a pawn center with c3 and d4 when timing allows.",
                "Keep your d-pawn flexible—either d3 for a quiet positional Giuoco Pianissimo or d4 for open tactical skirmishes."
            ),
            demoFen = "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4",
            recommendedMoveUci = "c2c3",
            moveExplanation = "3. c3 prepares to seize the full center with d4, supporting two central pawns while securing an escape retreat for the c4 bishop."
        ),
        LearnTopic(
            id = "opening_queens_gambit",
            category = LearnCategory.OPENING,
            title = "The Queen's Gambit",
            subtitle = "1. d4 d5 2. c4",
            summary = "White offers a wing pawn (c4) to tempt Black into surrendering central control (dxc4). Unlike a true gambit, White can virtually always regain the pawn with superior central dominance.",
            keyPrinciples = listOf(
                "If Black accepts with 2...dxc4, do not panic—strike back with e4 or e3 to quickly recapture on c4.",
                "If Black declines with 2...e6 (QGD) or 2...c6 (Slav), reinforce your d4 anchor and pin their knight with Bg5 or develop smoothly with Nf3.",
                "Maneuver your rooks to the half-open c-file for sustained queenside pressure."
            ),
            demoFen = "rnbqkbnr/ppp1pppp/8/3p4/2PP4/8/PP2PPPP/RNBQKBNR b KQkq c3 0 2",
            recommendedMoveUci = "e7e6",
            moveExplanation = "2...e6 defends d5 solidly, maintaining a firm pawn wedge in the center (the Queen's Gambit Declined)."
        ),

        // MIDDLEGAME
        LearnTopic(
            id = "middlegame_outposts",
            category = LearnCategory.MIDDLEGAME,
            title = "Outposts & Knight Strongholds",
            subtitle = "Dominating the 5th and 6th Ranks",
            summary = "An outpost is a square—usually on the 4th, 5th, or 6th rank—that cannot be attacked by an enemy pawn. A knight anchored on an outpost radiates influence across the entire board.",
            keyPrinciples = listOf(
                "Locate hole squares created when your opponent pushes flank pawns.",
                "Defend your outpost piece with a pawn so trading becomes disadvantageous for the defender.",
                "An octopus knight on the 6th rank paralyzes opposing piece coordination."
            ),
            demoFen = "r4rk1/pp1b1ppp/2n1pn2/3p4/3P4/2N1PN2/PP2BPPP/R2Q1RK1 w - - 0 11",
            recommendedMoveUci = "c3a4",
            moveExplanation = "Na4 routes the knight toward the c5 outpost square, which cannot be challenged by Black's pawns."
        ),
        LearnTopic(
            id = "middlegame_open_files",
            category = LearnCategory.MIDDLEGAME,
            title = "Open Files & the 7th Rank",
            subtitle = "Rook Penetration & Infiltration",
            summary = "Rooks need open highways to unleash their long-range power. Controlling an open file is the prerequisite to infiltrating the 7th rank, where rooks feast on enemy pawns and box in the defending king.",
            keyPrinciples = listOf(
                "Identify open files (no pawns) and half-open files (only opponent's pawns).",
                "Double rooks (the 'gun on the file') to prevent the opponent from contesting control.",
                "A rook on the 7th rank (the 'pig on the 7th') almost always represents decisive positional dominance."
            ),
            demoFen = "3r2k1/pp3ppp/2p5/8/8/2P1R3/PP3PPP/6K1 w - - 0 22",
            recommendedMoveUci = "e3e7",
            moveExplanation = "Re7 infiltrates the 7th rank directly, attacking the b7 pawn and paralyzing Black's rooks."
        ),
        LearnTopic(
            id = "middlegame_pawn_breaks",
            category = LearnCategory.MIDDLEGAME,
            title = "Pawn Breaks & Structure",
            subtitle = "Unlocking Closed Centers & Creating Lines",
            summary = "When positions become locked, pieces have nowhere to go. A pawn break is a planned pawn advance that forces trades, opening diagonals and files for your waiting pieces.",
            keyPrinciples = listOf(
                "Identify where your pawn chain points—attack in the direction your pawns point.",
                "Prepare pawn levers by supporting the target square with rooks and pieces.",
                "Never initiate a break that leaves your own king exposed."
            ),
            demoFen = "r1b2rk1/ppq1bppp/2n1p3/3pP3/3P4/2PB1N2/P4PPP/R1BQ1RK1 w - - 0 12",
            recommendedMoveUci = "d3h7",
            moveExplanation = "The classic Greek Gift bishop sacrifice (Bxh7+) blows open the pawn shelter of Black's castled king."
        ),

        // ENDGAME
        LearnTopic(
            id = "endgame_king_activity",
            category = LearnCategory.ENDGAME,
            title = "King Activity in the Endgame",
            subtitle = "From Hiding to Attacking Weapon",
            summary = "During the opening and middlegame, your king hides behind pawns for safety. Once queens are off the board, the king transforms into your strongest attacking and blocking piece.",
            keyPrinciples = listOf(
                "March your king toward the center as soon as major piece threats have cleared.",
                "Use your king to escort your passed pawns and blockade opponent pawns.",
                "A passive king in the endgame is almost guaranteed to lose."
            ),
            demoFen = "8/5pk1/4p1p1/8/8/4P1P1/5PK1/8 w - - 0 35",
            recommendedMoveUci = "g2f3",
            moveExplanation = "Kf3 immediately centralizes the king toward e4 and d4 before Black's king can arrive."
        ),
        LearnTopic(
            id = "endgame_opposition",
            category = LearnCategory.ENDGAME,
            title = "The Opposition & Key Squares",
            subtitle = "The Universal King & Pawn Blueprint",
            summary = "When two kings face each other on the same rank, file, or diagonal separated by one square, the player who does not have to move holds 'the opposition'. It lets you outflank the opponent king.",
            keyPrinciples = listOf(
                "Keep an odd number of squares between the kings to hold the opposition when it's your opponent's turn.",
                "If your king reaches the key squares (the 6th rank ahead of your pawn), promotion is mathematically forced regardless of who has the move.",
                "Never push your pawn ahead of your king in king & pawn endgames."
            ),
            demoFen = "8/8/8/4k3/8/4K3/8/8 w - - 0 1",
            recommendedMoveUci = "e3d3",
            moveExplanation = "Kd3 takes the diagonal opposition, forcing Black's king to give ground to the left or right."
        ),
        LearnTopic(
            id = "endgame_rook_lucena",
            category = LearnCategory.ENDGAME,
            title = "Lucena Position: Building the Bridge",
            subtitle = "Winning Rook & Pawn vs. Rook",
            summary = "The Lucena position is the cornerstone of all rook endgames. When your pawn reaches the 7th rank and your king is in front of it, White wins by using the rook as an umbrella shield ('building a bridge').",
            keyPrinciples = listOf(
                "Cut off the defending king by at least one file.",
                "Place your rook on the 4th rank.",
                "Step your king out to allow the pawn to promote; when the opponent checks from behind, your rook steps onto the 4th rank to block the check."
            ),
            demoFen = "1K1R4/3P1k2/8/8/8/8/8/2r5 w - - 0 1",
            recommendedMoveUci = "d8d4",
            moveExplanation = "Rd4 establishes the 4th-rank bridge platform. White will then play Ke8, and block vertical rook checks with Re4+."
        )
    )
}
