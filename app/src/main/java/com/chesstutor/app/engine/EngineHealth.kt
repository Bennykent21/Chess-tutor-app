package com.chesstutor.app.engine

import kotlinx.coroutines.flow.StateFlow

/** Which engine produced an answer. */
enum class EngineSource(val label: String) {
    /** On-device Stockfish with its neural network. */
    NATIVE("Stockfish (on device)"),
    /** Free online evaluation service, used only when on-device Stockfish is unavailable. */
    CLOUD("Online engine"),
    /** The small built-in Kotlin engine: always available, much weaker. */
    LITE("Lite engine"),
    UNKNOWN("Unknown"),
}

/** Lifecycle of the native Stockfish process. */
enum class NativeEngineState {
    NOT_STARTED,
    STARTING,
    READY,
    /** Failed recently; the next request will try to start it again. */
    FAILED,
    /** Given up for this app session (binary missing, or it failed too many times). */
    DISABLED,
}

/** Live status for the UI, for example the "Lite engine" badge. */
data class EngineStatus(
    val nativeState: NativeEngineState = NativeEngineState.NOT_STARTED,
    val lastAnswerSource: EngineSource = EngineSource.UNKNOWN,
    val problem: String? = null,
) {
    /** True when answers are coming from a weaker substitute instead of on-device Stockfish. */
    val isDegraded: Boolean
        get() = nativeState == NativeEngineState.DISABLED ||
            nativeState == NativeEngineState.FAILED ||
            lastAnswerSource == EngineSource.LITE ||
            lastAnswerSource == EngineSource.CLOUD
}

/** Facts gathered from the engine's own output. Nothing here is estimated. */
data class EngineTelemetry(
    /** From `id name`, for example "Stockfish 19". */
    val engineIdName: String? = null,
    /** From `info string NNUE evaluation using ...`; null if the engine never reported it. */
    val networkInfo: String? = null,
    /** Last `nps` the engine reported during a real search. */
    val lastNps: Long? = null,
    val lastNodes: Long? = null,
    val lastDepth: Int? = null,
    /** The engine's own error text (`CRITICAL ERROR` or the first `ERROR` line), if any. */
    val criticalError: String? = null,
    val processStarts: Int = 0,
    val unexpectedExits: Int = 0,
    val binarySizeBytes: Long? = null,
)

/** Result of an on-demand health check (Settings > engine self-test). */
data class EngineHealthReport(
    val answeredBy: EngineSource,
    val nativeState: NativeEngineState,
    val telemetry: EngineTelemetry,
    val bestMove: String?,
    val centipawns: Int?,
    val depth: Int?,
    val elapsedMs: Long,
    val problem: String?,
) {
    val networkLoaded: Boolean get() = telemetry.networkInfo != null

    val verdict: Verdict
        get() = when {
            answeredBy == EngineSource.NATIVE && networkLoaded -> Verdict.HEALTHY
            answeredBy == EngineSource.NATIVE -> Verdict.NATIVE_WITHOUT_NETWORK_REPORT
            else -> Verdict.USING_FALLBACK
        }

    enum class Verdict(val headline: String) {
        HEALTHY("On-device Stockfish is working, with its neural network."),
        NATIVE_WITHOUT_NETWORK_REPORT(
            "On-device Stockfish answered, but it did not report loading its neural network."
        ),
        USING_FALLBACK("On-device Stockfish is NOT working. Answers come from a weaker substitute."),
    }

    /** Human-readable report shown in Settings. Every number comes from the engine itself. */
    fun toReportText(): String = buildString {
        appendLine(verdict.headline)
        appendLine()
        appendLine("Answered by: ${answeredBy.label}")
        appendLine("Engine: ${telemetry.engineIdName ?: "not reported"}")
        appendLine("Network: ${telemetry.networkInfo ?: "not reported"}")
        appendLine("Native state: ${nativeState.name.lowercase().replace('_', ' ')}")
        telemetry.lastNps?.let { appendLine("Speed: ${formatThousands(it)} nodes/sec (reported by the engine)") }
        telemetry.lastNodes?.let { appendLine("Nodes searched: ${formatThousands(it)}") }
        appendLine("Depth: ${depth ?: telemetry.lastDepth ?: "-"}")
        appendLine("Best move from start: ${bestMove ?: "-"}" + (centipawns?.let { " ($it cp)" } ?: ""))
        appendLine("Time: $elapsedMs ms")
        telemetry.binarySizeBytes?.let { bytes ->
            // A binary well under 10 MB cannot contain Stockfish's network.
            val size = if (bytes >= 1024 * 1024) "${bytes / (1024 * 1024)} MB" else "${bytes / 1024} KB"
            appendLine("Binary size: $size")
        }
        appendLine("Process starts: ${telemetry.processStarts}, unexpected exits: ${telemetry.unexpectedExits}")
        telemetry.criticalError?.let { appendLine("Engine error: $it") }
        problem?.let { append("Problem: $it") }
    }.trimEnd()

    private fun formatThousands(value: Long): String = String.format(java.util.Locale.US, "%,d", value)
}

/** Implemented by engine clients that can describe their own health. */
interface EngineHealthReporter {
    val status: StateFlow<EngineStatus>
    suspend fun healthCheck(movetimeMs: Int = 1000): EngineHealthReport
}
