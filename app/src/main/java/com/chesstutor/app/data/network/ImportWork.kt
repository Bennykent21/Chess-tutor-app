package com.chesstutor.app.data.network

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chesstutor.app.data.model.ImportProgress
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Imports with WorkManager (plan Phase 6B): the download keeps running when the app leaves
 * the screen or is closed, and waits for the internet if it is off. The app follows the
 * work's progress and result through [status], also after it was restarted.
 */
class WorkImportRunner(context: Context, scope: CoroutineScope) : ImportRunner {
    private val work = WorkManager.getInstance(context.applicationContext)
    private val mutableStatus = MutableStateFlow<ImportStatus?>(null)
    override val status: StateFlow<ImportStatus?> = mutableStatus.asStateFlow()
    /** The finished import already shown, so it is not reported again. */
    private var acknowledged: java.util.UUID? = null

    init {
        scope.launch {
            work.getWorkInfosForUniqueWorkFlow(WORK_NAME).collect { infos ->
                val info = infos.lastOrNull()
                mutableStatus.value = info?.takeIf { it.id != acknowledged }?.let(::statusOf)
            }
        }
    }

    override fun start(request: ImportRequest) {
        val job = OneTimeWorkRequestBuilder<ImportWorker>()
            .setInputData(ImportWorker.input(request))
            .addTag(ImportWorker.tagOf(request))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, job)
    }

    override fun cancel() {
        work.cancelUniqueWork(WORK_NAME)
        mutableStatus.value = null
    }

    override fun acknowledge() {
        if (mutableStatus.value !is ImportStatus.Finished) return
        mutableStatus.value = null
        // Work results stay in WorkManager for a while; remember which one was shown.
        acknowledged = lastId
        work.pruneWork()
    }

    private var lastId: java.util.UUID? = null

    override suspend fun isRunning(): Boolean =
        work.getWorkInfosForUniqueWorkFlow(WORK_NAME).first().any { !it.state.isFinished }

    private fun statusOf(info: WorkInfo): ImportStatus? {
        lastId = info.id
        val request = ImportWorker.requestFromTags(info.tags) ?: return null
        return when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ImportStatus.Running(
                request,
                ImportProgress(targetCount = request.maxGames, currentPeriod = request.platform.displayName, phase = "Starting… (it waits for the internet if you are offline)"),
            )
            WorkInfo.State.RUNNING -> ImportStatus.Running(request, ImportWorker.progressOf(info.progress, request))
            WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED -> ImportWorker.finishedOf(info.outputData, request)
            WorkInfo.State.CANCELLED -> null
        }
    }

    companion object {
        const val WORK_NAME = "account-import"
    }
}

/** The background import itself: downloads and saves the games, reporting progress as it goes. */
class ImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val request = requestOf(inputData) ?: return Result.failure()
        val finished = runImport(request, RatingApiClient(), AppContainer.provideGameRepository(applicationContext)) { progress ->
            setProgressAsync(progressData(progress))
        }
        return Result.success(output(finished))
    }

    companion object {
        private const val KIND = "kind"
        private const val PLATFORM = "platform"
        private const val USERNAME = "username"
        private const val MAX = "max"
        private const val BEFORE = "before"
        private const val IMPORTED = "imported"
        private const val INCOMPLETE = "incomplete"
        private const val FAILED = "failed"
        private const val ERROR = "error"
        private const val SAVE_FAILED = "saveFailed"
        private const val TARGET = "target"
        private const val PERIOD = "period"
        private const val PHASE = "phase"
        private const val DONE = "done"

        fun input(request: ImportRequest): Data = workDataOf(
            KIND to request.kind.name,
            PLATFORM to request.platform.name,
            USERNAME to request.username,
            MAX to request.maxGames,
            BEFORE to (request.beforeMillis ?: -1L),
        )

        private const val TAG_PREFIX = "import|"

        /** The request written into a tag, so the app can read it back from the work's info at any stage. */
        fun tagOf(request: ImportRequest): String =
            listOf(request.kind.name, request.platform.name, request.maxGames, request.beforeMillis ?: -1L, request.username)
                .joinToString("|", prefix = TAG_PREFIX)

        fun requestFromTags(tags: Set<String>): ImportRequest? {
            val parts = tags.firstOrNull { it.startsWith(TAG_PREFIX) }?.removePrefix(TAG_PREFIX)?.split("|", limit = 5) ?: return null
            return runCatching {
                ImportRequest(
                    kind = ImportKind.valueOf(parts[0]),
                    platform = RatingPlatform.valueOf(parts[1]),
                    maxGames = parts[2].toInt(),
                    beforeMillis = parts[3].toLong().takeIf { it >= 0 },
                    username = parts[4],
                )
            }.getOrNull()
        }

        fun requestOf(source: Data): ImportRequest? {
            if (source.getString(USERNAME) == null) return null
            return runCatching {
                ImportRequest(
                    kind = ImportKind.valueOf(source.getString(KIND)!!),
                    platform = RatingPlatform.valueOf(source.getString(PLATFORM)!!),
                    username = source.getString(USERNAME)!!,
                    maxGames = source.getInt(MAX, 100),
                    beforeMillis = source.getLong(BEFORE, -1L).takeIf { it >= 0 },
                )
            }.getOrNull()
        }

        private fun progressData(progress: ImportProgress): Data = Data.Builder()
            .putInt(IMPORTED, progress.importedCount)
            .putInt(TARGET, progress.targetCount)
            .putString(PERIOD, progress.currentPeriod)
            .putString(PHASE, progress.phase)
            .putInt(FAILED, progress.failedPeriods)
            .build()

        fun progressOf(data: Data, request: ImportRequest): ImportProgress = ImportProgress(
            importedCount = data.getInt(IMPORTED, 0),
            targetCount = data.getInt(TARGET, request.maxGames),
            currentPeriod = data.getString(PERIOD) ?: request.platform.displayName,
            phase = data.getString(PHASE) ?: "Starting…",
            failedPeriods = data.getInt(FAILED, 0),
        )

        private fun output(finished: ImportStatus.Finished): Data = Data.Builder()
            .putBoolean(DONE, true)
            .putInt(IMPORTED, finished.imported)
            .putInt(INCOMPLETE, finished.incomplete)
            .putInt(FAILED, finished.failedPeriods)
            .putString(ERROR, finished.error)
            .putBoolean(SAVE_FAILED, finished.saveFailed)
            .build()

        fun finishedOf(data: Data, request: ImportRequest): ImportStatus.Finished = ImportStatus.Finished(
            request = request,
            imported = data.getInt(IMPORTED, 0),
            incomplete = data.getInt(INCOMPLETE, 0),
            failedPeriods = data.getInt(FAILED, 0),
            // A worker that crashed leaves no result.
            error = data.getString(ERROR) ?: if (!data.getBoolean(DONE, false)) "The import stopped before it finished." else null,
            saveFailed = data.getBoolean(SAVE_FAILED, false),
        )
    }
}
