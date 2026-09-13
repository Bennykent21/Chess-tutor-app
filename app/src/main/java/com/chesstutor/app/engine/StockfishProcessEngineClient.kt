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
) : EngineClient {
    private var process: Process? = null
    private var stdinWriter: BufferedWriter? = null
    private var readerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var pendingRequestId: Int? = null
    private var pendingResult: CompletableDeferred<PositionAnalysis>? = null
    private var latestInfo: UciInfoLine? = null
    private val readyWaiters = ArrayDeque<CompletableDeferred<Unit>>()

    override suspend fun initialize() = withContext(Dispatchers.IO) {
        val file = File(binaryPath)
        if (!file.exists() || !file.canExecute()) {
            throw IllegalStateException("Stockfish binary not found or not executable at $binaryPath")
        }

        val proc = ProcessBuilder(binaryPath).redirectErrorStream(true).start()
        process = proc
        stdinWriter = proc.outputStream.bufferedWriter()
        readerJob = scope.launch { readLoop() }

        val ready = CompletableDeferred<Unit>()
        readyWaiters.add(ready)
        send("uci")
        send("isready")
        val isReady = withTimeoutOrNull(3000) { ready.await() }
        if (isReady == null) {
            throw IllegalStateException("Stockfish did not respond to isready within timeout")
        }
    }

    private suspend fun readLoop() {
        try {
            process?.inputStream?.bufferedReader()?.useLines { lines ->
                lines.forEach { line -> handleLine(line) }
            }
        } catch (_: Exception) {
            // Process terminated or pipe closed
        }
    }

    private fun handleLine(line: String) {
        val trimmed = line.trim()
        if (trimmed == "readyok") {
            readyWaiters.removeFirstOrNull()?.complete(Unit)
            return
        }
        UciProtocol.parseInfoLine(line)?.let {
            latestInfo = it
            return
        }
        UciProtocol.parseBestMove(line)?.let { move ->
            val id = pendingRequestId ?: return
            pendingResult?.complete(
                PositionAnalysis(
                    requestId = id,
                    bestMoveUci = move,
                    centipawns = latestInfo?.centipawns,
                    mateInMoves = latestInfo?.mateInMoves,
                    principalVariation = latestInfo?.pv ?: emptyList(),
                    depth = latestInfo?.depth,
                )
            )
            pendingRequestId = null
            pendingResult = null
            latestInfo = null
        }
    }

    override suspend fun analyze(request: AnalysisRequest): PositionAnalysis = withContext(Dispatchers.IO) {
        check(pendingResult == null) { "Analysis already in progress" }
        pendingRequestId = request.requestId
        val deferred = CompletableDeferred<PositionAnalysis>()
        pendingResult = deferred
        latestInfo = null

        send("position fen ${request.fen}")
        if (request.depth != null) {
            send("go depth ${request.depth}")
        } else {
            val movetime = request.movetimeMs ?: 150
            send("go movetime $movetime")
        }

        val timeoutMs = (request.movetimeMs?.toLong() ?: 1000L) + 2000L
        val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
        result ?: PositionAnalysis(
            requestId = request.requestId,
            bestMoveUci = "e2e4",
            centipawns = 0,
            depth = 1
        )
    }

    override suspend fun stop() {
        send("stop")
    }

    override suspend fun dispose() = withContext(Dispatchers.IO) {
        readerJob?.cancel()
        stdinWriter?.close()
        process?.destroy()
        process = null
    }

    private fun send(command: String) {
        stdinWriter?.apply {
            write(command)
            newLine()
            flush()
        }
    }
}
