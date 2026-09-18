package com.chesstutor.app.engine

import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Application-facing analysis boundary.
 *
 * The ViewModel asks for analysis by position, not by engine implementation.
 * This service owns request IDs, stale-result protection, score normalization,
 * and cancellation semantics. Engine failures still propagate so callers can
 * decide how to present them.
 */
class AnalysisService(
    private val engine: EngineClient,
) {
    private val requestCounter = AtomicInteger(0)

    @Volatile
    private var latestRequestId: Int = 0

    suspend fun analyze(
        fen: String,
        depth: Int? = null,
        movetimeMs: Int? = null,
    ): PositionAnalysis? {
        val requestId = requestCounter.incrementAndGet()
        latestRequestId = requestId

        val request = AnalysisRequest(
            requestId = requestId,
            fen = fen,
            depth = depth,
            movetimeMs = movetimeMs,
        )

        return try {
            val raw = engine.analyze(request)
            val validated = EngineResultValidator.validate(
                request = request,
                analysis = raw,
                scorePerspective = EngineResultValidator.ScorePerspective.WHITE,
            )

            // A newer request may have started while this engine call was in flight.
            // Never let an older answer overwrite newer UI state.
            if (requestId != latestRequestId) null else validated
        } catch (cancelled: CancellationException) {
            runCatching { engine.stop() }
            throw cancelled
        }
    }

    /** Invalidates the current request and stops any active engine search. */
    suspend fun cancelActiveAnalysis() {
        latestRequestId = requestCounter.incrementAndGet()
        runCatching { engine.stop() }
    }
}
