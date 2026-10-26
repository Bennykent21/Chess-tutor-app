package com.chesstutor.app.data.repository

import com.chesstutor.app.data.local.LearningDao
import com.chesstutor.app.data.local.LearningProfileEntity
import com.chesstutor.app.data.model.LearningProfile

/**
 * The player's profile: the starting rating from Home's welcome card (D30). The classic
 * curriculum's per-module progress (table `module_progress`) is no longer read.
 */
interface LearningRepository {
    suspend fun getProfile(): LearningProfile
    suspend fun saveProfile(profile: LearningProfile)
}

class RoomLearningRepository(private val dao: LearningDao) : LearningRepository {
    override suspend fun getProfile(): LearningProfile =
        dao.getProfile()?.toDomain() ?: LearningProfile()

    override suspend fun saveProfile(profile: LearningProfile) =
        dao.upsertProfile(LearningProfileEntity.fromDomain(profile))
}

class InMemoryLearningRepository(initial: LearningProfile = LearningProfile()) : LearningRepository {
    private var profile = initial

    override suspend fun getProfile() = profile
    override suspend fun saveProfile(profile: LearningProfile) { this.profile = profile }
}
