package com.chesstutor.app.data.model

data class LearningProfile(
    val estimatedRating: Int = 600,
    val assessmentState: String = "NOT_STARTED",
    val assessmentPositionIndex: Int = 0,
    val assessmentCorrect: Int = 0,
    val assessmentTotal: Int = 0,
    val learningGoal: String = "GENERAL_IMPROVEMENT",
    val totalTacticalAttempts: Int = 0,
    val totalTacticalCorrect: Int = 0,
    val soundEnabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val tacticalAccuracy: Float
        get() = if (totalTacticalAttempts == 0) 0f
        else totalTacticalCorrect.toFloat() / totalTacticalAttempts.toFloat()
}

data class ModuleProgress(
    val moduleId: String,
    val practiced: Boolean = false,
    val mastered: Boolean = false,
    val attempts: Int = 0,
    val correctAttempts: Int = 0,
    val lastPracticedAt: Long? = null
) {
    val accuracy: Float
        get() = if (attempts == 0) 0f else correctAttempts.toFloat() / attempts.toFloat()
}
