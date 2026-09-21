package com.chesstutor.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.chesstutor.app.data.model.LearningProfile

@Entity(tableName = "learning_profile")
data class LearningProfileEntity(
    @PrimaryKey val id: Int = 1,
    val estimatedRating: Int,
    val assessmentState: String,
    val assessmentPositionIndex: Int,
    val assessmentCorrect: Int,
    val assessmentTotal: Int,
    val learningGoal: String,
    val totalTacticalAttempts: Int,
    val totalTacticalCorrect: Int,
    val soundEnabled: Boolean,
    val updatedAt: Long
) {
    fun toDomain() = LearningProfile(
        estimatedRating, assessmentState, assessmentPositionIndex, assessmentCorrect,
        assessmentTotal, learningGoal, totalTacticalAttempts, totalTacticalCorrect,
        soundEnabled, updatedAt
    )

    companion object {
        fun fromDomain(profile: LearningProfile) = LearningProfileEntity(
            id = 1,
            estimatedRating = profile.estimatedRating,
            assessmentState = profile.assessmentState,
            assessmentPositionIndex = profile.assessmentPositionIndex,
            assessmentCorrect = profile.assessmentCorrect,
            assessmentTotal = profile.assessmentTotal,
            learningGoal = profile.learningGoal,
            totalTacticalAttempts = profile.totalTacticalAttempts,
            totalTacticalCorrect = profile.totalTacticalCorrect,
            soundEnabled = profile.soundEnabled,
            updatedAt = profile.updatedAt
        )
    }
}
