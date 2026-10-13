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
            "f3f7", 400, "mate-in-one"
        ),
        PlacementQuestion(
            "placement_back_rank",
            "6k1/5ppp/8/8/8/8/5PPP/4R1K1 w - - 0 1",
            "e1e8", 650, "back-rank mate"
        ),
        PlacementQuestion(
            "placement_fork",
            "r1b1k3/pp3ppp/8/8/1n6/2N5/PP3PPP/R3K2R b q - 0 12",
            "b4c2", 900, "fork"
        ),
        PlacementQuestion(
            "placement_pin",
            "4k3/8/8/8/1b6/2N5/8/R3K3 w - - 0 1",
            "a1c1", 1100, "pin"
        ),
        PlacementQuestion(
            "placement_lucena",
            "4K3/4P2k/8/8/8/8/r7/3R4 w - - 0 1",
            "d1d4", 1350, "rook endgame"
        ),
        PlacementQuestion(
            "placement_opposition",
            "8/8/3k4/8/4K3/3P4/8/8 w - - 0 1",
            "e4d4", 1500, "opposition"
        ),
        PlacementQuestion(
            "placement_greek_gift",
            "r1b2rk1/ppq1bppp/2n1p3/3pP3/3P4/2PB1N2/P4PPP/R1BQ1RK1 w - - 0 12",
            "d3h7", 1650, "attacking sacrifice"
        ),
        PlacementQuestion(
            "placement_discovered",
            "r1b2rk1/ppp2ppp/3q4/8/8/3B4/PPP2PPP/R2Q1RK1 w - - 0 10",
            "d3h7", 1800, "discovered attack"
        )
    )

    fun estimateRating(correct: Int, total: Int): Int {
        if (total <= 0) return 250
        val ratio = (correct.toFloat() / total).coerceIn(0f, 1f)
        return (250 + (2950f * ratio)).toInt().coerceIn(250, 3200)
    }

    fun estimateRatingFromSolved(solvedIds: Set<String>): Int {
        val solved = questions.filter { it.id in solvedIds }
        if (solved.isEmpty()) return 400
        val avgTarget = solved.map { it.targetRating }.average()
        val highestTarget = solved.maxOf { it.targetRating }
        val accuracyFactor = (solved.size.toDouble() / questions.size.toDouble()).coerceIn(0.25, 1.0)
        val raw = (avgTarget * 0.55 + highestTarget * 0.45) * (0.75 + 0.25 * accuracyFactor)
        return raw.toInt().coerceIn(400, 1800)
    }

    fun formatRatingBand(rating: Int): String {
        val low = ((rating - 100).coerceAtLeast(400) / 50) * 50
        val high = ((rating + 100).coerceAtMost(2000) / 50) * 50
        return "~$low–$high"
    }
}
