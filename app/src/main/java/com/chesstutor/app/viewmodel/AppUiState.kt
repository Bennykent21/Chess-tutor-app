package com.chesstutor.app.viewmodel

import com.chesstutor.app.data.model.ImportDepth
import com.chesstutor.app.data.model.ImportProgress
import com.chesstutor.app.data.model.LinkedChessProfile
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.domain.ReviewItem
import com.chesstutor.app.engine.EngineStatus

/**
 * App-wide state shared by every tab: the player's profile, settings, linked account, the
 * mistakes due for review and the engine's health. Each tab's own screens live in their
 * feature view models.
 */
data class AppUiState(
    /** False until the saved learning profile has been read, so start-up does not flash the wrong screen. */
    val profileLoaded: Boolean = false,
    /** The starting rating from Home's welcome card, until a linked account gives a real one. */
    val estimatedRating: Int = 600,
    /** "COMPLETE" once the player has answered the welcome card (D30). */
    val assessmentState: String = "NOT_STARTED",
    /** Every saved mistake (Puzzles > Mistakes review). */
    val reviews: List<ReviewItem> = emptyList(),
    val storageWarning: String? = null,
    val engineSelfTestReport: String? = null,
    val isRunningEngineSelfTest: Boolean = false,
    val linkedProfile: LinkedChessProfile? = null,
    val isLinkingLoading: Boolean = false,
    val linkingError: String? = null,
    val linkingSuccessMessage: String? = null,
    val selectedImportDepth: ImportDepth = ImportDepth.LAST_100,
    val importProgress: ImportProgress? = null,
    /** The quiet automatic sync of a linked account is running. */
    val isAutoSyncing: Boolean = false,
    val lastSyncedEpochMs: Long? = null,
    /** Player settings, mirrored from DataStore. */
    val appSettings: AppSettings = AppSettings(),
    /** Which engine is answering, for the "Lite engine" badge. */
    val engineStatus: EngineStatus = EngineStatus(),
) {
    val effectiveUserRating: Int
        get() = linkedProfile?.activeRating ?: estimatedRating
}
