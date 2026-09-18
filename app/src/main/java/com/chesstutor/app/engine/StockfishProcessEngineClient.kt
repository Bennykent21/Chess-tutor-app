package com.chesstutor.app.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedWriter
import java.io.File

class StockfishProcessEngineClient(
    private val binaryPath: String,
    private val fallbackClient: EngineClient = LocalFallbackEngineClient()
) : EngineClient {
    val targetPath: String get() = binaryPath
    var lastStartupError: String? = null
        private set

    private var process: Process? = null
    private var stdinWriter: BufferedWriter? = null
    private var readerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val analysisMutex = Mutex()

    private var activeRequest: AnalysisRequest? = null
    private var pendingResult: CompletableDeferred<PositionAnalysis>? = null
    private var latestInfo: UciInfoLine? = null
    private val uciWaiters = ArrayDeque<CompletableDeferred<Unit>>()
    private val readyWaiters = ArrayDeque<CompletableDeferred<Unit>>()
    private var engineIdName: String = "Stockfish"

    val isAlive: Boolean
        get() = process?.isAlive == true

    override suspend fun initialize() = withContext(Dispatchers.IO) {
        val file = File(binaryPath)
        if (!file.exists()) {
            val err = "Stockfish binary not found at $binaryPath"
            lastStartupError = err
            throw IllegalStateException(err)
        }
        if (!file.canExecute()) {
            val err = "Stockfish binary exists but does not have execute permission at $binaryPath"
            lastStartupError = err
            throw IllegalStateException(err)
        }

        try {
            val proc = ProcessBuilder(binaryPath).redirectErrorStream(true).start()
            process = proc
            stdinWriter = proc.outputStream.bufferedWriter()
            readerJob = scope.launch { readLoop() }

            val uciReady = CompletableDeferred<Unit>()
            uciWaiters.add(uciReady)
            if (!send("uci")) {
                throw IllegalStateException("Failed to send UCI initialization command")
            }

            val uciOk = withTimeoutOrNull(4000) { uciReady.await() }
            if (uciOk == null) {
                val err = "Stockfish process started, but did not respond with uciok within 4000ms"
                lastStartupError = err
                dispose()
                throw IllegalStateException(err)
            }

            val ready = CompletableDeferred<Unit>()
            readyWaiters.add(ready)
            if (!send("isready")) {
                throw IllegalStateException("Failed to send isready command")
            }

            val isReady = withTimeoutOrNull(4000) { ready.await() }
            if (isReady == null) {
                val err = "Stockfish process started, but did not respond to isready within 4000ms"
                lastStartupError = err
                dispose()
                throw IllegalStateException(err)
            }
            lastStartupError = null
        } catch (e: Exception) {
            val err = "${e::class.java.simpleName}: ${e.message}"
            lastStartupError = err
            dispose()
            throw e
        }
    }

    suspend fun ensureInitialized(): Boolean = withContext(Dispatchers.IO) {
        if (isAlive) return@withContext true
        try {
            initialize()
            isAlive
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Calibrates engine strength using official UCI_LimitStrength + UCI_Elo.
     * Stockfish supports UCI_Elo in range 1320..3190. For ratings below 1320,
     * it falls back to Skill Level 0..5 to avoid the artificial 1320 floor.
     */
    override suspend fun setStrengthRating(elo: Int) = withContext(Dispatchers.IO) {
        if (!isAlive) return@withContext
        if (elo >= 1320) {
            send("setoption name UCI_LimitStrength value true")
            send("setoption name UCI_Elo value ${elo.coerceIn(1320, 3190)}")
        } else {
            // For sub-1320, disable Elo mode and use Skill Level 0..5
            send("setoption name UCI_LimitStrength value false")
            val skillLevel = ((elo - 400).coerceAtLeast(0) / 180).coerceIn(0, 5)
            send("setoption name Skill Level value $skillLevel")
        }
    }

    private suspend fun readLoop() {
        try {
            process?.inputStream?.bufferedReader()?.useLines { lines ->
                lines.forEach { line -> handleLine(line) }
            }
        } catch (_: Exception) {
            // Process terminated or pipe closed
        } finally {
            // If process terminated mid-search, fail-over active request to fallback engine immediately
            val pending = pendingResult
            val req = activeRequest
            if (pending != null && pending.isActive && req != null) {
                try {
                    val fallbackResult = fallbackClient.analyze(req)
                    pending.complete(fallbackResult)
                } catch (e: Exception) {
                    pending.completeExceptionally(e)
                }
            }
            pendingResult = null
            activeRequest = null
        }
    }

    private fun handleLine(line: String) {
        val trimmed = line.trim()
        if (trimmed.startsWith("id name ")) {
            engineIdName = trimmed.removePrefix("id name ").trim()
        }
        if (trimmed == "uciok") {
            uciWaiters.removeFirstOrNull()?.complete(Unit)
            return
        }
        if (trimmed == "readyok") {
            readyWaiters.removeFirstOrNull()?.complete(Unit)
            return
        }
        UciProtocol.parseInfoLine(line)?.let {
            latestInfo = it
            return
        }
        UciProtocol.parseBestMove(line)?.let { move ->
            val req = activeRequest
            val reqId = req?.requestId ?: 0
            pendingResult?.complete(
                PositionAnalysis(
                    requestId = reqId,
                    bestMoveUci = move,
                    centipawns = latestInfo?.centipawns,
                    mateInMoves = latestInfo?.mateInMoves,
                    principalVariation = latestInfo?.pv ?: emptyList(),
                    depth = latestInfo?.depth,
                )
            )
            activeRequest = null
            pendingResult = null
            latestInfo = null
        }
    }

    override suspend fun analyze(request: AnalysisRequest): PositionAnalysis =
        analysisMutex.withLock {
            withContext(Dispatchers.IO) {
        // If process is dead, fail over immediately to fallback without hanging
        if (process == null || process?.isAlive != true) {
            return@withContext fallbackClient.analyze(request)
        }

        check(pendingResult == null) { "Analysis already in progress" }
        activeRequest = request
        val deferred = CompletableDeferred<PositionAnalysis>()
        pendingResult = deferred
        latestInfo = null

        val ok = send("position fen ${request.fen}") &&
            if (request.depth != null) {
                send("go depth ${request.depth}")
            } else {
                val movetime = request.movetimeMs ?: 150
                send("go movetime $movetime")
            }

        if (!ok) {
            // Process pipe broken, fallback immediately
            activeRequest = null
            pendingResult = null
            return@withContext fallbackClient.analyze(request)
        }

        val timeoutMs = (request.movetimeMs?.toLong() ?: 1000L) + 2500L
        val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
        result ?: run {
            // Timed out: stop engine and return fallback
            send("stop")
            activeRequest = null
            pendingResult = null
            fallbackClient.analyze(request)
        }
            }
        }

    override suspend fun stop() {
        send("stop")
    }

    override suspend fun dispose() = withContext(Dispatchers.IO) {
        readerJob?.cancel()
        try {
            stdinWriter?.close()
        } catch (_: Exception) {}
        process?.destroy()
        process = null
    }

    private fun send(command: String): Boolean {
        return try {
            val writer = stdinWriter ?: return false
            writer.write(command)
            writer.newLine()
            writer.flush()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Executes a smoke test on the engine returning structured diagnostic metadata.
     */
    suspend fun runDiagnostics(movetimeMs: Int = 1000): EngineDiagnostics = withContext(Dispatchers.IO) {
        if (!isAlive) {
            ensureInitialized()
        }

        if (!isAlive) {
            return@withContext EngineDiagnostics(
                engineName = "Stockfish (Inactive)",
                isAlive = false,
                bestMove = "None",
                centipawns = null,
                depth = null,
                pv = "",
                latencyMs = 0,
                resolvedBinaryPath = binaryPath,
                launchError = lastStartupError ?: "Stockfish process is not alive"
            )
        }

        val startMs = System.currentTimeMillis()
        val analysis = analyze(
            AnalysisRequest(
                requestId = 9999,
                fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                movetimeMs = movetimeMs
            )
        )
        val latency = System.currentTimeMillis() - startMs
        EngineDiagnostics(
            engineName = engineIdName,
            isAlive = isAlive,
            bestMove = analysis.bestMoveUci,
            centipawns = analysis.centipawns,
            depth = analysis.depth,
            pv = analysis.principalVariation.joinToString(" "),
            latencyMs = latency,
            resolvedBinaryPath = binaryPath,
            launchError = null
        )
    }
}

data class EngineDiagnostics(
    val engineName: String,
    val isAlive: Boolean,
    val bestMove: String,
    val centipawns: Int?,
    val depth: Int?,
    val pv: String,
    val latencyMs: Long,
    val resolvedBinaryPath: String? = null,
    val launchError: String? = null
)
