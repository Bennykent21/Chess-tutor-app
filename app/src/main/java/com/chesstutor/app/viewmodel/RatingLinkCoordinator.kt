package com.chesstutor.app.viewmodel

import com.chesstutor.app.data.model.ImportDepth
import com.chesstutor.app.data.model.ImportProgress
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.chesstutor.app.data.network.RatingApiClient
import com.chesstutor.app.data.repository.GameRepository
import com.chesstutor.app.data.repository.RatingRepository
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
        onApplyBotElo: () -> Unit,
        updateState: ((AppUiState) -> AppUiState) -> Unit,
        importDepth: ImportDepth = ImportDepth.LAST_100
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
                val importedGames = ratingApiClient.fetchRecentGames(
                    platform = platform,
                    username = clean,
                    maxGames = importDepth.maxGames,
                    onProgress = { progress ->
                        updateState { it.copy(importProgress = progress) }
                    }
                ).getOrDefault(emptyList())

                for (game in importedGames) {
                    runCatching { gameRepository?.saveGame(game) }
                }
                val incompleteCount = importedGames.count { it.incompleteParse }
                val syncSuffix = buildString {
                    if (importedGames.isNotEmpty()) {
                        append(" · Imported ${importedGames.size} games")
                        if (incompleteCount > 0) {
                            append(" ($incompleteCount incomplete PGN${if (incompleteCount == 1) "" else "s"})")
                        }
                    }
                }
                updateState {
                    it.copy(
                        isLinkingLoading = false,
                        importProgress = null,
                        linkingError = null,
                        lastSyncedEpochMs = System.currentTimeMillis(),
                        linkingSuccessMessage = "Connected ${profile.platform.displayName} '@${profile.username}'$syncSuffix"
                    )
                }
                onApplyBotElo()
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
        onApplyBotElo: () -> Unit,
        updateState: ((AppUiState) -> AppUiState) -> Unit,
        importDepth: ImportDepth = ImportDepth.LAST_100
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
                val importedGames = ratingApiClient.fetchRecentGames(
                    platform = profile.platform,
                    username = profile.username,
                    maxGames = importDepth.maxGames,
                    onProgress = { progress ->
                        updateState { it.copy(importProgress = progress) }
                    }
                ).getOrDefault(emptyList())

                for (game in importedGames) {
                    runCatching { gameRepository?.saveGame(game) }
                }
                val incompleteCount = importedGames.count { it.incompleteParse }
                val incompleteNote = if (incompleteCount > 0) " · $incompleteCount incomplete" else ""
                updateState {
                    it.copy(
                        isLinkingLoading = false,
                        importProgress = null,
                        lastSyncedEpochMs = System.currentTimeMillis(),
                        linkingSuccessMessage = if (importedGames.isNotEmpty()) {
                            "Synced '@${profile.username}' (${importedGames.size} games$incompleteNote)"
                        } else {
                            "Updated ratings for '@${profile.username}'"
                        }
                    )
                }
                onApplyBotElo()
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

    fun setRatingTimeControl(
        timeControl: RatingTimeControl,
        onApplyBotElo: () -> Unit
    ) {
        scope.launch {
            ratingRepository.updateTimeControl(timeControl)
            onApplyBotElo()
        }
    }

    fun unlinkAccount(
        onApplyBotElo: () -> Unit,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        activeImportJob?.cancel()
        scope.launch {
            ratingRepository.unlinkAccount()
            updateState {
                it.copy(
                    linkedProfile = null,
                    useLinkedRatingForBot = false,
                    importProgress = null,
                    linkingSuccessMessage = null,
                    linkingError = null
                )
            }
            onApplyBotElo()
        }
    }
}
