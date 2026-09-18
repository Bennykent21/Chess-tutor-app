package com.chesstutor.app.engine

interface EngineClient {
    suspend fun initialize()
    suspend fun analyze(request: AnalysisRequest): PositionAnalysis
    suspend fun stop()
    suspend fun dispose()

    /**
     * Configure the engine's playing strength when supported.
     *
     * This is deliberately a best-effort capability: cloud engines and the
     * deterministic local fallback may ignore it. Implementations that expose
     * a real UCI strength control should apply it to the underlying engine.
     */
    suspend fun setStrengthRating(rating: Int) {
        // No-op by default.
    }
}
