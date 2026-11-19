package com.chesstutor.app.domain.activity

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.GameSource
import com.chesstutor.app.domain.learn.LessonProgress
import com.chesstutor.app.domain.puzzle.PuzzleHistoryEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The days the player trained in the app (D110). The day streak and Home's week both come from
 * these days, so they always agree; before, the streak counted puzzles only and the week counted
 * any game, imported ones too.
 */
object ActivityDays {

    /** The days [times] (epoch milliseconds) fall on in [zone]. */
    fun of(times: Iterable<Long>, zone: ZoneId = ZoneId.systemDefault()): Set<LocalDate> =
        times.mapTo(HashSet()) { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    /**
     * What counts: a solved puzzle, a game played in this app (imported games were played
     * elsewhere), a lesson finished for the first time and an opening drill answered.
     */
    fun of(
        puzzles: List<PuzzleHistoryEntry>,
        games: List<GameRecord>,
        lessons: Collection<LessonProgress>,
        drillTimes: List<Long>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Set<LocalDate> = of(
        puzzles.filter { it.solved }.map { it.finishedAt } +
            games.filter { it.source == GameSource.ARENA }.map { it.dateMillis } +
            lessons.mapNotNull { it.completedAt } +
            drillTimes,
        zone,
    )

    /** The days as they change, from each kind of activity's own flow. */
    fun observe(
        puzzles: Flow<List<PuzzleHistoryEntry>>,
        games: Flow<List<GameRecord>>,
        lessons: Flow<Map<String, LessonProgress>>,
        drillTimes: Flow<List<Long>>,
    ): Flow<Set<LocalDate>> =
        combine(puzzles, games, lessons, drillTimes) { p, g, l, d -> of(p, g, l.values, d) }.distinctUntilChanged()

    /** Days in a row with any activity. Today still counts as kept until midnight. */
    fun streak(days: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in days) today else today.minusDays(1)
        var count = 0
        while (day in days) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    /** Monday to Sunday of [today]'s week: whether each day had any activity. */
    fun week(days: Set<LocalDate>, today: LocalDate): List<Boolean> {
        val monday = today.with(DayOfWeek.MONDAY)
        return (0L until 7L).map { monday.plusDays(it) in days }
    }
}
