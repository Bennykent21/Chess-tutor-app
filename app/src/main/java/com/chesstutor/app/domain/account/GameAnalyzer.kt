package com.chesstutor.app.domain.account

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.engine.EngineService
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow

/** A finished analysis, kept per game for the games list (accuracy badges) and the dashboards. */
data class AnalysisSummary(
    val gameId: String,
    val plies: Int,
    val whiteAccuracy: Double?,
    val blackAccuracy: Double?,
    /** True when any position was judged by the Lite engine, so the review may be off. */
    val lite: Boolean,
    val analysedAt: Long,
) {
    fun accuracyFor(color: PieceColor): Double? = if (color == PieceColor.WHITE) whiteAccuracy else blackAccuracy
}

/** Where the engine's verdicts are cached (plan 7.4 `analysis_result`), so a review opens instantly. */
interface GameAnalysisRepository {
    fun observeEvals(gameId: String): Flow<List<PlyEval>>
    suspend fun evals(gameId: String): List<PlyEval>
    suspend fun saveEval(gameId: String, eval: PlyEval)
    fun observeSummaries(): Flow<Map<String, AnalysisSummary>>
    suspend fun saveSummary(summary: AnalysisSummary)

    /** Forgets a game's analysis (the game was deleted, or the player asked to analyse it again). */
    suspend fun delete(gameId: String)
}

/** The engine gave no answer for a position, so the analysis stopped there. */
class AnalysisFailedException(val ply: Int) : Exception("The engine did not answer at ply $ply")

/**
 * Background game analysis (plan 6.4): one full-strength search per position, saved as it
 * goes, so a stopped analysis resumes where it left off and a finished one is never redone.
 */
class GameAnalyzer(
    private val engine: EngineService,
    private val repository: GameAnalysisRepository,
    private val depth: Int = DEFAULT_DEPTH,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * Analyses every position of [game] not yet cached, reporting (positions done, total)
     * after each one, then saves and returns the summary. Cancelling the coroutine stops it
     * between positions.
     */
    suspend fun analyse(game: GameSummary, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): AnalysisSummary {
        val start = game.startFen ?: Position.STARTING_FEN
        val positions = GameReviewer.positions(start, game.moves)
        val cached = repository.evals(game.id).mapTo(HashSet()) { it.ply }
        positions.forEachIndexed { ply, position ->
            currentCoroutineContext().ensureActive()
            if (ply !in cached) {
                val eval = GameReviewer.terminalEval(ply, position) ?: search(ply, position) ?: throw AnalysisFailedException(ply)
                repository.saveEval(game.id, eval)
            }
            onProgress(ply + 1, positions.size)
        }
        val evals = repository.evals(game.id)
        val review = GameReviewer.review(start, game.moves, evals, game.bookPlies)
        val summary = AnalysisSummary(
            gameId = game.id,
            plies = review.totalPlies,
            whiteAccuracy = review.report(PieceColor.WHITE).accuracy,
            blackAccuracy = review.report(PieceColor.BLACK).accuracy,
            lite = evals.any { it.lite },
            analysedAt = clock(),
        )
        repository.saveSummary(summary)
        return summary
    }

    private suspend fun search(ply: Int, position: Position): PlyEval? {
        val fen = position.toFen()
        val line = engine.analyseToDepth(fen, lines = 1, depth = depth).firstOrNull()
            ?: engine.analyse(fen, lines = 1, movetimeMs = FALLBACK_MOVETIME_MS).firstOrNull()
            ?: return null
        val best = line.firstMove ?: return null
        return PlyEval(
            ply = ply,
            centipawns = line.centipawns,
            mate = line.mateInMoves,
            bestMove = best,
            line = line.moves.take(MAX_LINE),
            depth = line.depth,
            lite = engine.status.value.isDegraded,
        )
    }

    companion object {
        /** Deep enough to catch most tactics, quick enough on a phone (a few seconds per game). */
        const val DEFAULT_DEPTH = 14
        const val FALLBACK_MOVETIME_MS = 800
        /** Moves of the engine's line kept per position, enough to show the idea. */
        const val MAX_LINE = 8
    }
}
