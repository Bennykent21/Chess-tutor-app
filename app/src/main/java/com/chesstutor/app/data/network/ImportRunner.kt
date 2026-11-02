package com.chesstutor.app.data.network

import com.chesstutor.app.data.diagnostics.PerfLog
import com.chesstutor.app.data.diagnostics.ImportTiming
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.ImportProgress
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.repository.GameRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Why games are being imported, which decides what the player is told at the end. */
enum class ImportKind { LINK, MORE, BACKFILL }

/**
 * One import of a linked account's games (plan 6.4): up to [maxGames], older than [beforeMillis]
 * when set. "Import more" and carrying on a cut-short import set it to the oldest saved game,
 * so the games already here do not use up [maxGames] (Phase 7 import fix).
 */
data class ImportRequest(
    val kind: ImportKind,
    val platform: RatingPlatform,
    val username: String,
    val maxGames: Int,
    val beforeMillis: Long? = null,
)

/** How the current (or last) import is going. */
sealed interface ImportStatus {
    val request: ImportRequest

    data class Running(override val request: ImportRequest, val progress: ImportProgress) : ImportStatus

    data class Finished(
        override val request: ImportRequest,
        /** Games downloaded, including ones that were saved already. */
        val imported: Int,
        val incomplete: Int,
        val failedPeriods: Int,
        /** Why the download failed, or null. */
        val error: String?,
        /** The games came in but could not be saved. */
        val saveFailed: Boolean,
        /** Games that were not saved before this import. */
        val added: Int = imported,
    ) : ImportStatus {
        /** Everything asked for arrived: the saved import target can be forgotten. */
        val complete: Boolean get() = error == null && !saveFailed && failedPeriods == 0
    }
}

/**
 * Runs imports (plan Phase 6B): the app uses WorkManager so a long import keeps going when
 * the app leaves the screen; tests use [InProcessImportRunner]. [status] is the current or
 * last import, or null before any.
 */
interface ImportRunner {
    val status: StateFlow<ImportStatus?>

    /** Starts [request], replacing an import already running. */
    fun start(request: ImportRequest)

    fun cancel()

    /** Forgets a finished import once its result was shown. */
    fun acknowledge()

    /** True while an import is waiting or running. */
    suspend fun isRunning(): Boolean
}

/**
 * The download itself, shared by both runners: games are saved batch by batch as they
 * arrive, so the games list fills in while it runs and a stopped import keeps what it got.
 */
suspend fun runImport(
    request: ImportRequest,
    client: RatingApiClient,
    games: GameRepository,
    onProgress: (ImportProgress) -> Unit,
): ImportStatus.Finished {
    var last: ImportProgress? = null
    var saveFailed = false
    var added = 0
    val started = System.nanoTime()
    val result = client.fetchRecentGames(
        platform = request.platform,
        username = request.username,
        maxGames = request.maxGames,
        onProgress = { progress ->
            last = progress
            onProgress(progress)
        },
        onBatch = { batch: List<GameRecord> ->
            try {
                if (batch.isNotEmpty()) {
                    val before = games.existingIds(batch.map { it.id })
                    games.saveGames(batch)
                    added += batch.count { it.id !in before }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveFailed = true
            }
        },
        beforeMillis = request.beforeMillis,
    )
    result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
    val imported = result.getOrDefault(emptyList())
    PerfLog.import(ImportTiming(imported.size, (System.nanoTime() - started) / 1_000_000, request.platform.displayName))
    return ImportStatus.Finished(
        request = request,
        imported = imported.size,
        incomplete = imported.count { it.incompleteParse },
        failedPeriods = last?.failedPeriods ?: 0,
        error = result.exceptionOrNull()?.let { it.message?.takeIf(String::isNotBlank) ?: "Something went wrong while downloading your games." },
        saveFailed = saveFailed,
        added = if (saveFailed) 0 else added,
    )
}

/** Runs the import in [scope] (tests, and a fallback without WorkManager). */
class InProcessImportRunner(
    private val scope: CoroutineScope,
    private val games: GameRepository,
    private val client: RatingApiClient = RatingApiClient(),
) : ImportRunner {
    private val mutableStatus = MutableStateFlow<ImportStatus?>(null)
    override val status: StateFlow<ImportStatus?> = mutableStatus.asStateFlow()
    private var job: Job? = null

    override fun start(request: ImportRequest) {
        job?.cancel()
        mutableStatus.value = ImportStatus.Running(request, ImportProgress(targetCount = request.maxGames, currentPeriod = request.platform.displayName))
        job = scope.launch {
            mutableStatus.value = runImport(request, client, games) { mutableStatus.value = ImportStatus.Running(request, it) }
        }
    }

    override fun cancel() {
        job?.cancel()
        job = null
        mutableStatus.value = null
    }

    override fun acknowledge() {
        if (mutableStatus.value is ImportStatus.Finished) mutableStatus.value = null
    }

    override suspend fun isRunning(): Boolean = job?.isActive == true
}
