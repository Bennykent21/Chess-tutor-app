package com.chesstutor.app.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalysisServiceTest {

    private val startFen =
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    @Test
    fun normalizesAndValidatesEngineResultAtServiceBoundary() = runTest {
        val engine = ControlledEngine()
        val service = AnalysisService(engine)

        val result = service.analyze(startFen, depth = 8)

        assertEquals("e2e4", result?.bestMoveUci)
        assertEquals(35, result?.centipawns)
        assertEquals(8, result?.depth)
    }

    @Test
    fun staleResultIsDiscardedWhenNewerRequestStarts() = runTest {
        val engine = ControlledEngine()
        val service = AnalysisService(engine)

        val first = async { service.analyze(startFen, depth = 6) }
        engine.awaitRequest(1)

        val second = async { service.analyze(startFen, depth = 8) }
        engine.awaitRequest(2)

        engine.complete(1, depth = 6)
        assertNull(first.await())

        engine.complete(2, depth = 8)
        assertEquals(8, second.await()?.depth)
    }

    @Test
    fun cancellationInvalidatesActiveRequest() = runTest {
        val engine = ControlledEngine()
        val service = AnalysisService(engine)

        val request = async { service.analyze(startFen, depth = 6) }
        engine.awaitRequest(1)

        service.cancelActiveAnalysis()

        engine.complete(1, depth = 6)
        assertNull(request.await())
    }

    private class ControlledEngine : EngineClient {
        private val requests = mutableMapOf<Int, CompletableDeferred<PositionAnalysis>>()

        override suspend fun initialize() = Unit

        override suspend fun analyze(request: AnalysisRequest): PositionAnalysis {
            val deferred = CompletableDeferred<PositionAnalysis>()
            requests[request.requestId] = deferred
            return deferred.await()
        }

        suspend fun awaitRequest(id: Int) {
            while (!requests.containsKey(id)) {
                kotlinx.coroutines.yield()
            }
        }

        fun complete(id: Int, depth: Int) {
            requests.remove(id)?.complete(
                PositionAnalysis(
                    requestId = id,
                    bestMoveUci = "e2e4",
                    centipawns = 35,
                    depth = depth,
                )
            )
        }

        override suspend fun stop() = Unit
        override suspend fun dispose() = Unit
    }
}
