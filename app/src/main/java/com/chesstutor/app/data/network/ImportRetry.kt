package com.chesstutor.app.data.network

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.IOException

/**
 * A failure that retrying will not fix (unknown user, access refused). Its message is written for
 * the player and is shown in the Review screen as-is.
 */
class ImportFailedException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** A temporary server-side problem (HTTP 429 or 5xx) that is worth retrying. */
class RetryableHttpException(val code: Int) : IOException("HTTP $code")

/**
 * Runs [attempt] up to [maxAttempts] times with a growing pause, retrying timeouts, dropped
 * connections, HTTP 429 and HTTP 5xx. An [ImportFailedException] is rethrown immediately, and when
 * every attempt fails an [ImportFailedException] with a player-readable message is thrown, so a
 * network problem can never turn into a silent "0 games imported".
 *
 * [what] completes the sentence "Couldn't load ...", e.g. "your Chess.com games for 2026-10".
 */
internal suspend fun <T> retryImportRequest(
    what: String,
    maxAttempts: Int = 3,
    baseDelayMs: Long = 1_000L,
    attempt: suspend () -> T
): T {
    var last: IOException? = null
    for (n in 1..maxAttempts) {
        currentCoroutineContext().ensureActive()
        try {
            return attempt()
        } catch (e: ImportFailedException) {
            throw e
        } catch (e: IOException) {
            last = e
        }
        if (n < maxAttempts) delay(baseDelayMs * n)
    }
    throw ImportFailedException(retryExhaustedMessage(what, last), last)
}

internal fun httpFailureMessage(what: String, code: Int): String = when (code) {
    404 -> "Couldn't find $what (HTTP 404). Check the username."
    403 -> "Access to $what was refused (HTTP 403)."
    429 -> "Too many requests while loading $what (HTTP 429). Wait a minute and try again."
    else -> "Couldn't load $what (HTTP $code)."
}

internal fun retryExhaustedMessage(what: String, cause: IOException?): String =
    if (cause is RetryableHttpException) {
        httpFailureMessage(what, cause.code)
    } else {
        "Couldn't download $what. Check your connection and try again."
    }
