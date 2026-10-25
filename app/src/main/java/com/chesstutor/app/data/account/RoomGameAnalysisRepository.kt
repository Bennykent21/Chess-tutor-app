package com.chesstutor.app.data.account

import androidx.room.withTransaction
import com.chesstutor.app.data.local.AnalysisDao
import com.chesstutor.app.data.local.AnalysisResultEntity
import com.chesstutor.app.data.local.AppDatabase
import com.chesstutor.app.data.local.GameAnalysisEntity
import com.chesstutor.app.domain.account.AnalysisSummary
import com.chesstutor.app.domain.account.GameAnalysisRepository
import com.chesstutor.app.domain.account.PlyEval
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Game analysis cached in Room (`analysis_result` and `game_analysis`). */
class RoomGameAnalysisRepository(private val db: AppDatabase) : GameAnalysisRepository {
    private val dao: AnalysisDao get() = db.analysisDao()

    override fun observeEvals(gameId: String): Flow<List<PlyEval>> = dao.observeEvals(gameId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun evals(gameId: String): List<PlyEval> = dao.evals(gameId).map { it.toDomain() }

    override suspend fun saveEval(gameId: String, eval: PlyEval) = dao.upsertEval(eval.toEntity(gameId))

    override fun observeSummaries(): Flow<Map<String, AnalysisSummary>> =
        dao.observeSummaries().map { rows -> rows.associate { it.gameId to it.toDomain() } }

    override suspend fun saveSummary(summary: AnalysisSummary) = dao.upsertSummary(summary.toEntity())

    override suspend fun delete(gameId: String) {
        db.withTransaction {
            dao.deleteEvals(gameId)
            dao.deleteSummary(gameId)
        }
    }
}

/** Test double, and the fallback without a database. */
class InMemoryGameAnalysisRepository : GameAnalysisRepository {
    private val evals = MutableStateFlow<Map<String, Map<Int, PlyEval>>>(emptyMap())
    private val summaries = MutableStateFlow<Map<String, AnalysisSummary>>(emptyMap())

    override fun observeEvals(gameId: String): Flow<List<PlyEval>> = evals.map { all -> all[gameId].orEmpty().values.sortedBy { it.ply } }

    override suspend fun evals(gameId: String): List<PlyEval> = evals.value[gameId].orEmpty().values.sortedBy { it.ply }

    override suspend fun saveEval(gameId: String, eval: PlyEval) {
        val all = evals.value
        evals.value = all + (gameId to (all[gameId].orEmpty() + (eval.ply to eval)))
    }

    override fun observeSummaries(): Flow<Map<String, AnalysisSummary>> = summaries

    override suspend fun saveSummary(summary: AnalysisSummary) {
        summaries.value = summaries.value + (summary.gameId to summary)
    }

    override suspend fun delete(gameId: String) {
        evals.value = evals.value - gameId
        summaries.value = summaries.value - gameId
    }
}

private fun AnalysisResultEntity.toDomain() = PlyEval(
    ply = ply,
    centipawns = centipawns,
    mate = mate,
    bestMove = bestMove,
    line = line.split(' ').filter { it.isNotBlank() },
    depth = depth,
    lite = lite,
)

private fun PlyEval.toEntity(gameId: String) = AnalysisResultEntity(
    gameId = gameId,
    ply = ply,
    centipawns = centipawns,
    mate = mate,
    bestMove = bestMove,
    line = line.joinToString(" "),
    depth = depth,
    lite = lite,
)

private fun GameAnalysisEntity.toDomain() = AnalysisSummary(gameId, plies, whiteAccuracy, blackAccuracy, lite, analysedAt)

private fun AnalysisSummary.toEntity() = GameAnalysisEntity(gameId, plies, whiteAccuracy, blackAccuracy, lite, analysedAt)
