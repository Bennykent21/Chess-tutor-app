package com.chesstutor.app.engine

data class AnalysisRequest(
    val requestId: Int,
    val fen: String,
    val movetimeMs: Int? = null,
    val depth: Int? = null,
    /** Number of candidate lines to return (UCI MultiPV). 1 = best move only. */
    val multiPv: Int = 1,
    /**
     * Playing strength for this one search, or null for full strength.
     * Only bot moves should set this: analysis, eval bars and game review must always
     * run at full strength.
     */
    val strengthElo: Int? = null,
)

/**
 * One candidate line from a MultiPV search. Scores use White's perspective once the
 * analysis has passed [EngineResultValidator].
 */
data class EngineLine(
    val rank: Int,
    val moves: List<String>,
    val centipawns: Int? = null,
    val mateInMoves: Int? = null,
    val depth: Int? = null,
) {
    val firstMove: String? get() = moves.firstOrNull()
}

/**
 * Engine analysis normalized at the application boundary.
 *
 * [centipawns] and [mateInMoves] use White's perspective:
 * positive means an advantage for White, negative means an advantage for Black.
 */
data class PositionAnalysis(
    val requestId: Int,
    val bestMoveUci: String,
    val centipawns: Int? = null,
    val mateInMoves: Int? = null,
    val principalVariation: List<String> = emptyList(),
    val depth: Int? = null,
    /** Candidate lines, best first. Empty unless the request asked for MultiPV > 1. */
    val lines: List<EngineLine> = emptyList(),
    /** Which engine produced this answer. */
    val source: EngineSource = EngineSource.UNKNOWN,
) {
    val isForcedMate get() = mateInMoves != null
}
