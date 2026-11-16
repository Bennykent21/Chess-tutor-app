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
 * Background game analysis (plan 6.4), in two passes (D86): a quick, shallow search of every
 * position so the review appears within moments, then a deeper one of each position, at most
 * [depth] and [movetimeMs], so the grades sharpen as it goes. A fixed time rather than a fixed
 * depth keeps a review to about the same length on every phone. Everything is saved as it goes,
 * so a stopped analysis resumes where it left off and a finished one is never redone.
 */
class GameAnalyzer(
    private val engine: EngineService,
    private val repository: GameAnalysisRepository,
    private val depth: Int = DEFAULT_DEPTH,
    private val movetimeMs: Int = DEFAULT_MOVETIME_MS,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Where the opening's positions may already be evaluated deeply (Lichess), or null. */
    private val cloud: CloudEvals? = null,
) {
    /** Positions Stockfish searched in the last [analyse] (cached ones are not counted), and how long that took. */
    var lastSearched: Int = 0
        private set
    var lastSearchMillis: Long = 0
        private set

    /**
     * Analyses [game], reporting (positions done, total, deeper pass) after each position, then
     * saves and returns the summary. The summary is saved only when both passes are done, so an
     * unfinished review is picked up again. Cancelling the coroutine stops it between positions.
     */
    suspend fun analyse(game: GameSummary, onProgress: (done: Int, total: Int, deeper: Boolean) -> Unit = { _, _, _ -> }): AnalysisSummary {
        lastSearched = 0
        lastSearchMillis = 0
        val start = game.startFen ?: Position.STARTING_FEN
        val positions = GameReviewer.positions(start, game.moves)
        if (cloud != null && start == Position.STARTING_FEN) lookUpTheOpening(game.id, positions)
        val cached = repository.evals(game.id).mapTo(HashSet()) { it.ply }
        positions.forEachIndexed { ply, position ->
            currentCoroutineContext().ensureActive()
            if (ply !in cached) {
                val eval = GameReviewer.terminalEval(ply, position) ?: timed { search(ply, position, QUICK_DEPTH, QUICK_MOVETIME_MS) } ?: throw AnalysisFailedException(ply)
                repository.saveEval(game.id, eval)
            }
            onProgress(ply + 1, positions.size, false)
        }
        if (depth > QUICK_DEPTH) {
            val shallow = repository.evals(game.id).associateBy { it.ply }
            positions.forEachIndexed { ply, position ->
                currentCoroutineContext().ensureActive()
                val old = shallow[ply]
                if (old != null && needsDeeperLook(old)) {
                    val eval = timed { search(ply, position, depth, movetimeMs) }
                    // A Lite answer never replaces Stockfish's, however shallow.
                    if (eval != null && (!eval.lite || old.lite)) repository.saveEval(game.id, eval)
                }
                onProgress(ply + 1, positions.size, true)
            }
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

    /**
     * Saves cloud evaluations from the start until the first position the cloud does not hold (or
     * holds too shallow): past that, a game's own positions are almost never there.
     */
    private suspend fun lookUpTheOpening(gameId: String, positions: List<Position>) {
        val cloud = cloud ?: return
        val saved = repository.evals(gameId).associateBy { it.ply }
        for (ply in 0 until minOf(positions.size, MAX_CLOUD_PLIES)) {
            currentCoroutineContext().ensureActive()
            val old = saved[ply]
            if (old != null && !needsDeeperLook(old)) continue
            if (GameReviewer.terminalEval(ply, positions[ply]) != null) return
            val eval = cloud.lookup(ply, positions[ply].toFen())?.takeIf { (it.depth ?: 0) >= depth } ?: return
            repository.saveEval(gameId, eval)
        }
    }

    private suspend fun timed(search: suspend () -> PlyEval?): PlyEval? {
        val started = System.nanoTime()
        return search().also {
            if (it != null) lastSearched++
            lastSearchMillis += (System.nanoTime() - started) / 1_000_000
        }
    }

    private suspend fun search(ply: Int, position: Position, depth: Int, movetimeMs: Int): PlyEval? {
        val fen = position.toFen()
        // Two lines: the second shows whether the best move was the only good one ("Great").
        val lines = engine.analyseWithin(fen, lines = 2, depth = depth, movetimeMs = movetimeMs).ifEmpty { engine.analyse(fen, lines = 2, movetimeMs = FALLBACK_MOVETIME_MS) }
        val line = lines.firstOrNull() ?: return null
        val second = lines.getOrNull(1)
        val best = line.firstMove ?: return null
        return PlyEval(
            ply = ply,
            centipawns = line.centipawns,
            mate = line.mateInMoves,
            bestMove = best,
            line = line.moves.take(MAX_LINE),
            depth = line.depth,
            lite = engine.status.value.isDegraded,
            secondCentipawns = second?.centipawns.takeIf { second?.mateInMoves == null },
            secondMate = second?.mateInMoves,
        )
    }

    companion object {
        /**
         * Depth 18 graded about 1 move in 21 differently from a depth-24 search where depth 14 got 1 in 8
         * wrong (ReviewDepthTest, Phase 13B); depth 20 took twice as long and was no better.
         */
        const val DEFAULT_DEPTH = 18
        /** The deeper pass's time per position: a phone that cannot reach [DEFAULT_DEPTH] in it stops a little short. */
        const val DEFAULT_MOVETIME_MS = 1_500
        /** The quick pass: enough for a first review in moments. */
        const val QUICK_DEPTH = 10
        const val QUICK_MOVETIME_MS = 400
        /** Cloud lookups stop here at the latest: deeper into a game, positions are rarely held. */
        const val MAX_CLOUD_PLIES = 30

        /** True for a position the deeper pass still has to search: a quick or Lite answer, not a finished game. */
        fun needsDeeperLook(eval: PlyEval): Boolean = eval.bestMove.isNotEmpty() && (eval.lite || (eval.depth ?: 0) <= QUICK_DEPTH)
        const val FALLBACK_MOVETIME_MS = 800
        /** Moves of the engine's line kept per position, enough to show the idea. */
        const val MAX_LINE = 8
    }
}
