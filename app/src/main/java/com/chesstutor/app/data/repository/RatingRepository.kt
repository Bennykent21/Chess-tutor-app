package com.chesstutor.app.data.repository

import com.chesstutor.app.data.local.LinkedProfileDao
import com.chesstutor.app.data.local.LinkedProfileEntity
import com.chesstutor.app.data.model.LinkedChessProfile
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.chesstutor.app.data.network.RatingApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface RatingRepository {
    fun getLinkedProfileFlow(): Flow<LinkedChessProfile?>
    suspend fun getLinkedProfile(): LinkedChessProfile?
    suspend fun linkAccount(
        platform: RatingPlatform,
        username: String,
        timeControl: RatingTimeControl = RatingTimeControl.RAPID
    ): Result<LinkedChessProfile>
    suspend fun refreshProfile(): Result<LinkedChessProfile>
    suspend fun updateTimeControl(timeControl: RatingTimeControl)
    suspend fun unlinkAccount()
}

class RoomRatingRepository(
    private val dao: LinkedProfileDao,
    private val apiClient: RatingApiClient = RatingApiClient()
) : RatingRepository {

    override fun getLinkedProfileFlow(): Flow<LinkedChessProfile?> {
        return dao.getLinkedProfileFlow().map { it?.toDomain() }
    }

    override suspend fun getLinkedProfile(): LinkedChessProfile? {
        return dao.getLinkedProfile()?.toDomain()
    }

    override suspend fun linkAccount(
        platform: RatingPlatform,
        username: String,
        timeControl: RatingTimeControl
    ): Result<LinkedChessProfile> {
        val result = when (platform) {
            RatingPlatform.CHESS_COM -> apiClient.fetchChessComStats(username, timeControl)
            RatingPlatform.LICHESS -> apiClient.fetchLichessStats(username, timeControl)
        }

        result.onSuccess { profile ->
            dao.insertOrUpdate(LinkedProfileEntity.fromDomain(profile))
        }
        return result
    }

    override suspend fun refreshProfile(): Result<LinkedChessProfile> {
        val current = getLinkedProfile() ?: return Result.failure(IllegalStateException("No account currently linked"))
        return linkAccount(current.platform, current.username, current.selectedTimeControl)
    }

    override suspend fun updateTimeControl(timeControl: RatingTimeControl) {
        val current = getLinkedProfile() ?: return
        val updated = current.copy(selectedTimeControl = timeControl)
        dao.insertOrUpdate(LinkedProfileEntity.fromDomain(updated))
    }

    override suspend fun unlinkAccount() {
        dao.clear()
    }
}

class InMemoryRatingRepository(
    initialProfile: LinkedChessProfile? = null
) : RatingRepository {
    private val profileFlow = kotlinx.coroutines.flow.MutableStateFlow(initialProfile)

    override fun getLinkedProfileFlow(): Flow<LinkedChessProfile?> = profileFlow

    override suspend fun getLinkedProfile(): LinkedChessProfile? = profileFlow.value

    override suspend fun linkAccount(
        platform: RatingPlatform,
        username: String,
        timeControl: RatingTimeControl
    ): Result<LinkedChessProfile> {
        val profile = LinkedChessProfile(
            platform = platform,
            username = username.trim(),
            rapidRating = 1520,
            blitzRating = 1450,
            bulletRating = 1380,
            selectedTimeControl = timeControl,
            lastUpdated = System.currentTimeMillis()
        )
        profileFlow.value = profile
        return Result.success(profile)
    }

    override suspend fun refreshProfile(): Result<LinkedChessProfile> {
        val current = profileFlow.value ?: return Result.failure(IllegalStateException("No account currently linked"))
        return Result.success(current)
    }

    override suspend fun updateTimeControl(timeControl: RatingTimeControl) {
        val current = profileFlow.value ?: return
        profileFlow.value = current.copy(selectedTimeControl = timeControl)
    }

    override suspend fun unlinkAccount() {
        profileFlow.value = null
    }
}

