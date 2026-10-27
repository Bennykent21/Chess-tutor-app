package com.chesstutor.app.data.diagnostics

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Crashes recorded on the phone (plan Phase 6, D7: no server). When the app crashes, the
 * error and its stack trace are written to a small file the player can read and choose to
 * share from Settings > Your data. Nothing is sent anywhere by itself.
 */
class CrashLog(private val file: File) {

    /** The recorded crashes, oldest first, as plain text; empty when there are none. */
    fun read(): String = runCatching { if (file.exists()) file.readText() else "" }.getOrDefault("")

    /** How many crashes are recorded. */
    fun count(): Int = read().split(HEADER).size - 1

    fun clear() {
        runCatching { file.delete() }
    }

    /** Adds one crash, keeping only the newest [MAX_CRASHES] so the file stays small. */
    fun record(thread: String, error: Throwable, at: Long, about: String) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString().take(MAX_TRACE_CHARS)
        val entry = buildString {
            append(HEADER).append(' ').append(timeLabel(at)).append(" ===\n")
            append(about).append('\n')
            append("Thread: ").append(thread).append('\n')
            append(trace).append('\n')
        }
        val kept = read().split(HEADER).drop(1).takeLast(MAX_CRASHES - 1).joinToString("") { HEADER + it }
        runCatching { file.writeText(kept + entry) }
    }

    companion object {
        const val FILE_NAME = "crash-log.txt"
        const val MAX_CRASHES = 5
        const val MAX_TRACE_CHARS = 8_000
        private const val HEADER = "=== Crash"

        fun of(context: Context): CrashLog = CrashLog(File(context.applicationContext.filesDir, FILE_NAME))

        /**
         * Records any crash on any thread before the system's own handler closes the app.
         * Safe to call more than once: only the first call installs the handler.
         */
        fun install(context: Context, appVersion: String) {
            if (installed) return
            installed = true
            val log = of(context)
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                runCatching { log.record(thread.name, error, System.currentTimeMillis(), about(appVersion)) }
                previous?.uncaughtException(thread, error)
            }
        }

        @Volatile
        private var installed = false

        /** The app and phone a crash happened on: version, Android version and model. */
        fun about(appVersion: String): String =
            "Chess Tutor $appVersion · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"

        private fun timeLabel(at: Long): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(at))
    }
}
