package com.chesstutor.app.engine

data class AnalysisRequest(
    val requestId: Int,
    val fen: String,
    val movetimeMs: Int? = null,
    val depth: Int? = null,
)

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
