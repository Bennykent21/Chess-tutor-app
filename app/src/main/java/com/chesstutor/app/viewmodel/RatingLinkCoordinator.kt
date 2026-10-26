package com.chesstutor.app.viewmodel

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.ImportDepth
import com.chesstutor.app.data.model.ImportProgress
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.chesstutor.app.data.network.RatingApiClient
import com.chesstutor.app.data.repository.GameRepository
import com.chesstutor.app.data.repository.RatingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RatingLinkCoordinator(
    private val ratingRepository: RatingRepository,
    private val scope: CoroutineScope,
    private val gameRepository: GameRepository? = null,
    private val ratingApiClient: RatingApiClient = RatingApiClient()
) {
    private var activeImportJob: Job? = null

    fun cancelImport(updateState: ((AppUiState) -> AppUiState) -> Unit) {
        activeImportJob?.cancel()
        activeImportJob = null
        updateState {
            it.copy(
                isLinkingLoading = false,
                importProgress = null,
                linkingSuccessMessage = "Import cancelled."
            )
        }
    }

    fun linkAccount(
        platform: RatingPlatform,
        username: String,
        timeControl: RatingTimeControl,
        updateState: ((AppUiState) -> AppUiState) -> Unit,
        importDepth: ImportDepth = ImportDepth.LAST_100,
        /** Told whether the whole import arrived (false when months failed or the download broke). */
        onFinished: (complete: Boolean) -> Unit = {},
    ) {
        val clean = username.trim()
        if (clean.isBlank()) {
            updateState { it.copy(linkingError = "Please enter a valid username.") }
            return
        }
        activeImportJob?.cancel()
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
                    phase = "Verifying ${platform.displayName} profile '$clean'..."
                )
            )
        }
        activeImportJob = scope.launch {
            val result = ratingRepository.linkAccount(platform, clean, timeControl)
            result.onSuccess { profile ->
                var lastProgress: ImportProgress? = null
                var saveError: Throwable? = null
                val fetchResult = ratingApiClient.fetchRecentGames(
                    platform = platform,
                    username = clean,
                    maxGames = importDepth.maxGames,
                    onProgress = { progress ->
                        lastProgress = progress
                        updateState { it.copy(importProgress = progress) }
                    },
                    // Saved as they arrive, so the games list fills in while the import runs.
                    onBatch = { batch -> saveGamesOrError(batch)?.let { saveError = it } },
                )
                // A cancelled import must stop here (cancelImport already updated the UI).
                fetchResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                val importedGames = fetchResult.getOrDefault(emptyList())
                val issue = importIssue(fetchResult.exceptionOrNull(), saveError)
                onFinished(fetchResult.isSuccess && (lastProgress?.failedPeriods ?: 0) == 0)
                val failedPeriods = lastProgress?.failedPeriods ?: 0

                val incompleteCount = importedGames.count { it.incompleteParse }
                val syncSuffix = buildString {
                    if (importedGames.isNotEmpty()) {
                        append(" · Imported ${importedGames.size} games")
                        if (incompleteCount > 0) {
                            append(" ($incompleteCount incomplete PGN${if (incompleteCount == 1) "" else "s"})")
                        }
                        append(partialNote(failedPeriods))
                    } else if (issue == null) {
                        append(" · No games found yet")
                    }
                }
                updateState {
                    it.copy(
                        isLinkingLoading = false,
                        importProgress = null,
                        linkingError = issue?.let { msg -> "Account connected, but your games weren't imported. $msg" },
                        lastSyncedEpochMs = if (issue == null) System.currentTimeMillis() else it.lastSyncedEpochMs,
                        linkingSuccessMessage = "Connected ${profile.platform.displayName} '@${profile.username}'$syncSuffix"
                    )
                }
            }.onFailure { err ->
                updateState {
                    it.copy(
                        isLinkingLoading = false,
                        importProgress = null,
                        linkingError = err.message ?: "Failed to link profile. Please check username."
                    )
                }
            }
        }
    }

    fun refreshProfile(
        currentProfileUsername: String?,
        updateState: ((AppUiState) -> AppUiState) -> Unit,
        importDepth: ImportDepth = ImportDepth.LAST_100,
        /** Told whether the whole import arrived (false when months failed or the download broke). */
        onFinished: (complete: Boolean) -> Unit = {},
    ) {
        if (currentProfileUsername == null) return
        activeImportJob?.cancel()
        updateState {
            it.copy(
                isLinkingLoading = true,
                linkingError = null,
                linkingSuccessMessage = null,
                importProgress = ImportProgress(
                    importedCount = 0,
                    targetCount = importDepth.maxGames,
                    currentPeriod = currentProfileUsername,
                    phase = "Syncing '@$currentProfileUsername'..."
                )
            )
        }
        activeImportJob = scope.launch {
            val result = ratingRepository.refreshProfile()
            result.onSuccess { profile ->
                var lastProgress: ImportProgress? = null
                var saveError: Throwable? = null
                val fetchResult = ratingApiClient.fetchRecentGames(
                    platform = profile.platform,
                    username = profile.username,
                    maxGames = importDepth.maxGames,
                    onProgress = { progress ->
                        lastProgress = progress
                        updateState { it.copy(importProgress = progress) }
                    },
                    onBatch = { batch -> saveGamesOrError(batch)?.let { saveError = it } },
                )
                fetchResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                val importedGames = fetchResult.getOrDefault(emptyList())
                val issue = importIssue(fetchResult.exceptionOrNull(), saveError)
                onFinished(fetchResult.isSuccess && (lastProgress?.failedPeriods ?: 0) == 0)
                val failedPeriods = lastProgress?.failedPeriods ?: 0

                val incompleteCount = importedGames.count { it.incompleteParse }
                val incompleteNote = if (incompleteCount > 0) " · $incompleteCount incomplete" else ""
                updateState {
                    it.copy(
                        isLinkingLoading = false,
                        importProgress = null,
                        linkingError = issue?.let { msg -> "Ratings updated, but your games weren't synced. $msg" },
                        lastSyncedEpochMs = if (issue == null) System.currentTimeMillis() else it.lastSyncedEpochMs,
                        linkingSuccessMessage = if (importedGames.isNotEmpty()) {
                            "Synced '@${profile.username}' (${importedGames.size} games$incompleteNote)${partialNote(failedPeriods)}"
                        } else {
                            "Updated ratings for '@${profile.username}'"
                        }
                    )
                }
            }.onFailure { err ->
                updateState {
                    it.copy(
                        isLinkingLoading = false,
                        importProgress = null,
                        linkingError = err.message ?: "Failed to refresh rating"
                    )
                }
            }
        }
    }

    fun setRatingTimeControl(timeControl: RatingTimeControl) {
        scope.launch {
            ratingRepository.updateTimeControl(timeControl)
        }
    }

    /**
     * The automatic sync: fresh ratings and any games played since [sinceMillis] (the newest
     * imported one), saved as they arrive. Quiet: no progress card, and it never interrupts an
     * import the player started. With [backfill] it then carries on an import that was cut
     * short (the app was closed part-way), with the usual progress card.
     */
    fun autoSync(
        sinceMillis: Long?,
        updateState: ((AppUiState) -> AppUiState) -> Unit,
        backfill: Backfill? = null,
    ) {
        if (activeImportJob?.isActive == true) return
        activeImportJob = scope.launch {
            updateState { it.copy(isAutoSyncing = true) }
            try {
                val profile = ratingRepository.refreshProfile().getOrNull() ?: return@launch
                val result = ratingApiClient.fetchRecentGames(
                    platform = profile.platform,
                    username = profile.username,
                    maxGames = ImportDepth.LAST_100.maxGames,
                    sinceMillis = sinceMillis,
                    onBatch = { batch -> saveGamesOrError(batch) },
                )
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                if (result.isSuccess) updateState { it.copy(lastSyncedEpochMs = System.currentTimeMillis()) }
                if (backfill != null) runBackfill(profile.platform, profile.username, backfill, updateState)
            } finally {
                updateState { it.copy(isAutoSyncing = false) }
            }
        }
    }

    /** Finishing an interrupted import: up to [maxGames] games older than [beforeMillis]. */
    data class Backfill(val beforeMillis: Long, val maxGames: Int, val onDone: (complete: Boolean) -> Unit)

    private suspend fun runBackfill(
        platform: RatingPlatform,
        username: String,
        backfill: Backfill,
        updateState: ((AppUiState) -> AppUiState) -> Unit,
    ) {
        updateState {
            it.copy(
                isLinkingLoading = true,
                importProgress = ImportProgress(targetCount = backfill.maxGames, currentPeriod = platform.displayName, phase = "Carrying on your import..."),
            )
        }
        var lastProgress: ImportProgress? = null
        try {
            val result = ratingApiClient.fetchRecentGames(
                platform = platform,
                username = username,
                maxGames = backfill.maxGames,
                onProgress = { progress ->
                    lastProgress = progress
                    updateState { it.copy(importProgress = progress) }
                },
                onBatch = { batch -> saveGamesOrError(batch) },
                beforeMillis = backfill.beforeMillis,
            )
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            backfill.onDone(result.isSuccess && (lastProgress?.failedPeriods ?: 0) == 0)
        } finally {
            updateState { it.copy(isLinkingLoading = false, importProgress = null) }
        }
    }

    fun unlinkAccount(
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        activeImportJob?.cancel()
        scope.launch {
            ratingRepository.unlinkAccount()
            updateState {
                it.copy(
                    linkedProfile = null,
                    importProgress = null,
                    linkingSuccessMessage = null,
                    linkingError = null
                )
            }
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

    /** A player-readable reason the import failed, or null if it went fine. */
    private fun importIssue(fetchError: Throwable?, saveError: Throwable?): String? = when {
        fetchError != null ->
            fetchError.message?.takeIf { it.isNotBlank() } ?: "Something went wrong while downloading your games."
        saveError != null ->
            "Your games were downloaded but couldn't be saved on this device. Check your free storage and try again."
        else -> null
    }

    private fun partialNote(failedPeriods: Int): String =
        if (failedPeriods > 0) " · some games couldn't be downloaded, tap Sync to retry" else ""
}
