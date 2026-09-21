package com.chesstutor.app.data.local

import androidx.room.Entity
import com.chesstutor.app.data.model.ModuleProgress

@Entity(tableName = "module_progress", primaryKeys = ["moduleId"])
data class ModuleProgressEntity(
    val moduleId: String,
    val practiced: Boolean,
    val mastered: Boolean,
    val attempts: Int,
    val correctAttempts: Int,
    val lastPracticedAt: Long?
) {
    fun toDomain() = ModuleProgress(moduleId, practiced, mastered, attempts, correctAttempts, lastPracticedAt)

    companion object {
        fun fromDomain(progress: ModuleProgress) = ModuleProgressEntity(
            progress.moduleId, progress.practiced, progress.mastered,
            progress.attempts, progress.correctAttempts, progress.lastPracticedAt
        )
    }
}
