package com.chesstutor.app.engine

import android.content.Context
import android.util.Log
import com.chesstutor.app.di.AppContainer
import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.domain.MoveChoice
import com.example.chess.core.PieceColor
import com.example.chess.core.Position
import com.example.chess.engine.BotStrength
import com.example.chess.engine.Evaluation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

/**
 * High-level evaluation representation designed to directly power UI components
 * such as the evaluation bar, coach commentary, and move analysis cards.
 *
 * All scores are normalized to White's perspective:
 * - Positive [centipawns] / [mateInMoves] indicates an advantage for White.
 * - Negative indicates an advantage for Black.
 */
data class EngineEvaluation(
    val centipawns: Int? = null,
    val mateInMoves: Int? = null,
    val depth: Int? = null,
    val bestMoveUci: String = "",
    val bestMoveSan: String? = null,
    val principalVariation: List<String> = emptyList(),
    val fen: String = ""
) {
    val isForcedMate: Boolean get() = mateInMoves != null

    /**
     * Formatted human-readable evaluation score (e.g. "+1.4", "-0.8", "0.0", "M2", "-M1").
     */
    val formattedScore: String
        get() {
            if (mateInMoves != null) {
                return if (mateInMoves > 0) "M$mateInMoves" else "-M${abs(mateInMoves)}"
            }
            val cp = centipawns ?: 0
            val pawns = cp / 100.0
            return if (pawns > 0) "+${String.format(Locale.US, "%.1f", pawns)}"
            else String.format(Locale.US, "%.1f", pawns)
        }

    /**
     * White's win/advantage percentage in range [0.04, 0.96] (or 0.98/0.02 for mates),
     * suitable for driving a vertical or horizontal evaluation bar in the UI.
     * 0.5f represents an equal position.
     */
    val winningPercentageWhite: Float
        get() {
            if (mateInMoves != null) {
                return if (mateInMoves > 0) 0.98f else 0.02f
            }
            val pct = WinProbability.fromCentipawns(centipawns ?: 0) / 100.0
            return pct.toFloat().coerceIn(0.04f, 0.96f)
        }

    /**
     * Returns the evaluation score from the perspective of the side to move.
     */
    fun scoreForTurn(isWhiteTurn: Boolean): Float {
        if (mateInMoves != null) {
            val base = if (mateInMoves > 0) 10000f else -10000f
            return if (isWhiteTurn) base else -base
        }
        val cp = centipawns ?: 0
        val raw = cp / 100f
        return if (isWhiteTurn) raw else -raw
    }

    /**
     * Returns centipawns relative to the specified player color.
     */
    fun centipawnsForPlayer(isWhite: Boolean): Int? {
        val cp = centipawns ?: return null
        return if (isWhite) cp else -cp
    }

    /**
     * Converts to the domain [Evaluation] model used by chess core components.
     */
    fun toCoreEvaluation(): Evaluation {
        return Evaluation(centipawns = centipawns, mateInMoves = mateInMoves)
    }
}

/**
 * State representation of the chess engine for UI observation.
 */
data class ChessEngineState(
    val isInitialized: Boolean = false,
    val isCalculating: Boolean = false,
    val currentEvaluation: EngineEvaluation? = null,
    val lastCalculatedMove: MoveChoice? = null,
    val activeElo: Int = 1200,
    val lastError: String? = null
)

/**
 * Manager class responsible for integrating the Stockfish chess engine,
 * orchestrating move calculations, producing evaluation scores to power the UI,
 * and managing fallbacks gracefully.
 */
