package com.chesstutor.app.engine

import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Application-facing analysis boundary.
 *
 * The ViewModel asks for analysis by position, not by engine implementation.
 * This service owns request IDs and stale-result protection. Engine clients are
 * responsible for validating their own output and returning scores normalized
 * to White's perspective.
 */
class AnalysisService(
    private val engine: EngineClient,
) {
    private val requestCounter = AtomicInteger(0)

    @Volatile
    private var latestRequestId: Int = 0

    @Volatile
    private var activeRequestId: Int? = null

    suspend fun analyze(
        fen: String,
        depth: Int? = null,
        movetimeMs: Int? = null,
    ): PositionAnalysis? {
        val requestId = requestCounter.incrementAndGet()
        val previousActiveRequest = activeRequestId
        latestRequestId = requestId
        activeRequestId = requestId

        // Do not leave an older engine search running while a newer request is
        // waiting for the result. This is especially important for serialized
        // process-backed engines, where an old search can otherwise block the
        // newer request.
        if (previousActiveRequest != null && previousActiveRequest != requestId) {
            runCatching { engine.stop() }
        }

        val request = AnalysisRequest(
            requestId = requestId,
            fen = fen,
            depth = depth,
            movetimeMs = movetimeMs,
        )

        return try {
            // EngineClient implementations validate UCI output and normalize
            // their score to White's perspective. Do not validate/normalize a
            // second time here: doing so would invert an already-normalized
            // Black-to-move score.
            val result = engine.analyze(request)

            // A newer request may have started while this engine call was in flight.
            // Never let an older answer overwrite newer UI state.
            if (requestId != latestRequestId) null else result
        } catch (cancelled: CancellationException) {
            runCatching { engine.stop() }
            throw cancelled
        } finally {
            if (activeRequestId == requestId) {
                activeRequestId = null
            }
        }
    }

    /** Invalidates the current request and stops any active engine search. */
    suspend fun cancelActiveAnalysis() {
        latestRequestId = requestCounter.incrementAndGet()
        runCatching { engine.stop() }
    }
}
