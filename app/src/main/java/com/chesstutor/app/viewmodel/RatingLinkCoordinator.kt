package com.chesstutor.app.viewmodel

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.ImportDepth
import com.chesstutor.app.data.model.ImportProgress
import com.chesstutor.app.data.model.LinkedChessProfile
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.chesstutor.app.data.network.ImportKind
import com.chesstutor.app.data.network.ImportRequest
import com.chesstutor.app.data.network.ImportRunner
import com.chesstutor.app.data.network.ImportStatus
import com.chesstutor.app.data.network.InProcessImportRunner
import com.chesstutor.app.data.network.RatingApiClient
import com.chesstutor.app.data.repository.GameRepository
import com.chesstutor.app.data.repository.InMemoryGameRepository
import com.chesstutor.app.data.repository.RatingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Linking Chess.com or Lichess and bringing in the games (plan 6.4). Checking the account
 * and the quick automatic sync run here; the long imports (link, import more, carrying on
 * an import that was cut short) go to [importer], which in the app is WorkManager, so they
 * keep running when the app leaves the screen (plan Phase 6B).
 */
class RatingLinkCoordinator(
    private val ratingRepository: RatingRepository,
    private val scope: CoroutineScope,
    private val gameRepository: GameRepository? = null,
    private val ratingApiClient: RatingApiClient = RatingApiClient(),
    private val importer: ImportRunner = InProcessImportRunner(scope, gameRepository ?: InMemoryGameRepository(), ratingApiClient),
) {
    private var verifyJob: Job? = null
    private var syncJob: Job? = null
    /** New games "Import more" saved before handing over to the importer, for its end message. */
    private var newGamesBeforeImport = 0

    /**
     * Follows the importer for the life of the app: its progress card while it runs, then the
     * result message. [onFinished] hears every finished import (to forget a completed target).
     */
    fun observeImports(updateState: ((AppUiState) -> AppUiState) -> Unit, onFinished: (ImportStatus.Finished) -> Unit) {
        scope.launch {
            importer.status.collect { status ->
                when (status) {
                    null -> Unit
                    is ImportStatus.Running -> updateState { it.copy(isLinkingLoading = true, importProgress = status.progress) }
                    is ImportStatus.Finished -> {
                        val newGames = if (status.request.kind == ImportKind.MORE) newGamesBeforeImport else 0
                        newGamesBeforeImport = 0
                        updateState { finishedState(it, status, newGames) }
                        onFinished(status)
                        importer.acknowledge()
                    }
                }
            }
        }
    }

    /** True while a long import runs (checked before starting another). */
    suspend fun isImporting(): Boolean = importer.isRunning()

    fun cancelImport(updateState: ((AppUiState) -> AppUiState) -> Unit) {
        verifyJob?.cancel()
        importer.cancel()
        updateState { it.copy(isLinkingLoading = false, importProgress = null, linkingSuccessMessage = "Import cancelled.") }
    }

    fun linkAccount(
        platform: RatingPlatform,
        username: String,
        timeControl: RatingTimeControl,
        updateState: ((AppUiState) -> AppUiState) -> Unit,
        importDepth: ImportDepth = ImportDepth.LAST_100,
    ) {
        val clean = username.trim()
        if (clean.isBlank()) {
            updateState { it.copy(linkingError = "Please enter a valid username.") }
            return
        }
        verifyJob?.cancel()
        updateState {
            it.copy(
                isLinkingLoading = true,
                linkingError = null,
                linkingSuccessMessage = null,
                selectedImportDepth = importDepth,
                importProgress = ImportProgress(
                    importedCount = 0,
                    targetCount = importDepth.maxGames,
                    currentPeriod = platform.displayName,
                    phase = "Verifying ${platform.displayName} profile '$clean'...",
                ),
            )
        }
        verifyJob = scope.launch {
            ratingRepository.linkAccount(platform, clean, timeControl)
                .onSuccess { profile -> importer.start(ImportRequest(ImportKind.LINK, profile.platform, profile.username, importDepth.maxGames)) }
                .onFailure { err ->
                    updateState { it.copy(isLinkingLoading = false, importProgress = null, linkingError = err.message ?: "Failed to link profile. Please check username.") }
                }
        }
    }

    /**
     * "Import more": fresh ratings and the games played since [sinceMillis] (the newest saved
     * one), then up to [importDepth] games older than [beforeMillis] (the oldest saved one). It
     * used to ask for the newest games again, so the games already saved used up the request
     * and "Import 500" with 257 saved brought nothing new (Phase 7 import fix).
     */
    fun refreshProfile(
        currentProfileUsername: String?,
        updateState: ((AppUiState) -> AppUiState) -> Unit,
        importDepth: ImportDepth = ImportDepth.LAST_100,
        beforeMillis: Long? = null,
        sinceMillis: Long? = null,
    ) {
        if (currentProfileUsername == null) return
        verifyJob?.cancel()
        updateState {
            it.copy(
                isLinkingLoading = true,
                linkingError = null,
                linkingSuccessMessage = null,
                importProgress = ImportProgress(
                    importedCount = 0,
                    targetCount = importDepth.maxGames,
                    currentPeriod = currentProfileUsername,
                    phase = "Checking for new games from '@$currentProfileUsername'...",
                ),
            )
        }
        verifyJob = scope.launch {
            ratingRepository.refreshProfile()
                .onSuccess { profile ->
                    newGamesBeforeImport = if (sinceMillis != null) fetchNewGames(profile, sinceMillis).getOrNull()?.size ?: 0 else 0
                    importer.start(ImportRequest(ImportKind.MORE, profile.platform, profile.username, importDepth.maxGames, beforeMillis = beforeMillis))
                }
                .onFailure { err -> updateState { it.copy(isLinkingLoading = false, importProgress = null, linkingError = err.message ?: "Failed to refresh rating") } }
        }
    }

    fun setRatingTimeControl(timeControl: RatingTimeControl) {
        scope.launch { ratingRepository.updateTimeControl(timeControl) }
    }

    /**
     * The automatic sync: fresh ratings and any games played since [sinceMillis] (the newest
     * imported one), saved as they arrive. Quiet: no progress card, and it never interrupts an
     * import. With [backfill] it then hands the importer the rest of an import that was cut
     * short (the app was closed part-way).
     */
    fun autoSync(
        sinceMillis: Long?,
        updateState: ((AppUiState) -> AppUiState) -> Unit,
        backfill: Backfill? = null,
    ) {
        if (syncJob?.isActive == true || verifyJob?.isActive == true) return
        syncJob = scope.launch {
            if (importer.isRunning()) return@launch
            updateState { it.copy(isAutoSyncing = true) }
            try {
                val profile = ratingRepository.refreshProfile().getOrNull() ?: return@launch
                val result = fetchNewGames(profile, sinceMillis)
                if (result.isSuccess) updateState { it.copy(lastSyncedEpochMs = System.currentTimeMillis()) }
                if (backfill != null) {
                    importer.start(ImportRequest(ImportKind.BACKFILL, profile.platform, profile.username, backfill.maxGames, beforeMillis = backfill.beforeMillis))
                }
            } finally {
                updateState { it.copy(isAutoSyncing = false) }
            }
        }
    }

    /** Up to 100 games played since [sinceMillis] (all of the newest when null), saved as they arrive. */
    private suspend fun fetchNewGames(profile: LinkedChessProfile, sinceMillis: Long?): Result<List<GameRecord>> {
        val result = ratingApiClient.fetchRecentGames(
            platform = profile.platform,
            username = profile.username,
            maxGames = ImportDepth.LAST_100.maxGames,
            sinceMillis = sinceMillis,
            onBatch = { batch -> saveGamesOrError(batch) },
        )
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        return result
    }

    /** Finishing an interrupted import: up to [maxGames] games older than [beforeMillis]. */
    data class Backfill(val beforeMillis: Long, val maxGames: Int)

    fun unlinkAccount(updateState: ((AppUiState) -> AppUiState) -> Unit) {
        verifyJob?.cancel()
        syncJob?.cancel()
        importer.cancel()
        scope.launch {
            ratingRepository.unlinkAccount()
            updateState { it.copy(linkedProfile = null, isLinkingLoading = false, importProgress = null, linkingSuccessMessage = null, linkingError = null) }
        }
    }

    /** Saves the batch; returns the error instead of swallowing it. Cancellation is rethrown. */
    private suspend fun saveGamesOrError(games: List<GameRecord>): Throwable? {
        if (games.isEmpty()) return null
        return try {
            gameRepository?.saveGames(games)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }
    }

    companion object {
        /**
         * The screen after an import ends: no progress card, and what to tell the player.
         * [newGames] are the new games "Import more" saved before the importer started.
         */
        fun finishedState(state: AppUiState, done: ImportStatus.Finished, newGames: Int = 0): AppUiState {
            val request = done.request
            val issue = when {
                done.error != null -> done.error
                done.saveFailed -> "Your games were downloaded but couldn't be saved on this device. Check your free storage and try again."
                else -> null
            }
            val partial = if (done.failedPeriods > 0) " · some games couldn't be downloaded, tap Import to retry" else ""
            val incomplete = if (done.incomplete > 0) " (${done.incomplete} incomplete PGN${if (done.incomplete == 1) "" else "s"})" else ""
            val name = "'@${request.username}'"
            val success = when (request.kind) {
                ImportKind.LINK -> "Connected ${request.platform.displayName} $name" +
                    (if (done.imported > 0) " · Imported ${done.imported} games$incomplete$partial" else if (issue == null) " · No games found yet" else "")
                ImportKind.MORE -> moreMessage(done, newGames, name, incomplete, partial, issue)
                ImportKind.BACKFILL -> if (done.added > 0) "Finished importing: ${games(done.added, "more ")}$partial" else null
            }
            val error = issue?.let { msg ->
                when (request.kind) {
                    ImportKind.LINK -> "Account connected, but your games weren't imported. $msg"
                    ImportKind.MORE -> "Ratings updated, but your older games weren't imported. $msg"
                    ImportKind.BACKFILL -> "Your import stopped early. $msg"
                }
            }
            return state.copy(
                isLinkingLoading = false,
                importProgress = null,
                linkingError = error,
                lastSyncedEpochMs = if (issue == null) System.currentTimeMillis() else state.lastSyncedEpochMs,
                linkingSuccessMessage = success ?: state.linkingSuccessMessage,
            )
        }

        /** "Added 3 new games and 500 older games for '@kenny'", or why nothing came. */
        private fun moreMessage(done: ImportStatus.Finished, newGames: Int, name: String, incomplete: String, partial: String, issue: String?): String? {
            val older = done.added.takeIf { it > 0 }?.let { games(it, if (done.request.beforeMillis != null) "older " else "") }
            val fresh = newGames.takeIf { it > 0 }?.let { games(it, "new ") }
            val added = listOfNotNull(fresh, older)
            return when {
                added.isNotEmpty() -> "Added ${added.joinToString(" and ")} for $name$incomplete$partial"
                issue != null -> null
                done.request.beforeMillis != null -> "No older games found for $name: every game from ${done.request.platform.displayName} is here already"
                else -> "Updated ratings for $name"
            }
        }

        /** "1 game", "500 older games". */
        private fun games(count: Int, kind: String = ""): String = if (count == 1) "1 ${kind}game" else "$count ${kind}games"
    }
}
