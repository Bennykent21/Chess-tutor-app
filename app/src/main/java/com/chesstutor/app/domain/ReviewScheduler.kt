package com.chesstutor.app.domain

import java.time.Instant
import java.time.temporal.ChronoUnit

object ReviewScheduler {
    val intervalsDays = listOf(1, 3, 7, 14, 30)

    fun recordAttempt(item: ReviewItem, correct: Boolean, usedHint: Boolean, now: Instant) {
        item.attempts++
        if (!correct) {
            item.stage = -1
            item.dueAt = now
            return
        }
        item.stage = if (usedHint) 0 else (item.stage + 1).coerceIn(0, intervalsDays.lastIndex)
        item.dueAt = now.plus(intervalsDays[item.stage].toLong(), ChronoUnit.DAYS)
    }

    fun isDue(item: ReviewItem, now: Instant): Boolean {
        return !item.dueAt.isAfter(now)
    }
}
