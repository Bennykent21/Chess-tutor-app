package com.chesstutor.app.domain

enum class LearnCategory(val displayName: String) {
    OPENING("Opening Principles & Repertoire"),
    TACTICS("Tactical Motifs & Patterns"),
    MIDDLEGAME("Middlegame Strategy & Planning"),
    ENDGAME("Endgame Technique & Blueprints")
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
        ),
        LearnTopic(
            id = "opening_sicilian_defense",
            category = LearnCategory.OPENING,
            title = "The Sicilian Defense (1. e4 c5)",
            subtitle = "Fighting for the Center Asymmetrically",
            summary = "The most popular and highest-scoring response to 1. e4. By playing c5, Black fights for the central d4 square with a flank pawn, leading to sharp, dynamic, and combative games.",
            keyPrinciples = listOf(
                "Trade your c-pawn for White's central d-pawn to establish a central pawn majority.",
                "Use the half-open c-file for active counterplay against White's queenside.",
                "Do not castle into premature kingside pawn storms without sufficient piece defense."
            ),
            demoFen = "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2",
            recommendedMoveUci = "g1f3",
            moveExplanation = "2. Nf3 develops the knight naturally and prepares to open the center with 3. d4 (Open Sicilian)."
        ),
        LearnTopic(
            id = "opening_ruy_lopez",
            category = LearnCategory.OPENING,
            title = "The Ruy Lopez (Spanish Opening)",
            subtitle = "1. e4 e5 2. Nf3 Nc6 3. Bb5",
            summary = "The classical pinnacle of chess strategy. White's bishop pins and attacks the defender of the e5 pawn, applying indirect long-term pressure against Black's entire center.",
            keyPrinciples = listOf(
                "Bb5 attacks the knight that guards Black's key central e5 pawn.",
                "Preserve your light-squared bishop (the 'Spanish Bishop') on c2 or b3 if chased by a6 and b5.",
                "Prepare the standard pawn center with c3 and d4."
            ),
            demoFen = "r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3",
            recommendedMoveUci = "a7a6",
            moveExplanation = "3...a6 is the Morphy Defense, questioning White's bishop immediately and initiating queenside counterplay."
        ),
        LearnTopic(
            id = "tactics_pin_and_skewer",
            category = LearnCategory.TACTICS,
            title = "The Absolute Pin & Skewer",
            subtitle = "Paralyzing Enemy Pieces Along Lines",
            summary = "A pin freezes a piece because moving it would expose a more valuable piece (or king) behind it. A skewer forces the more valuable piece to move, allowing capture of the piece behind it.",
            keyPrinciples = listOf(
                "Attack pinned pieces with pawns whenever possible ('pile up on the pinned piece').",
                "Relative pins freeze pieces in front of queens or rooks; absolute pins freeze pieces in front of kings.",
                "Rooks, bishops, and queens are the only pieces capable of delivering pins and skewers."
            ),
            demoFen = "r1b1k2r/pppp1ppp/8/4q3/1bP5/2N1P3/PP1Q1PPP/R3KB1R w KQkq - 0 10",
            recommendedMoveUci = "a1c1",
            moveExplanation = "Rc1 defends the pinned knight on c3, neutralising Black's tactical pressure along the diagonal."
        ),
        LearnTopic(
            id = "tactics_smothered_mate",
            category = LearnCategory.TACTICS,
            title = "The Smothered Mate (Philidor's Legacy)",
            subtitle = "Suffocation by Knight & Queen Sacrifice",
            summary = "One of the most beautiful mating patterns in chess. The defending king is completely boxed in and suffocated by his own pieces, allowing a lone knight to deliver checkmate.",
            keyPrinciples = listOf(
                "Force the enemy king into the corner with a double check.",
                "Sacrifice the queen on g8 or b8 to force an enemy rook or piece to block the king's only escape square.",
                "Deliver checkmate with Nf7# or Nc7# jumping over the suffocated defenders."
            ),
            demoFen = "6k1/5ppp/8/8/8/2Q5/5PPP/5RK1 w - - 0 1",
            recommendedMoveUci = "c3c8",
            moveExplanation = "Qc8# delivers a decisive back-rank checkmate because Black's king has no luft (escape air) behind his pawns."
        ),
        LearnTopic(
            id = "endgame_philidor_defense",
            category = LearnCategory.ENDGAME,
            title = "Philidor Defense: Third-Rank Defense",
            subtitle = "Holding the Draw in Rook Endgames",
            summary = "When defending with a rook against a pawn and rook, keep your rook on your third rank (6th rank for Black). This prevents the enemy king from advancing in front of his pawn.",
            keyPrinciples = listOf(
                "Keep your defending rook on the 6th rank (or 3rd rank) until the pawn advances.",
                "As soon as the opponent pawn pushes forward, move your rook to the opposite end of the board.",
                "Deliver endless vertical checks from behind because the enemy king has no pawn shield."
            ),
            demoFen = "8/8/8/4P3/8/2r5/4K3/1k1R4 b - - 0 1",
            recommendedMoveUci = "c3c6",
            moveExplanation = "Rc6 establishes the classic 6th-rank blockade, preventing White's king from walking in front of the e5 pawn."
        ),
        LearnTopic(
            id = "opening_sicilian_defense",
            category = LearnCategory.OPENING,
            title = "The Sicilian Defense",
            subtitle = "1. e4 c5: The Combatant's Counter-Attack",
            summary = "The most popular and combative response to 1. e4. Black fights for the center from the flank (c5), creating asymmetrical pawn structures with winning chances for both sides.",
            keyPrinciples = listOf(
                "Trade the c-pawn for White's d-pawn (cxd4) to create a semi-open c-file for your rook.",
                "Maintain two central pawns (e and d) against White's single remaining central e-pawn.",
                "Fight for queenside counterplay while keeping the king safe in the center or kingside."
            ),
            demoFen = "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2",
            recommendedMoveUci = "g1f3",
            moveExplanation = "2. Nf3 is the Open Sicilian main line, developing a knight toward the center and preparing an immediate strike with 3. d4."
        ),
        LearnTopic(
            id = "tactics_knight_fork",
            category = LearnCategory.TACTICS,
            title = "The Royal Knight Fork",
            subtitle = "Simultaneous Branching Strikes",
            summary = "Knights are unique because they can hop over pieces and attack multiple squares that no other piece can defend simultaneously. A royal fork attacks both King and Queen.",
            keyPrinciples = listOf(
                "Look for undefended or under-defended enemy pieces positioned on squares of the same color.",
                "Lure opposing heavy pieces onto forkable squares using checks or pawn sacrifices.",
                "Remember: a knight fork with check cannot be blocked—the king MUST move or the knight must be captured."
            ),
            demoFen = "r1b1k3/pp3ppp/8/8/1n6/2N5/PP3PPP/R2K1B1R b q - 0 12",
            recommendedMoveUci = "b4c2",
            moveExplanation = "Nc2 forks White's a1 rook and king on d1, forcing a winning material exchange."
        ),
        LearnTopic(
            id = "tactics_discovered_attack",
            category = LearnCategory.TACTICS,
            title = "Discovered Attack & The Windmill",
            subtitle = "Unmasking Hidden Artillery",
            summary = "A discovered attack occurs when one piece moves out of the way, unmasking a devastating attack from a queen, rook, or bishop behind it. Discovered checks are practically unstoppable.",
            keyPrinciples = listOf(
                "Line up your long-range pieces (rooks, bishops, queens) on the enemy king or queen even if shielded by friendly pieces.",
                "The moving front piece can attack an entirely separate target or capture free material with impunity.",
                "A discovered check allows the unmasking piece to make outrageous threats because the opponent must answer the check."
            ),
            demoFen = "r1b2rk1/pp3ppp/2n5/1B1p4/3P4/5N2/PP1B1PPP/R2QR1K1 w - - 0 13",
            recommendedMoveUci = "b5c6",
            moveExplanation = "Bxc6 removes Black's knight guard while creating doubled isolated c-pawns and open paths for White's pieces."
        ),
        LearnTopic(
            id = "middlegame_pawn_chains",
            category = LearnCategory.MIDDLEGAME,
            title = "Pawn Chains & Structures",
            subtitle = "Attacking the Base, Not the Head",
            summary = "Pawn chains dictate which side of the board each player should attack. The golden rule formulated by Nimzowitsch: attack the pawn chain at its base, where it is supported only by pieces.",
            keyPrinciples = listOf(
                "Attack the enemy pawn chain at its base (the root pawn) using flank pawn strikes.",
                "Advance pieces on the side of the board your pawn chain points toward.",
                "Never leave backward pawns on open files where opposing rooks can lock them down."
            ),
            demoFen = "rnbqk2r/pp2bppp/4pn2/2ppP3/3P4/2PB1N2/PP3PPP/RNBQK2R b KQkq - 0 6",
            recommendedMoveUci = "f6d7",
            moveExplanation = "Nd7 retreats to a safe square, preparing to undermine White's e5 wedge with an eventual f6 or c4 pawn strike."
        ),
        LearnTopic(
            id = "endgame_lucena_bridge",
            category = LearnCategory.ENDGAME,
            title = "The Lucena Position: Building the Bridge",
            subtitle = "The Universal Blueprint to Win Rook Endgames",
            summary = "The Lucena position is the cornerstone of rook and pawn endgames. When your pawn is on the 7th rank and your king is in front of it, you build a 'bridge' with your rook on the 4th rank to shield against enemy checks.",
            keyPrinciples = listOf(
                "Step 1: Use your rook to cut off the defending king by at least one file.",
                "Step 2: Lift your rook to the 4th rank (e.g. Rf4 or Rd4).",
                "Step 3: Walk your king out from the pawn's promotion path.",
                "Step 4: When the opposing rook checks you from behind, interpose your rook on the 4th rank ('build the bridge') to escort your queen home."
            ),
            demoFen = "1K1R4/3P1k2/8/8/8/8/r7/8 w - - 0 1",
            recommendedMoveUci = "d8e8",
            moveExplanation = "Re8! checks Black's king and drives it away from the d-file, allowing White's king to step out to d7 and build the winning bridge."
        ),
        LearnTopic(
            id = "endgame_king_opposition",
            category = LearnCategory.ENDGAME,
            title = "King Opposition & Triangulation",
            subtitle = "The Geometry of King and Pawn Endgames",
            summary = "Opposition occurs when two kings face each other on the same rank or file with only one square between them. The player who does NOT have to move holds the opposition and controls the critical breakthrough squares.",
            keyPrinciples = listOf(
                "Keep an odd number of squares between the two kings to seize the direct opposition.",
                "When you have the opposition, your opponent's king must step aside and yield access to key promotion squares.",
                "Use outflanking and triangulation to transfer the move (Zugzwang) to your opponent."
            ),
            demoFen = "8/8/8/4k3/8/4K3/4P3/8 w - - 0 1",
            recommendedMoveUci = "e3d3",
            moveExplanation = "Kd3! outflanks Black's king, maintaining the opposition and clearing the advance path for the e-pawn to promote."
        )
    )
}
