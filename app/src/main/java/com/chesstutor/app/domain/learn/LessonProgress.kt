package com.chesstutor.app.domain.learn

import kotlinx.coroutines.flow.Flow

/** Where the player is with one lesson (plan section 7.4, table `lesson_progress`). */
data class LessonProgress(
    val lessonId: String,
    /** The step to resume at; 0 once the lesson is finished. */
    val stepIndex: Int = 0,
    val stepCount: Int = 0,
    val completed: Boolean = false,
    /** Times finished. */
    val plays: Int = 0,
    val bestCleanChallenges: Int = 0,
    val challengeCount: Int = 0,
    /** Every challenge solved cleanly in one run, at least once. */
    val perfect: Boolean = false,
    val xpEarned: Int = 0,
    val completedAt: Long? = null,
) {
    val started: Boolean get() = completed || stepIndex > 0
}

/** What finishing a lesson gave the player, for the Done card. */
data class LessonCompletion(
    val xp: Int,
    val firstTime: Boolean,
    val newlyPerfect: Boolean,
    val progress: LessonProgress,
)

/**
 * XP for lessons (plan section 5.3: earned by learning, never by tapping around). The
 * lesson's XP comes once, the first time it is finished; a perfect bonus comes once, the
 * first time every challenge is solved cleanly in one run. Replays give nothing.
 */
object LessonXp {
    const val PERFECT_BONUS = 10

    fun complete(lesson: Lesson, previous: LessonProgress?, result: LessonResult, now: Long): LessonCompletion {
        val firstTime = previous?.completed != true
        val newlyPerfect = result.perfect && lesson.quizCount > 0 && previous?.perfect != true
        val xp = (if (firstTime) lesson.xp else 0) + (if (newlyPerfect) PERFECT_BONUS else 0)
        val progress = LessonProgress(
            lessonId = lesson.id,
            stepIndex = 0,
            stepCount = lesson.steps.size,
            completed = true,
            plays = (previous?.plays ?: 0) + 1,
            bestCleanChallenges = maxOf(previous?.bestCleanChallenges ?: 0, result.cleanChallenges),
            challengeCount = lesson.quizCount,
            perfect = previous?.perfect == true || (result.perfect && lesson.quizCount > 0),
            xpEarned = (previous?.xpEarned ?: 0) + xp,
            completedAt = if (firstTime) now else previous?.completedAt,
        )
        return LessonCompletion(xp = xp, firstTime = firstTime, newlyPerfect = newlyPerfect, progress = progress)
    }
}

/** Lesson progress storage (Room in the app, in memory in tests). */
interface LessonProgressRepository {
    fun observe(): Flow<Map<String, LessonProgress>>

    suspend fun get(lessonId: String): LessonProgress?

    /** Remembers the step to resume at. A finished lesson stays finished. */
    suspend fun saveStep(lessonId: String, stepIndex: Int, stepCount: Int)

    /** Records a finished lesson and its XP (in the shared XP ledger). */
    suspend fun complete(lesson: Lesson, result: LessonResult): LessonCompletion
}
