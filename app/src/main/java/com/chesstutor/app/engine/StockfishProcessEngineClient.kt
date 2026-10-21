package com.chesstutor.app.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedWriter
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Runs Stockfish as a child process and talks UCI to it over pipes.
 *
 * Failure handling, in order of preference:
 * 1. Native Stockfish answers.
 * 2. If it fails to start, or exits during a search, the request is answered by
 *    [fallbackClient] and the next request tries native again.
 * 3. After [NativeEngineConfig.maxNativeFailures] failures in one app session (or if the
 *    binary is missing or Android refuses to run it) native is disabled for the session,
 *    and [status] says so, so the UI can show a "Lite engine" badge.
 *
 * Positions are checked with [EngineFenGate] before they are sent, because Stockfish 19
 * terminates on any position it considers invalid.
 */
class StockfishProcessEngineClient(
    private val binaryPath: String,
    private val fallbackClient: EngineClient = LocalFallbackEngineClient(),
    private val launcher: NativeProcessLauncher = SystemProcessLauncher,
    private val config: NativeEngineConfig = NativeEngineConfig(),
) : EngineClient, EngineHealthReporter {

    val targetPath: String get() = binaryPath

    @Volatile
    var lastStartupError: String? = null
        private set

    /** One running engine process and the waiters that belong to it. */
    private class Session(val process: Process, val writer: BufferedWriter) {
        val uciWaiters = ConcurrentLinkedQueue<CompletableDeferred<Unit>>()
        val readyWaiters = ConcurrentLinkedQueue<CompletableDeferred<Unit>>()
        @Volatile var intentionalShutdown = false
        /** Set once this session's failure has been counted, so one crash counts once. */
        @Volatile var failureRecorded = false
        var readerJob: Job? = null
    }

    @Volatile
    private var session: Session? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val analysisMutex = Mutex()
    private val lifecycleMutex = Mutex()

    // Search state. Guarded by searchLock because the reader runs on its own thread.
    private val searchLock = Any()
    private var activeRequest: AnalysisRequest? = null
    private var pendingResult: CompletableDeferred<PositionAnalysis>? = null
    private val latestInfoByPv = HashMap<Int, UciInfoLine>()
    private var staleBestMovesToIgnore = 0

    // Options currently set inside the engine. Only touched while holding analysisMutex
    // or lifecycleMutex, and reset whenever a new process starts.
    private var appliedStrengthElo: Int? = null
    private var appliedMultiPv = 1

    @Volatile
    private var nativeFailures = 0

    @Volatile
    private var disabledReason: String? = null

    private val telemetryLock = Any()
    private var telemetry = EngineTelemetry()

    private val _status = MutableStateFlow(EngineStatus())
    override val status: StateFlow<EngineStatus> = _status.asStateFlow()

    /** True while a native Stockfish process is running and has not been disabled. */
    val isAlive: Boolean
        get() = disabledReason == null && session?.process?.let(::processIsAlive) == true

    fun currentTelemetry(): EngineTelemetry = synchronized(telemetryLock) { telemetry }

    // ---------------------------------------------------------------- lifecycle

    override suspend fun initialize() = lifecycleMutex.withLock {
        withContext(Dispatchers.IO) { startNativeLocked() }
    }

    /** Must be called while holding [lifecycleMutex]. Never throws. */
    private suspend fun startNativeLocked() {
        if (isAlive) return
        disabledReason?.let {
            fallbackClient.initialize()
            return
        }
        launcher.preflight(binaryPath)?.let { reason ->
            disable(reason)
            fallbackClient.initialize()
            return
        }

        shutdownSession()
        setNativeState(NativeEngineState.STARTING, problem = null)
        var started: Session? = null
        try {
            val process = launcher.start(binaryPath)
            val newSession = Session(process, process.outputStream.bufferedWriter())
            started = newSession
            session = newSession
            appliedStrengthElo = null
            appliedMultiPv = 1
            updateTelemetry {
                it.copy(
                    processStarts = it.processStarts + 1,
                    binarySizeBytes = launcher.sizeBytes(binaryPath),
                    criticalError = null,
                )
            }
            newSession.readerJob = scope.launch { readLoop(newSession) }

            if (!sendAndAwait(newSession, "uci", newSession.uciWaiters)) {
                throw IOException("Stockfish did not answer 'uci' within ${config.startupTimeoutMs} ms")
            }
            send(newSession, "setoption name Threads value ${config.threads}")
            send(newSession, "setoption name Hash value ${config.hashMb}")
            if (!sendAndAwait(newSession, "isready", newSession.readyWaiters)) {
                throw IOException("Stockfish did not answer 'isready' within ${config.startupTimeoutMs} ms")
            }
            lastStartupError = null
            setNativeState(NativeEngineState.READY, problem = null)
        } catch (e: Exception) {
            if (e is CancellationException) {
                shutdownSession()
                throw e
            }
            val message = e.message ?: e::class.java.simpleName
            shutdownSession()
            when {
                isPermissionProblem(e, message) -> disable("Android did not allow Stockfish to run: $message")
                // The reader already counted this failure (the process died during startup).
                started?.failureRecorded == true -> Unit
                else -> {
                    started?.failureRecorded = true
                    recordNativeFailure("Stockfish failed to start: $message")
                }
            }
            fallbackClient.initialize()
        }
    }

    override suspend fun dispose() = lifecycleMutex.withLock {
        withContext(Dispatchers.IO) { shutdownSession() }
    }

    private fun shutdownSession() {
        val old = session ?: return
        old.intentionalShutdown = true
        session = null
        runCatching {
            old.writer.write("quit")
            old.writer.newLine()
            old.writer.flush()
        }
        runCatching { old.writer.close() }
        runCatching { old.process.destroy() }
        old.readerJob?.cancel()
        failWaiters(old, IOException("Stockfish was shut down"))
        synchronized(searchLock) {
            pendingResult?.cancel()
            pendingResult = null
            activeRequest = null
            latestInfoByPv.clear()
            staleBestMovesToIgnore = 0
        }
    }

    // ---------------------------------------------------------------- strength

    /**
     * Stockfish strength is applied per request through [AnalysisRequest.strengthElo], so a
     * weak bot setting can never leak into analysis. This call only forwards the rating to
     * the fallback engine, which keeps a single global strength.
     */
    override suspend fun setStrengthRating(rating: Int) {
        runCatching { fallbackClient.setStrengthRating(rating) }
    }

    // ---------------------------------------------------------------- analysis

    override suspend fun analyze(request: AnalysisRequest): PositionAnalysis {
        // Anything Stockfish would reject never reaches it. The fallback engine still answers
        // positions our own chess core accepts, and throws for ones it cannot parse.
        if (EngineFenGate.rejectionReason(request.fen) != null) {
            return answerFromFallback(request)
        }
        if (!isAlive && disabledReason == null) {
            lifecycleMutex.withLock { withContext(Dispatchers.IO) { startNativeLocked() } }
        }
        val current = session
        if (!isAlive || current == null) return answerFromFallback(request)

        return analysisMutex.withLock {
            withContext(Dispatchers.IO) { searchNative(current, request) }
        }
    }

    private suspend fun searchNative(current: Session, request: AnalysisRequest): PositionAnalysis {
        if (session !== current || !processIsAlive(current.process)) return answerFromFallback(request)

        applyOptions(current, request)
        val deferred = CompletableDeferred<PositionAnalysis>()
        synchronized(searchLock) {
            activeRequest = request
            pendingResult = deferred
            latestInfoByPv.clear()
        }

        val goCommand = when {
            request.depth != null -> "go depth ${request.depth}"
            else -> "go movetime ${request.movetimeMs ?: DEFAULT_MOVETIME_MS}"
        }
        val sent = send(current, "position fen ${request.fen}") && send(current, goCommand)
        if (!sent) {
            clearSearchIf(deferred)
            return answerFromFallback(request)
        }

        val timeoutMs = when {
            request.movetimeMs != null && request.depth == null -> request.movetimeMs.toLong() + 3_000L
            request.depth != null -> (request.depth * 1_500L).coerceIn(5_000L, 30_000L)
            else -> 5_000L
        }

        val result = try {
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        } catch (cancelled: CancellationException) {
            // The caller gave up (for example a newer position arrived). Stop the search and
            // make sure its late bestmove cannot answer the next request.
            if (abandonSearch(deferred)) send(current, "stop")
            throw cancelled
        }
        if (result != null) return result

        // Timed out. If the answer raced in just now, use it.
        if (!abandonSearch(deferred)) return deferred.await()
        send(current, "stop")
        return answerFromFallback(request)
    }

    /**
     * Detaches [deferred] from the engine if it is still the active search, and arranges for
     * the engine's eventual bestmove to be ignored. Returns false if it already completed.
     */
    private fun abandonSearch(deferred: CompletableDeferred<PositionAnalysis>): Boolean =
        synchronized(searchLock) {
            if (pendingResult !== deferred) return@synchronized false
            pendingResult = null
            activeRequest = null
            latestInfoByPv.clear()
            staleBestMovesToIgnore++
            true
        }

    private fun clearSearchIf(deferred: CompletableDeferred<PositionAnalysis>) {
        synchronized(searchLock) {
            if (pendingResult === deferred) {
                pendingResult = null
                activeRequest = null
                latestInfoByPv.clear()
            }
        }
    }

    /** Sends strength and MultiPV options only when they change. The engine is idle here. */
    private fun applyOptions(current: Session, request: AnalysisRequest) {
        val wantedElo = request.strengthElo
        if (wantedElo != appliedStrengthElo) {
            when {
                wantedElo == null -> {
                    send(current, "setoption name UCI_LimitStrength value false")
                    send(current, "setoption name Skill Level value 20")
                }
                wantedElo >= MIN_UCI_ELO -> {
                    send(current, "setoption name UCI_LimitStrength value true")
                    send(current, "setoption name UCI_Elo value ${wantedElo.coerceIn(MIN_UCI_ELO, MAX_UCI_ELO)}")
                }
                else -> {
                    // Below Stockfish's UCI_Elo floor: use Skill Level 0..5 instead.
                    send(current, "setoption name UCI_LimitStrength value false")
                    send(current, "setoption name Skill Level value ${skillLevelFor(wantedElo)}")
                }
            }
            appliedStrengthElo = wantedElo
        }
        val wantedPv = request.multiPv.coerceIn(1, config.maxMultiPv)
        if (wantedPv != appliedMultiPv) {
            send(current, "setoption name MultiPV value $wantedPv")
            appliedMultiPv = wantedPv
        }
    }

    override suspend fun stop() {
        val current = session ?: return
        val searching = synchronized(searchLock) { pendingResult != null }
        if (searching) send(current, "stop")
    }

    // ---------------------------------------------------------------- health

    override suspend fun healthCheck(movetimeMs: Int): EngineHealthReport {
        // A manual check is a retry: forget earlier failures, unless the binary is unusable.
        if (launcher.preflight(binaryPath) == null) {
            nativeFailures = 0
            disabledReason = null
        }
        val start = System.currentTimeMillis()
        val outcome = runCatching {
            analyze(
                AnalysisRequest(
                    requestId = HEALTH_CHECK_REQUEST_ID,
                    fen = START_FEN,
                    movetimeMs = movetimeMs,
                )
            )
        }
        val elapsed = System.currentTimeMillis() - start
        val answer = outcome.getOrNull()
        val source = answer?.source ?: EngineSource.UNKNOWN
        val problem = outcome.exceptionOrNull()?.let { it.message ?: it::class.java.simpleName }
            ?: if (source != EngineSource.NATIVE) (disabledReason ?: lastStartupError) else null
        return EngineHealthReport(
            answeredBy = source,
            nativeState = status.value.nativeState,
            telemetry = currentTelemetry(),
            bestMove = answer?.bestMoveUci,
            centipawns = answer?.centipawns,
            depth = answer?.depth,
            elapsedMs = elapsed,
            problem = problem,
        )
    }

    // ---------------------------------------------------------------- reading output

    private fun readLoop(owner: Session) {
        try {
            owner.process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line -> handleLine(owner, line) }
            }
        } catch (_: Exception) {
            // Pipe closed: the process ended or was destroyed.
        } finally {
            onReaderFinished(owner)
        }
    }

    private fun onReaderFinished(owner: Session) {
        // Decide first, before waking anyone: did the engine die on its own?
        val diedOnItsOwn = !owner.intentionalShutdown && session === owner
        if (diedOnItsOwn) owner.failureRecorded = true
        failWaiters(owner, IOException("Stockfish process exited"))
        if (!diedOnItsOwn) return

        synchronized(searchLock) {
            // Never clear a newer session that may have started meanwhile.
            if (session === owner) session = null
        }
        runCatching { owner.writer.close() }
        updateTelemetry { it.copy(unexpectedExits = it.unexpectedExits + 1) }
        val critical = currentTelemetry().criticalError
        recordNativeFailure(
            if (critical != null) "Stockfish stopped: $critical" else "Stockfish stopped unexpectedly"
        )

        val (pending, request) = synchronized(searchLock) {
            val p = pendingResult
            val r = activeRequest
            pendingResult = null
            activeRequest = null
            latestInfoByPv.clear()
            staleBestMovesToIgnore = 0
            p to r
        }
        if (pending != null && pending.isActive && request != null) {
            scope.launch {
                try {
                    pending.complete(answerFromFallback(request))
                } catch (e: Exception) {
                    pending.completeExceptionally(e)
                }
            }
        }
    }

    private fun handleLine(owner: Session, line: String) {
        val trimmed = line.trim()
        when {
            trimmed.startsWith("id name ") ->
                updateTelemetry { it.copy(engineIdName = trimmed.removePrefix("id name ").trim()) }
            trimmed == "uciok" -> owner.uciWaiters.poll()?.complete(Unit)
            trimmed == "readyok" -> owner.readyWaiters.poll()?.complete(Unit)
            else -> {
                UciProtocol.parseInfoString(trimmed)?.let { text ->
                    onInfoString(text)
                    return
                }
                UciProtocol.parseInfoLine(trimmed)?.let { info ->
                    onInfo(info)
                    return
                }
                UciProtocol.parseBestMoveLine(trimmed)?.let(::onBestMove)
            }
        }
    }

    private fun onInfoString(text: String) {
        when {
            text.startsWith(NNUE_PREFIX) ->
                updateTelemetry { it.copy(networkInfo = text.removePrefix(NNUE_PREFIX).trim()) }
            text.startsWith("CRITICAL ERROR") ->
                updateTelemetry { it.copy(criticalError = text) }
            // Stockfish's "network not loaded" failure prints several ERROR lines before
            // exiting; the first one names the cause.
            text.startsWith("ERROR") ->
                updateTelemetry { if (it.criticalError == null) it.copy(criticalError = text) else it }
        }
    }

    private fun onInfo(info: UciInfoLine) {
        if (info.nps != null || info.nodes != null || info.depth != null) {
            updateTelemetry {
                it.copy(
                    lastNps = info.nps ?: it.lastNps,
                    lastNodes = info.nodes ?: it.lastNodes,
                    lastDepth = info.depth ?: it.lastDepth,
                )
            }
        }
        val scored = info.centipawns != null || info.mateInMoves != null
        if (!scored) return
        synchronized(searchLock) {
            if (activeRequest == null) return
            val existing = latestInfoByPv[info.multiPv]
            // Prefer exact scores; accept a bound only when nothing better is known yet.
            if (!info.isBound || existing == null || existing.isBound) {
                latestInfoByPv[info.multiPv] = info
            }
        }
    }

    private fun onBestMove(best: UciBestMove) {
        val captured = synchronized(searchLock) {
            if (staleBestMovesToIgnore > 0) {
                staleBestMovesToIgnore--
                return
            }
            val request = activeRequest ?: return
            val deferred = pendingResult ?: return
            val infos = latestInfoByPv.toMap()
            activeRequest = null
            pendingResult = null
            latestInfoByPv.clear()
            Triple(request, deferred, infos)
        }
        val (request, deferred, infos) = captured
        try {
            val main = infos[1]
            val lines = if (request.multiPv > 1) {
                infos.toSortedMap().map { (rank, info) ->
                    EngineLine(
                        rank = rank,
                        moves = info.pv,
                        centipawns = info.centipawns,
                        mateInMoves = info.mateInMoves,
                        depth = info.depth,
                    )
                }
            } else {
                emptyList()
            }
            val raw = PositionAnalysis(
                requestId = request.requestId,
                bestMoveUci = best.moveUci ?: NO_MOVE,
                centipawns = main?.centipawns,
                mateInMoves = main?.mateInMoves,
                principalVariation = main?.pv ?: emptyList(),
                depth = main?.depth,
                lines = lines,
                source = EngineSource.NATIVE,
            )
            val validated = EngineResultValidator.validate(
                request = request,
                analysis = raw,
                scorePerspective = EngineResultValidator.ScorePerspective.SIDE_TO_MOVE,
            )
            markAnswered(EngineSource.NATIVE)
            deferred.complete(validated)
        } catch (e: Exception) {
            deferred.completeExceptionally(e)
        }
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun answerFromFallback(request: AnalysisRequest): PositionAnalysis {
        val result = fallbackClient.analyze(request)
        val source = if (result.source == EngineSource.UNKNOWN) EngineSource.LITE else result.source
        markAnswered(source)
        return result.copy(source = source)
    }

    private suspend fun sendAndAwait(
        owner: Session,
        command: String,
        waiters: ConcurrentLinkedQueue<CompletableDeferred<Unit>>,
    ): Boolean {
        val waiter = CompletableDeferred<Unit>()
        waiters.add(waiter)
        if (!send(owner, command)) {
            waiters.remove(waiter)
            return false
        }
        return withTimeoutOrNull(config.startupTimeoutMs) { waiter.await() } != null
    }

    private fun send(owner: Session, command: String): Boolean = try {
        synchronized(owner.writer) {
            owner.writer.write(command)
            owner.writer.newLine()
            owner.writer.flush()
        }
        true
    } catch (_: Exception) {
        false
    }

    private fun failWaiters(owner: Session, error: Throwable) {
        generateSequence { owner.uciWaiters.poll() }.forEach { it.completeExceptionally(error) }
        generateSequence { owner.readyWaiters.poll() }.forEach { it.completeExceptionally(error) }
    }

    private fun recordNativeFailure(reason: String) {
        nativeFailures++
        lastStartupError = reason
        if (nativeFailures >= config.maxNativeFailures) {
            disable("$reason (stopped retrying after $nativeFailures failures this session)")
        } else {
            setNativeState(NativeEngineState.FAILED, problem = reason)
        }
    }

    private fun disable(reason: String) {
        disabledReason = reason
        lastStartupError = reason
        setNativeState(NativeEngineState.DISABLED, problem = reason)
    }

    private fun setNativeState(state: NativeEngineState, problem: String?) {
        _status.update { it.copy(nativeState = state, problem = problem) }
    }

    private fun markAnswered(source: EngineSource) {
        _status.update { it.copy(lastAnswerSource = source) }
    }

    private fun updateTelemetry(change: (EngineTelemetry) -> EngineTelemetry) {
        synchronized(telemetryLock) { telemetry = change(telemetry) }
    }

    private fun isPermissionProblem(error: Exception, message: String): Boolean =
        error is SecurityException ||
            message.contains("Permission denied", ignoreCase = true) ||
            message.contains("error=13") ||
            message.contains("EACCES", ignoreCase = true)

    // Process.isAlive needs API 26; exitValue() and its documented exception work on API 24.
    private fun processIsAlive(candidate: Process): Boolean = try {
        candidate.exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    }

    companion object {
        const val MIN_UCI_ELO = 1320
        const val MAX_UCI_ELO = 3190
        private const val DEFAULT_MOVETIME_MS = 150
        private const val HEALTH_CHECK_REQUEST_ID = 9_999
        private const val NO_MOVE = "0000"
        private const val NNUE_PREFIX = "NNUE evaluation using"
        private const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        /** Stockfish Skill Level (0..5) for ratings under the UCI_Elo floor. */
        fun skillLevelFor(rating: Int): Int = ((rating - 400).coerceAtLeast(0) / 180).coerceIn(0, 5)
    }
}

/** Kept for the existing diagnostics dialog. */
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
