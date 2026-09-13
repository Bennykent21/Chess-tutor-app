package com.chesstutor.app.engine

interface EngineClient {
    suspend fun initialize()
    suspend fun analyze(request: AnalysisRequest): PositionAnalysis
    suspend fun stop()
    suspend fun dispose()
}
