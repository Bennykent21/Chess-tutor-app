package com.chesstutor.app.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedWriter
import java.io.File

class StockfishProcessEngineClient(
    private val binaryPath: String,
    private val fallbackClient: EngineClient = LocalFallbackEngineClient()
) : EngineClient {
    private var process: Process? = null
    private var stdinWriter: BufferedWriter? = null
    private var readerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var activeRequest: AnalysisRequest? = null
    private var pendingResult: CompletableDeferred<PositionAnalysis>? = null
    private var latestInfo: UciInfoLine? = null
    private val readyWaiters = ArrayDeque<CompletableDeferred<Unit>>()
    private var engineIdName: String = "Stockfish"

    val isAlive: Boolean
        get() = process?.isAlive == true

    override suspend fun initialize() = withContext(Dispatchers.IO) {
        val file = File(binaryPath)
        if (!file.exists() || !file.canExecute()) {
            throw IllegalStateException("Stockfish binary not found or not executable at $binaryPath")
        }

        try {
            val proc = ProcessBuilder(binaryPath).redirectErrorStream(true).start()
            process = proc
            stdinWriter = proc.outputStream.bufferedWriter()
            readerJob = scope.launch { readLoop() }

            val ready = CompletableDeferred<Unit>()
            readyWaiters.add(ready)
            send("uci")
            send("isready")
            val isReady = withTimeoutOrNull(4000) { ready.await() }
            if (isReady == null) {
                dispose()
                throw IllegalStateException("Stockfish did not respond to isready within timeout")
            }
        } catch (e: Exception) {
            dispose()
            throw e
        }
    }

    /**
     * Calibrates engine strength using official UCI_LimitStrength + UCI_Elo.
     * Stockfish supports UCI_Elo in range 1320..3190. For ratings below 1320,
     * it falls back to Skill Level 0..5 to avoid the artificial 1320 floor.
     */
    suspend fun setElo(elo: Int) = withContext(Dispatchers.IO) {
        if (!isAlive) return@withContext
        if (elo >= 1320) {
            send("setoption name UCI_LimitStrength value true")
            send("setoption name UCI_Elo value ${elo.coerceIn(1320, 3190)}")
        } else {
            // For sub-1320, disable Elo mode and use Skill Level 0..4
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

    override suspend fun analyze(request: AnalysisRequest): PositionAnalysis = withContext(Dispatchers.IO) {
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
            stdinWriter?.apply {
                write(command)
                newLine()
                flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Executes a smoke test on the engine returning structured diagnostic metadata.
     */
    suspend fun runDiagnostics(movetimeMs: Int = 1000): EngineDiagnostics = withContext(Dispatchers.IO) {
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
            latencyMs = latency
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
    val latencyMs: Long
)
