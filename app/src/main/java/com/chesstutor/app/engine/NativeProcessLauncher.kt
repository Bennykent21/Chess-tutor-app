package com.chesstutor.app.engine

import java.io.File

/**
 * Starts the native engine process. Abstracted so tests can drive the client with a
 * scripted fake engine instead of a real Stockfish binary.
 */
interface NativeProcessLauncher {
    /** Returns null when [path] can be started, otherwise why it cannot. */
    fun preflight(path: String): String?

    /** Size of the binary in bytes, or null if unknown. */
    fun sizeBytes(path: String): Long?

    fun start(path: String): Process
}

object SystemProcessLauncher : NativeProcessLauncher {
    override fun preflight(path: String): String? {
        val file = File(path)
        if (!file.exists()) return "Stockfish binary not found at $path"
        if (!file.canExecute()) return "Stockfish binary is not executable at $path"
        return null
    }

    override fun sizeBytes(path: String): Long? = File(path).takeIf { it.exists() }?.length()

    override fun start(path: String): Process =
        ProcessBuilder(path).redirectErrorStream(true).start()
}

/** Tuning for the native engine. */
data class NativeEngineConfig(
    /**
     * At most 2 (tuned on a Galaxy S22, 21 Oct 2026): 4 threads gave 527k nodes/s against
     * about 185k to 260k on one, but filled every fast core, so the UI stuttered and the
     * phone ran hot during analysis. Two threads leave fast cores free for the screen.
     */
    val threads: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 2),
    val hashMb: Int = 32,
    /** Generous: the first start on a slow phone also maps a large binary into memory. */
    val startupTimeoutMs: Long = 10_000L,
    /** After this many failures in one app session, stop trying and use the fallback. */
    val maxNativeFailures: Int = 2,
    /** Upper bound on MultiPV lines, to keep phone searches fast. */
    val maxMultiPv: Int = 5,
)
