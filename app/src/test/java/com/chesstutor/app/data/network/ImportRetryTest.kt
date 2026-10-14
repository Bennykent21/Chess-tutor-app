package com.chesstutor.app.data.network

import java.io.IOException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportRetryTest {

    private fun <T> run(block: suspend () -> T): T = runBlocking { block() }

    @Test
    fun succeedsOnFirstAttemptWithoutRetrying() {
        var calls = 0
        val result = run { retryImportRequest("games", baseDelayMs = 0) { calls++; "ok" } }
        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun retriesTransientFailuresThenSucceeds() {
        var calls = 0
        val result = run {
            retryImportRequest("games", baseDelayMs = 0) {
                calls++
                if (calls < 3) throw IOException("timeout") else "ok"
            }
        }
        assertEquals("ok", result)
        assertEquals(3, calls)
    }

    @Test
    fun fatalFailureIsNotRetried() {
        var calls = 0
        val e = runCatching {
            run { retryImportRequest("games", baseDelayMs = 0) { calls++; throw ImportFailedException("nope") } }
        }.exceptionOrNull()
        assertTrue(e is ImportFailedException)
        assertEquals("nope", e!!.message)
        assertEquals(1, calls)
    }

    @Test
    fun exhaustedNetworkFailureSaysSoInsteadOfReturningNothing() {
        var calls = 0
        val e = runCatching {
            run { retryImportRequest("your Chess.com games", baseDelayMs = 0) { calls++; throw IOException("reset") } }
        }.exceptionOrNull()
        assertEquals(3, calls)
        assertTrue(e is ImportFailedException)
        assertEquals("Couldn't download your Chess.com games. Check your connection and try again.", e!!.message)
    }

    @Test
    fun exhaustedRateLimitExplainsWhatToDo() {
        val e = runCatching {
            run { retryImportRequest("your Lichess games", baseDelayMs = 0) { throw RetryableHttpException(429) } }
        }.exceptionOrNull()
        assertTrue(e!!.message!!.contains("Too many requests"))
        assertTrue(e.message!!.contains("Wait a minute"))
    }

    @Test
    fun serverErrorMessageNamesTheStatus() {
        assertEquals("Couldn't load X (HTTP 503).", httpFailureMessage("X", 503))
        assertEquals("Couldn't find X (HTTP 404). Check the username.", httpFailureMessage("X", 404))
    }

    @Test
    fun cancellationIsNeverSwallowedOrRetried() {
        var calls = 0
        val e = runCatching {
            run { retryImportRequest("games", baseDelayMs = 0) { calls++; throw CancellationException("cancelled") } }
        }.exceptionOrNull()
        assertTrue(e is CancellationException)
        assertEquals(1, calls)
    }
}
