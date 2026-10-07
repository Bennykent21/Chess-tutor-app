package com.chesstutor.app.domain

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

object ReviewScheduler {
    val intervalsDays = listOf(1, 3, 7, 14, 30)

    fun recordAttempt(
        item: ReviewItem,
        correct: Boolean,
        usedHint: Boolean,
        now: Instant
    ): ReviewItem {
        val nextAttempts = item.attempts + 1
        if (!correct) {
            val nextEase = (item.easeFactor - 0.2f).coerceIn(1.3f, 3.0f)
            return item.copy(
                attempts = nextAttempts,
                stage = -1,
                intervalDays = 0,
                easeFactor = nextEase,
                dueAt = now
            )
        }

        val quality = if (usedHint) 3 else 5
        val easeDelta = 0.1f - (5 - quality) * (0.08f + (5 - quality) * 0.02f)
        val nextEase = (item.easeFactor + easeDelta).coerceIn(1.3f, 3.0f)
        val nextStage = if (usedHint) 0 else (item.stage + 1).coerceIn(0, intervalsDays.lastIndex)
        val baseInterval = intervalsDays[nextStage]
        val computedInterval = if (nextStage <= 1 || usedHint) {
            baseInterval
        } else {
            (baseInterval * (nextEase / 2.5f)).roundToInt().coerceAtLeast(baseInterval)
        }

        return item.copy(
            attempts = nextAttempts,
            stage = nextStage,
            intervalDays = computedInterval,
            easeFactor = nextEase,
            dueAt = now.plus(computedInterval.toLong(), ChronoUnit.DAYS)
        )
    }

    fun isDue(item: ReviewItem, now: Instant): Boolean {
        return !item.dueAt.isAfter(now)
    }

    fun stageLabel(stage: Int): String {
        return when (stage) {
            -1 -> "Learning"
            0 -> "1 Day"
            1 -> "3 Days"
            2 -> "7 Days"
            3 -> "14 Days"
            4 -> "30 Days"
            else -> "Mastered"
        }
    }
}
