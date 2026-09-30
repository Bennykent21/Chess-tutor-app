package com.chesstutor.app.viewmodel

import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.chesstutor.app.data.repository.RatingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class RatingLinkCoordinator(
    private val ratingRepository: RatingRepository,
    private val scope: CoroutineScope
) {

    fun linkAccount(
        platform: RatingPlatform,
        username: String,
        timeControl: RatingTimeControl,
        onApplyBotElo: () -> Unit,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        val clean = username.trim()
        if (clean.isBlank()) {
            updateState { it.copy(linkingError = "Please enter a valid username.") }
            return
        }
        updateState {
            it.copy(
                isLinkingLoading = true,
                linkingError = null,
                linkingSuccessMessage = null
            )
        }
        scope.launch {
            val result = ratingRepository.linkAccount(platform, clean, timeControl)
            result.onSuccess { profile ->
                updateState {
                    it.copy(
                        isLinkingLoading = false,
                        linkingError = null,
                        linkingSuccessMessage = "Successfully linked ${profile.platform.displayName} profile '${profile.username}'",
                        useLinkedRatingForBot = true
                    )
                }
                onApplyBotElo()
            }.onFailure { err ->
                updateState {
                    it.copy(
                        isLinkingLoading = false,
                        linkingError = err.message ?: "Failed to link profile. Please check username."
                    )
                }
            }
        }
    }

    fun refreshProfile(
        currentProfileUsername: String?,
        onApplyBotElo: () -> Unit,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        if (currentProfileUsername == null) return
        updateState {
            it.copy(
                isLinkingLoading = true,
                linkingError = null,
                linkingSuccessMessage = null
            )
        }
        scope.launch {
            val result = ratingRepository.refreshProfile()
            result.onSuccess { profile ->
                updateState {
                    it.copy(
                        isLinkingLoading = false,
                        linkingSuccessMessage = "Updated ratings for '${profile.username}'"
                    )
                }
                onApplyBotElo()
            }.onFailure { err ->
                updateState {
                    it.copy(
                        isLinkingLoading = false,
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
        scope.launch {
            ratingRepository.unlinkAccount()
            updateState {
                it.copy(
                    linkedProfile = null,
                    useLinkedRatingForBot = false,
                    linkingSuccessMessage = null,
                    linkingError = null
                )
            }
            onApplyBotElo()
        }
    }
}
