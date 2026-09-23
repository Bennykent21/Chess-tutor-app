package com.chesstutor.app.data.model

/**
 * Curated placement assessment. Positions increase in difficulty so the result
 * estimates a starting training band without pretending to be an official
 * online chess rating.
 */
data class PlacementQuestion(
    val id: String,
    val fen: String,
    val expectedMoveUci: String,
    val targetRating: Int,
    val skill: String
)

object PlacementAssessment {
    val questions = listOf(
        PlacementQuestion(
            "placement_mate_1",
            "r1bqkb1r/pppp1ppp/2n5/4p3/2B1n3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 4",
            "f3f7", 250, "mate-in-one"
        ),
        PlacementQuestion(
            "placement_back_rank",
            "6k1/5ppp/8/8/8/8/5PPP/4R1K1 w - - 0 1",
            "e1e8", 600, "back-rank mate"
        ),
        PlacementQuestion(
            "placement_fork",
            "r1b1k3/pp3ppp/8/8/1n6/2N5/PP3PPP/R3K2R b q - 0 12",
            "b4c2", 1000, "fork"
        ),
        PlacementQuestion(
            "placement_pin",
            "4k3/8/8/8/1b6/2N5/8/R3K3 w - - 0 1",
            "a1c1", 1300, "pin"
        ),
        PlacementQuestion(
            "placement_lucena",
            "4K3/4P2k/8/8/8/8/r7/3R4 w - - 0 1",
            "d1d4", 1600, "rook endgame"
        ),
        PlacementQuestion(
            "placement_opposition",
            "8/8/8/4k3/8/4K3/8/8 w - - 0 1",
            "e3d3", 2000, "opposition"
        ),
        PlacementQuestion(
            "placement_greek_gift",
            "r1b2rk1/ppq1bppp/2n1p3/3pP3/3P4/2PB1N2/P4PPP/R1BQ1RK1 w - - 0 12",
            "d3h7", 2400, "attacking sacrifice"
        ),
        PlacementQuestion(
            "placement_discovered",
            "r1b2rk1/pp3ppp/2n5/1B1p4/3P4/5N2/PP1B1PPP/R2QR1K1 w - - 0 13",
            "b5c6", 3200, "discovered attack"
        )
    )

    fun estimateRating(correct: Int, total: Int): Int {
        if (total <= 0) return 250
        val ratio = (correct.toFloat() / total).coerceIn(0f, 1f)
        return (250 + (2950f * ratio)).toInt().coerceIn(250, 3200)
    }
}