class ChessEngineManager(
    val engineClient: EngineClient,
    val analysisService: AnalysisService = AnalysisService(engineClient),
    private val localBotMoveSelector: LocalBotMoveSelector = LocalBotMoveSelector(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val calculationDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default
) {
    private val _state = MutableStateFlow(ChessEngineState())
    val state: StateFlow<ChessEngineState> = _state.asStateFlow()

    val currentEvaluation: StateFlow<EngineEvaluation?> = MutableStateFlow<EngineEvaluation?>(null)
    private val _currentEvaluation = currentEvaluation as MutableStateFlow<EngineEvaluation?>

    val isCalculating: StateFlow<Boolean> = MutableStateFlow(false)
    private val _isCalculating = isCalculating as MutableStateFlow<Boolean>

    private var inFlightEvaluationJob: Job? = null

    /**
     * Initializes the underlying engine client.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            engineClient.initialize()
            _state.update { it.copy(isInitialized = true, lastError = null) }
            runCatching { Log.i(TAG, "Chess engine initialized successfully.") }
        } catch (e: Exception) {
            val errMsg = "Failed to initialize engine: ${e.message}"
            runCatching { Log.e(TAG, errMsg, e) }
            _state.update { it.copy(isInitialized = false, lastError = errMsg) }
        }
    }

    /**
     * Configures the playing strength (ELO rating) of the engine.
     */
    suspend fun setSkillLevel(elo: Int) {
        val clamped = elo.coerceIn(250, 3200)
        _state.update { it.copy(activeElo = clamped) }
        runCatching {
            engineClient.setStrengthRating(clamped)
        }.onFailure { err ->
            runCatching { Log.w(TAG, "Could not set engine strength rating: ${err.message}") }
        }
    }

    /**
     * Evaluates a chess position given its FEN string, returning an [EngineEvaluation]
     * normalized to White's perspective to power UI bars and indicators.
     */
    suspend fun evaluatePosition(
        fen: String,
        depth: Int = 10,
        movetimeMs: Int? = 500
    ): EngineEvaluation? = withContext(calculationDispatcher) {
        val chessPos = runCatching { ChessPosition(fen) }.getOrNull()
        if (chessPos == null || chessPos.isOver) {
            val eval = when {
                chessPos?.isCheckmate == true -> {
                    val mateScore = if (chessPos.sideToMove == 'w') -1 else 1
                    EngineEvaluation(mateInMoves = mateScore, fen = fen)
                }
                chessPos?.isStalemate == true || chessPos?.isSeventyFiveMoveDraw == true -> {
                    EngineEvaluation(centipawns = 0, fen = fen)
                }
                else -> null
            }
            _currentEvaluation.value = eval
            _state.update { it.copy(currentEvaluation = eval) }
            return@withContext eval
        }

        _isCalculating.value = true
        _state.update { it.copy(isCalculating = true) }

        try {
            val analysis = analysisService.analyze(fen = fen, depth = depth, movetimeMs = movetimeMs)
            if (analysis != null) {
                val matchingMove = chessPos.legalMoves.firstOrNull { it.uci == analysis.bestMoveUci }
                val eval = EngineEvaluation(
                    centipawns = analysis.centipawns,
                    mateInMoves = analysis.mateInMoves,
                    depth = analysis.depth,
                    bestMoveUci = analysis.bestMoveUci,
                    bestMoveSan = matchingMove?.san,
                    principalVariation = analysis.principalVariation,
                    fen = fen
                )
                _currentEvaluation.value = eval
                _state.update { it.copy(currentEvaluation = eval, isCalculating = false) }
                return@withContext eval
            } else {
                _state.update { it.copy(isCalculating = false) }
                return@withContext null
            }
        } catch (e: CancellationException) {
            _isCalculating.value = false
            _state.update { it.copy(isCalculating = false) }
            throw e
        } catch (e: Exception) {
            runCatching { Log.e(TAG, "Evaluation failed: ${e.message}", e) }
            _state.update { it.copy(isCalculating = false, lastError = e.message) }
            return@withContext null
        } finally {
            _isCalculating.value = false
            _state.update { it.copy(isCalculating = false) }
        }
    }

    /**
     * Starts an asynchronous evaluation of the position in the manager's coroutine scope,
     * automatically updating [currentEvaluation], [isCalculating], and [state] flows to power the UI.
     */
    fun startEvaluation(fen: String, depth: Int = 8, movetimeMs: Int? = 400): Job {
        inFlightEvaluationJob?.cancel()
        val job = scope.launch {
            evaluatePosition(fen, depth, movetimeMs)
        }
        inFlightEvaluationJob = job
        return job
    }

    /**
     * Calculates the best engine move for a given position.
     *
     * @param fen FEN representation of the board
     * @param depth Search depth limit (default 10)
     * @param movetimeMs Maximum search time limit in milliseconds (default 500)
     * @return [MoveChoice] containing UCI, SAN, and piece data, or null if position is finished
     */
    suspend fun calculateBestMove(
        fen: String,
        depth: Int = 10,
        movetimeMs: Int? = 500
    ): MoveChoice? = withContext(calculationDispatcher) {
        val chessPos = runCatching { ChessPosition(fen) }.getOrNull() ?: return@withContext null
        if (chessPos.isOver) return@withContext null

        _isCalculating.value = true
        _state.update { it.copy(isCalculating = true) }

        try {
            val analysis = analysisService.analyze(fen = fen, depth = depth, movetimeMs = movetimeMs)
            val move = if (analysis != null) {
                chessPos.legalMoves.firstOrNull { it.uci == analysis.bestMoveUci }
            } else null

            val chosen = move ?: chessPos.legalMoves.firstOrNull()
            if (chosen != null) {
                _state.update { it.copy(lastCalculatedMove = chosen, isCalculating = false) }
            }
            chosen
        } catch (e: CancellationException) {
            _isCalculating.value = false
            _state.update { it.copy(isCalculating = false) }
            throw e
        } catch (e: Exception) {
            runCatching { Log.w(TAG, "calculateBestMove failed, falling back to legal move: ${e.message}") }
            val fallbackMove = chessPos.legalMoves.firstOrNull()
            _state.update { it.copy(lastCalculatedMove = fallbackMove, isCalculating = false) }
            fallbackMove
        } finally {
            _isCalculating.value = false
            _state.update { it.copy(isCalculating = false) }
        }
    }

    /**
     * Calculates a bot move calibrated to a given ELO strength and difficulty.
     *
     * At Grandmaster/high-strength levels (ELO >= 2600), queries Stockfish directly for
     * deep tactical moves. At lower and intermediate levels, uses calibrated candidate
     * move selection with human-like blunder distributions.
     */
    suspend fun calculateBotMove(
        fen: String,
        elo: Int,
        botDifficulty: String = "Casual"
    ): MoveChoice? = withContext(calculationDispatcher) {
        val chessPos = runCatching { ChessPosition(fen) }.getOrNull() ?: return@withContext null
        if (chessPos.isOver) return@withContext null
        val corePos = Position.tryFromFen(fen).getOrNull() ?: return@withContext null

        return@withContext try {
            runCatching { engineClient.setStrengthRating(elo) }
            val depth = when {
                elo < 800 -> 2
                elo < 1400 -> 4
                elo < 2000 -> 5
                else -> 6
            }
            val result = analysisService.analyze(fen = fen, depth = depth, movetimeMs = 800)
            val matching = result?.let { analysis ->
                chessPos.legalMoves.firstOrNull { it.uci == analysis.bestMoveUci }
            }
            val chosen = matching ?: localBotMoveSelector.selectMove(corePos, elo).let { fallback ->
                chessPos.legalMoves.firstOrNull { it.uci == fallback.uci }
                    ?: chessPos.legalMoves.firstOrNull()
            }
            if (chosen != null) {
                _state.update { it.copy(lastCalculatedMove = chosen) }
            }
            chosen
        } catch (_: Exception) {
            runCatching {
                localBotMoveSelector.selectMove(corePos, elo)
            }.getOrNull()?.let { fallback ->
                chessPos.legalMoves.firstOrNull { it.uci == fallback.uci }
            } ?: chessPos.legalMoves.firstOrNull()
        }
    }

    /**
     * Cancels any in-flight move calculations or position evaluations and halts engine search.
     */
    suspend fun cancelCalculation() {
        inFlightEvaluationJob?.cancel()
        _isCalculating.value = false
        _state.update { it.copy(isCalculating = false) }
        runCatching { analysisService.cancelActiveAnalysis() }
        runCatching { engineClient.stop() }
    }

    /**
     * Halts engine search without destroying the process.
     */
    suspend fun stop() {
        cancelCalculation()
    }

    /**
     * Cleans up and releases engine resources.
     */
    suspend fun dispose() {
        cancelCalculation()
        runCatching { engineClient.dispose() }
        _state.update { it.copy(isInitialized = false) }
    }

    /**
     * Runs full engine diagnostics for UI diagnostics dialogs.
     */
    suspend fun runDiagnostics(movetimeMs: Int = 1000): EngineDiagnostics = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val res = analysisService.analyze(
                fen = ChessPosition.STARTING_FEN,
                movetimeMs = movetimeMs
            ) ?: throw IllegalStateException("Engine diagnostics result was null or superseded")
            val latency = System.currentTimeMillis() - start

            EngineDiagnostics(
                engineName = "Stockfish Engine",
                isAlive = true,
                bestMove = res.bestMoveUci,
                centipawns = res.centipawns,
                depth = res.depth,
                pv = res.principalVariation.joinToString(" "),
                latencyMs = latency,
                resolvedBinaryPath = "Configured engine chain",
                launchError = null
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            EngineDiagnostics(
                engineName = "Engine Error",
                isAlive = false,
                bestMove = "--",
                centipawns = null,
                depth = null,
                pv = "",
                latencyMs = latency,
                resolvedBinaryPath = "Unavailable",
                launchError = "${e::class.java.simpleName}: ${e.message}"
            )
        }
    }

    companion object {
        private const val TAG = "ChessEngineManager"

        /**
         * Convenience factory to create a [ChessEngineManager] backed by the application container.
         */
        fun create(context: Context): ChessEngineManager {
            val client = AppContainer.provideEngineClient(context)
            return ChessEngineManager(client)
        }
    }
}
