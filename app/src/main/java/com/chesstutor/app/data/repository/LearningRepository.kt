package com.chesstutor.app.data.repository

import com.chesstutor.app.data.local.LearningDao
import com.chesstutor.app.data.local.LearningProfileEntity
import com.chesstutor.app.data.local.ModuleProgressEntity
import com.chesstutor.app.data.model.LearningProfile
import com.chesstutor.app.data.model.ModuleProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface LearningRepository {
    suspend fun getProfile(): LearningProfile
    suspend fun saveProfile(profile: LearningProfile)
    suspend fun getModuleProgress(): List<ModuleProgress>
    fun observeModuleProgress(): Flow<List<ModuleProgress>>
    suspend fun markPracticed(moduleId: String)
    suspend fun markMastered(moduleId: String)
    suspend fun recordModuleAttempt(moduleId: String, correct: Boolean)
    suspend fun resetModuleProgress()
}

class RoomLearningRepository(private val dao: LearningDao) : LearningRepository {
    override suspend fun getProfile(): LearningProfile =
        dao.getProfile()?.toDomain() ?: LearningProfile()

    override suspend fun saveProfile(profile: LearningProfile) =
        dao.upsertProfile(LearningProfileEntity.fromDomain(profile))

    override suspend fun getModuleProgress(): List<ModuleProgress> =
        dao.getAllModuleProgress().map { it.toDomain() }

    override fun observeModuleProgress(): Flow<List<ModuleProgress>> =
        dao.observeModuleProgress().map { list -> list.map { it.toDomain() } }

    private suspend fun currentModule(moduleId: String): ModuleProgress =
        dao.getAllModuleProgress().firstOrNull { it.moduleId == moduleId }?.toDomain()
            ?: ModuleProgress(moduleId)

    override suspend fun markPracticed(moduleId: String) {
        val current = currentModule(moduleId)
        dao.upsertModuleProgress(
            ModuleProgressEntity.fromDomain(
                current.copy(practiced = true, lastPracticedAt = System.currentTimeMillis())
            )
        )
    }

    override suspend fun markMastered(moduleId: String) {
        val current = currentModule(moduleId)
        dao.upsertModuleProgress(
            ModuleProgressEntity.fromDomain(
                current.copy(practiced = true, mastered = true, lastPracticedAt = System.currentTimeMillis())
            )
        )
    }

    override suspend fun recordModuleAttempt(moduleId: String, correct: Boolean) {
        val current = currentModule(moduleId)
        dao.upsertModuleProgress(
            ModuleProgressEntity.fromDomain(
                current.copy(
                    practiced = true,
                    attempts = current.attempts + 1,
                    correctAttempts = current.correctAttempts + if (correct) 1 else 0,
                    lastPracticedAt = System.currentTimeMillis()
                )
            )
        )
    }

    override suspend fun resetModuleProgress() {
        dao.clearModuleProgress()
    }
}

class InMemoryLearningRepository(initial: LearningProfile = LearningProfile()) : LearningRepository {
    private var profile = initial
    private val modules = mutableMapOf<String, ModuleProgress>()

    override suspend fun getProfile() = profile
    override suspend fun saveProfile(profile: LearningProfile) { this.profile = profile }
    override suspend fun getModuleProgress() = modules.values.toList()
    override fun observeModuleProgress(): Flow<List<ModuleProgress>> =
        kotlinx.coroutines.flow.MutableStateFlow(modules.values.toList())
    override suspend fun markPracticed(moduleId: String) {
        val current = modules[moduleId] ?: ModuleProgress(moduleId)
        modules[moduleId] = current.copy(practiced = true, lastPracticedAt = System.currentTimeMillis())
    }
    override suspend fun markMastered(moduleId: String) {
        val current = modules[moduleId] ?: ModuleProgress(moduleId)
        modules[moduleId] = current.copy(practiced = true, mastered = true, lastPracticedAt = System.currentTimeMillis())
    }
    override suspend fun recordModuleAttempt(moduleId: String, correct: Boolean) {
        val current = modules[moduleId] ?: ModuleProgress(moduleId)
        modules[moduleId] = current.copy(
            practiced = true,
            attempts = current.attempts + 1,
            correctAttempts = current.correctAttempts + if (correct) 1 else 0,
            lastPracticedAt = System.currentTimeMillis()
        )
    }
    override suspend fun resetModuleProgress() { modules.clear() }
}
