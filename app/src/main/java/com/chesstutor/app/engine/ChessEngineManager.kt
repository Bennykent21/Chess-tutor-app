package com.chesstutor.app.engine

import android.util.Log
import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.domain.MoveChoice
import com.chesstutor.app.core.Position
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * An evaluation of one position, from White's side: positive [centipawns] or [mateInMoves]
 * favour White, negative favour Black.
 */
data class EngineEvaluation(
    val centipawns: Int? = null,
    val mateInMoves: Int? = null,
    val depth: Int? = null,
    val bestMoveUci: String = "",
    val principalVariation: List<String> = emptyList(),
    val fen: String = ""
)

/**
 * The app's way to the engine: evaluations, best moves, bot moves and candidate lines, each
 * answered by Stockfish or, when it fails, by the fallbacks behind [engineClient].
 */
class ChessEngineManager(
    private val engineClient: EngineClient,
    private val analysisService: AnalysisService = AnalysisService(engineClient),
    private val localBotMoveSelector: LocalBotMoveSelector = LocalBotMoveSelector(),
    private val calculationDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
    /** Where start-up and the self-test block. Tests pass their own dispatcher, so no thread outlives a test. */
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Live engine status (for the "Lite engine" badge). Clients that cannot report health
     * (test fakes) show a neutral status.
     */
    val engineStatus: StateFlow<EngineStatus> =
        (engineClient as? EngineHealthReporter)?.status ?: MutableStateFlow(EngineStatus())

    /** Starts the engine; a failure is logged and the fallbacks answer instead. */
    suspend fun initialize() = withContext(ioDispatcher) {
        try {
            engineClient.initialize()
            runCatching { Log.i(TAG, "Chess engine initialized successfully.") }
        } catch (e: Exception) {
            runCatching { Log.e(TAG, "Failed to initialize engine: ${e.message}", e) }
        }
    }

    /** Evaluates the position [fen]; a finished game is scored without asking the engine. */
    suspend fun evaluatePosition(
        fen: String,
        depth: Int? = 10,
        movetimeMs: Int? = 500
    ): EngineEvaluation? = withContext(calculationDispatcher) {
        val chessPos = runCatching { ChessPosition(fen) }.getOrNull()
        if (chessPos == null || chessPos.isOver) {
            return@withContext when {
                chessPos?.isCheckmate == true -> EngineEvaluation(mateInMoves = if (chessPos.sideToMove == 'w') -1 else 1, fen = fen)
                chessPos?.isStalemate == true || chessPos?.isSeventyFiveMoveDraw == true -> EngineEvaluation(centipawns = 0, fen = fen)
                else -> null
            }
        }
        try {
            analysisService.analyze(fen = fen, depth = depth, movetimeMs = movetimeMs)?.let { analysis ->
                EngineEvaluation(
                    centipawns = analysis.centipawns,
                    mateInMoves = analysis.mateInMoves,
                    depth = analysis.depth,
                    bestMoveUci = analysis.bestMoveUci,
                    principalVariation = analysis.principalVariation,
                    fen = fen
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { Log.e(TAG, "Evaluation failed: ${e.message}", e) }
            null
        }
    }

    /** The best move in [fen] at full strength, or the first legal move if no engine answers; null when the game is over. */
    suspend fun calculateBestMove(
        fen: String,
        depth: Int? = 10,
        movetimeMs: Int? = 500
    ): MoveChoice? = withContext(calculationDispatcher) {
        val chessPos = runCatching { ChessPosition(fen) }.getOrNull() ?: return@withContext null
        if (chessPos.isOver) return@withContext null
        try {
            val analysis = analysisService.analyze(fen = fen, depth = depth, movetimeMs = movetimeMs)
            analysis?.let { chessPos.legalMoves.firstOrNull { it.uci == analysis.bestMoveUci } } ?: chessPos.legalMoves.firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { Log.w(TAG, "calculateBestMove failed, falling back to legal move: ${e.message}") }
            chessPos.legalMoves.firstOrNull()
        }
    }

    /**
     * A bot move at [elo] strength. The strength travels with this one request, so it never
     * weakens later analysis; without an engine, the local selector picks a move of that strength.
     */
    suspend fun calculateBotMove(fen: String, elo: Int): MoveChoice? = withContext(calculationDispatcher) {
        val chessPos = runCatching { ChessPosition(fen) }.getOrNull() ?: return@withContext null
        if (chessPos.isOver) return@withContext null
        val corePos = Position.tryFromFen(fen).getOrNull() ?: return@withContext null
        suspend fun local() = runCatching { localBotMoveSelector.selectMove(corePos, elo) }.getOrNull()
            ?.let { fallback -> chessPos.legalMoves.firstOrNull { it.uci == fallback.uci } }
        return@withContext try {
            runCatching { engineClient.setStrengthRating(elo) }
            val depth = when {
                elo < 800 -> 2
                elo < 1400 -> 4
                elo < 2000 -> 5
                else -> 6
            }
            val result = analysisService.analyze(fen = fen, depth = depth, movetimeMs = 800, strengthElo = elo)
            result?.let { analysis -> chessPos.legalMoves.firstOrNull { it.uci == analysis.bestMoveUci } }
                ?: local() ?: chessPos.legalMoves.firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            local() ?: chessPos.legalMoves.firstOrNull()
        }
    }

    /**
     * Candidate lines for [fen] (MultiPV), at full strength. Null if no engine answered.
     */
    suspend fun analyseLines(fen: String, multiPv: Int, movetimeMs: Int): PositionAnalysis? =
        withContext(calculationDispatcher) {
            val chessPos = runCatching { ChessPosition(fen) }.getOrNull() ?: return@withContext null
            if (chessPos.isOver) return@withContext null
            try {
                analysisService.analyze(fen = fen, depth = null, movetimeMs = movetimeMs, multiPv = multiPv)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { Log.w(TAG, "analyseLines failed: ${e.message}") }
                null
            }
        }

    /** Like [analyseLines], but the search stops at [depth] (full strength, shallow). */
    suspend fun analyseLinesToDepth(fen: String, multiPv: Int, depth: Int): PositionAnalysis? =
        withContext(calculationDispatcher) {
            val chessPos = runCatching { ChessPosition(fen) }.getOrNull() ?: return@withContext null
            if (chessPos.isOver) return@withContext null
            try {
                analysisService.analyze(fen = fen, depth = depth, movetimeMs = null, multiPv = multiPv)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { Log.w(TAG, "analyseLinesToDepth failed: ${e.message}") }
                null
            }
        }

    /** Fresh health check, or null when the engine client cannot describe itself. */
    suspend fun healthCheck(movetimeMs: Int = 1000): EngineHealthReport? =
        (engineClient as? EngineHealthReporter)?.healthCheck(movetimeMs)

    /**
     * Engine self-test shown in Settings. Reports which engine actually answered, the speed
     * the engine itself reported, and whether its neural network loaded.
     */
    suspend fun runSelfTest(): String = withContext(ioDispatcher) {
        try {
            val report = healthCheck(movetimeMs = 1000)
                ?: return@withContext "This engine cannot report its health."
            report.toReportText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Self-test error: ${e.message ?: e::class.java.simpleName}"
        }
    }

    companion object {
        private const val TAG = "ChessEngineManager"
    }
}
