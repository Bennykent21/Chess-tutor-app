package com.chesstutor.app.engine

import com.chesstutor.app.domain.MoveChoice
import kotlinx.coroutines.flow.StateFlow

/**
 * The one engine API for the new features (plan section 4.4). New screens depend on this
 * interface, never on a specific engine client, so tests can pass a fake.
 *
 * All scores use White's perspective. Every call runs at full strength except [botMove].
 */
interface EngineService {
    /** Live engine status, for example to show a "Lite engine" badge. */
    val status: StateFlow<EngineStatus>

    /** Best move for [fen], or null if the game is over or the position is invalid. */
    suspend fun bestMove(fen: String, movetimeMs: Int = 500): MoveChoice?

    /** Evaluation for [fen], or null if no engine could answer. */
    suspend fun evaluate(fen: String, movetimeMs: Int = 500): EngineEvaluation?

    /**
     * Up to [lines] candidate lines, best first. Engines that cannot do MultiPV (the
     * fallbacks) return a single line.
     */
    suspend fun analyse(fen: String, lines: Int = 3, movetimeMs: Int = 1000): List<EngineLine>

    /**
     * Up to [lines] candidate lines from a search stopped at [depth]. Bots use shallow depths
     * so they really do miss deeper tactics (plan D26). Engines without depth control answer
     * with a short timed search.
     */
    suspend fun analyseToDepth(fen: String, lines: Int, depth: Int): List<EngineLine> = analyse(fen, lines, 300)

    /** A move for a bot playing at [elo]. */
    suspend fun botMove(fen: String, elo: Int): MoveChoice?

    /** Runs a fresh health check, or null if the engine cannot describe itself. */
    suspend fun healthCheck(): EngineHealthReport?
}

/** [EngineService] backed by the app's [ChessEngineManager]. */
class ManagerEngineService(private val manager: ChessEngineManager) : EngineService {
    override val status: StateFlow<EngineStatus> get() = manager.engineStatus

    override suspend fun bestMove(fen: String, movetimeMs: Int): MoveChoice? =
        manager.calculateBestMove(fen = fen, depth = null, movetimeMs = movetimeMs)

    override suspend fun evaluate(fen: String, movetimeMs: Int): EngineEvaluation? =
        manager.evaluatePosition(fen = fen, depth = null, movetimeMs = movetimeMs)

    override suspend fun analyse(fen: String, lines: Int, movetimeMs: Int): List<EngineLine> {
        val analysis = manager.analyseLines(fen, lines.coerceAtLeast(1), movetimeMs) ?: return emptyList()
        return linesOf(analysis)
    }

    override suspend fun analyseToDepth(fen: String, lines: Int, depth: Int): List<EngineLine> {
        val analysis = manager.analyseLinesToDepth(fen, lines.coerceAtLeast(1), depth.coerceAtLeast(1)) ?: return emptyList()
        return linesOf(analysis)
    }

    override suspend fun botMove(fen: String, elo: Int): MoveChoice? =
        manager.calculateBotMove(fen = fen, elo = elo)

    override suspend fun healthCheck(): EngineHealthReport? = manager.healthCheck()

    companion object {
        /** The candidate lines of [analysis], or its main line when the engine gave none. */
        fun linesOf(analysis: PositionAnalysis): List<EngineLine> {
            if (analysis.lines.isNotEmpty()) return analysis.lines
            if (analysis.bestMoveUci.isBlank() || analysis.bestMoveUci == "0000") return emptyList()
            val moves = analysis.principalVariation.ifEmpty { listOf(analysis.bestMoveUci) }
            return listOf(
                EngineLine(
                    rank = 1,
                    moves = moves,
                    centipawns = analysis.centipawns,
                    mateInMoves = analysis.mateInMoves,
                    depth = analysis.depth,
                )
            )
        }
    }
}
