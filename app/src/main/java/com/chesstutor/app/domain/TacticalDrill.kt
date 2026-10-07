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
            category = "Tactical Setup",
            fen = "r1bqkb1r/pppp1ppp/2n5/4p3/2B1n3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 4",
            solutionUci = "f3f7",
            prompt = "White to move · Find mate in 1"
        ),
        TacticalDrill(
            id = "drill_back_rank",
            title = "Back-Rank Checkmate",
            category = "King Safety",
            fen = "6k1/5ppp/8/8/8/8/5PPP/4R1K1 w - - 0 1",
            solutionUci = "e1e8",
            prompt = "White to move · Deliver back-rank mate"
        ),
        TacticalDrill(
            id = "drill_overworked",
            title = "Overworked Defenders",
            category = "Tactical Pressure",
            fen = "2r3k1/5ppp/8/3q4/8/8/5PPP/2R3K1 w - - 0 1",
            solutionUci = "c1c8",
            prompt = "White to move · Exploit the overloaded queen (Rxc8+)"
        ),
        TacticalDrill(
            id = "drill_hanging_piece",
            title = "Hanging Major Piece",
            category = "Board Awareness",
            fen = "r1b1kbnr/pppp1ppp/2n5/4p3/3qP3/5N2/PPP2PPP/RNBQKB1R w KQkq - 0 5",
            solutionUci = "f3d4",
            prompt = "White to move · Capture the undefended queen"
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
            title = "Interposing Against Check",
            category = "Defensive Technique",
            fen = "rnbqk1nr/pppp1ppp/8/4p3/1b2P3/3P4/PPP2PPP/RNBQKBNR w KQkq - 1 3",
            solutionUci = "c1d2",
            prompt = "White to move · Interpose to block the bishop check"
        ),
        TacticalDrill(
            id = "drill_queen_battery",
            title = "Queen & Bishop Battery Mate",
            category = "Forced Mate",
            fen = "r1bq1rk1/ppp2p1p/2n5/8/8/2Q5/PB3PPP/R3K2R w KQ - 0 10",
            solutionUci = "c3g7",
            prompt = "White to move · Deliver mate on the long diagonal"
        ),
        TacticalDrill(
            id = "drill_pin_skewer",
            title = "Absolute Pin Defense",
            category = "Pins & Skewers",
            fen = "4k3/8/8/8/1b6/2N5/8/R3K3 w - - 0 1",
            solutionUci = "a1c1",
            prompt = "White to move · Support the pinned knight"
        ),
        TacticalDrill(
            id = "drill_lucena",
            title = "Lucena Position",
            category = "Endgame Technique",
            fen = "4K3/4P2k/8/8/8/8/r7/3R4 w - - 0 1",
            solutionUci = "d1d4",
            prompt = "White to move · Build the 4th-rank bridge"
        ),
        TacticalDrill(
            id = "drill_opposition",
            title = "King Opposition",
            category = "Endgame Technique",
            fen = "8/8/3k4/8/4K3/3P4/8/8 w - - 0 1",
            solutionUci = "e4d4",
            prompt = "White to move · Step ahead of the pawn to seize opposition"
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
            fen = "r1b2rk1/ppp2ppp/3q4/8/8/3B4/PPP2PPP/R2Q1RK1 w - - 0 10",
            solutionUci = "d3h7",
            prompt = "White to move · Check with the bishop to win the queen"
        )
    )
}
