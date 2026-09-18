package com.chesstutor.app.engine

data class AnalysisRequest(
    val requestId: Int,
    val fen: String,
    val movetimeMs: Int? = null,
    val depth: Int? = null,
)

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
) {
    val isForcedMate get() = mateInMoves != null
}
