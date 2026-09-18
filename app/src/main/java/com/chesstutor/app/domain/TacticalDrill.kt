package com.chesstutor.app.domain

data class TacticalDrill(
    val id: String,
    val title: String,
    val category: String,
    val fen: String,
    val solutionUci: String,
    val prompt: String = "White to move · Find mate in 1"
)

object TrainDrillsRepository {
    val drills = listOf(
        TacticalDrill(
            id = "drill_mate_1",
            title = "Missed Mate in 1",
            category = "Forced Mate",
            fen = "r1bqkb1r/pppp1ppp/2n5/4p3/2B1n3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 4",
            solutionUci = "f3f7",
            prompt = "White to move · Find mate in 1"
        ),
        TacticalDrill(
            id = "drill_back_rank",
            title = "Back-Rank Checkmate",
            category = "King Safety",
            fen = "3r2k1/5ppp/8/8/8/8/5PPP/4R1K1 w - - 0 1",
            solutionUci = "e1e8",
            prompt = "White to move · Deliver back-rank mate"
        ),
        TacticalDrill(
            id = "drill_overworked",
            title = "Overworked Defenders",
            category = "Tactical Pressure",
            fen = "2r3k1/5ppp/8/3q4/8/8/5PPP/2R3K1 w - - 0 1",
            solutionUci = "c1c8",
            prompt = "White to move · Exploit the back rank"
        ),
        TacticalDrill(
            id = "drill_hanging_piece",
            title = "Hanging Major Piece",
            category = "Board Awareness",
            fen = "r1bqk2r/pppp1ppp/2n2n2/4p3/1b2P3/2NP1N2/PPP2PPP/R1BQKB1R w KQkq - 2 5",
            solutionUci = "c1d2",
            prompt = "White to move · Defend the pinned knight"
        ),
        TacticalDrill(
            id = "drill_knight_fork",
            title = "Royal Knight Fork",
            category = "Fork & Double Attack",
            fen = "r1b1k3/pp3ppp/8/8/1n6/2N5/PP3PPP/R3K2R b q - 0 12",
            solutionUci = "b4c2",
            prompt = "Black to move · Fork King and Rook"
        ),
        TacticalDrill(
            id = "drill_interpose",
            title = "Failing to Interpose",
            category = "Defensive Technique",
            fen = "r1bqk2r/ppp2ppp/2n5/1B1p4/1b1Pn3/2N2N2/PPP2PPP/R1BQK2R w KQkq - 0 7",
            solutionUci = "c1d2",
            prompt = "White to move · Shield the King"
        ),
        TacticalDrill(
            id = "drill_queen_mate",
            title = "Queen Battery Mate",
            category = "Forced Mate",
            fen = "r1b2rk1/pp3ppp/2n5/3p4/3P4/2Q2N2/PP1B1PPP/R3KB1R w - - 0 1",
            solutionUci = "f1d3",
            prompt = "White to move · Line up the battery"
        ),
        TacticalDrill(
            id = "drill_pin_skewer",
            title = "Absolute Pin Defense",
            category = "Pins & Skewers",
            fen = "r1b1k2r/pppp1ppp/8/4q3/1bP5/2N1P3/PP1Q1PPP/R3KB1R w KQkq - 0 10",
            solutionUci = "a1c1",
            prompt = "White to move · Support the pinned piece"
        ),
        TacticalDrill(
            id = "drill_lucena",
            title = "Lucena Position",
            category = "Endgame Technique",
            fen = "1K1R4/3P1k2/8/8/8/8/8/2r5 w - - 0 1",
            solutionUci = "d8d4",
            prompt = "White to move · Build the 4th-rank bridge"
        ),
        TacticalDrill(
            id = "drill_opposition",
            title = "King Opposition",
            category = "Endgame Technique",
            fen = "8/8/8/4k3/8/4K3/8/8 w - - 0 1",
            solutionUci = "e3d3",
            prompt = "White to move · Seize the opposition"
        ),
        TacticalDrill(
            id = "drill_greek_gift",
            title = "Greek Gift Sacrifice",
            category = "Attacking Patterns",
            fen = "r1b2rk1/ppq1bppp/2n1p3/3pP3/3P4/2PB1N2/P4PPP/R1BQ1RK1 w - - 0 12",
            solutionUci = "d3h7",
            prompt = "White to move · Strike at the king"
        ),
        TacticalDrill(
            id = "drill_discovered_check",
            title = "Discovered Attack",
            category = "Tactical Motifs",
            fen = "r1b2rk1/pp3ppp/2n5/1B1p4/3P4/5N2/PP1B1PPP/R2QR1K1 w - - 0 13",
            solutionUci = "b5c6",
            prompt = "White to move · Remove the guard"
        )
    )
}
