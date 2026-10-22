package com.chesstutor.app.feature.puzzles

import com.chesstutor.app.domain.puzzle.LevelProgress
import com.chesstutor.app.domain.puzzle.PuzzleLevels
import com.chesstutor.app.domain.puzzle.PuzzleStats
import com.chesstutor.app.domain.puzzle.PuzzleWorld

/** How a level looks on the path. */
enum class LevelNodeState { PASSED, CURRENT, OPEN, LOCKED }

/** One row of the level path: a world banner or a level. */
sealed interface PathItem {
    data class WorldBanner(val world: PuzzleWorld, val passedLevels: Int) : PathItem
    data class Level(val level: Int, val state: LevelNodeState, val bestScore: Int?, val world: PuzzleWorld) : PathItem
}

object PuzzlesHomeModel {

    fun nodeState(level: Int, levels: Map<Int, LevelProgress>): LevelNodeState {
        val passed = levels.filterValues { it.passed }.keys
        return when {
            level in passed -> LevelNodeState.PASSED
            level == PuzzleLevels.current(passed) -> LevelNodeState.CURRENT
            PuzzleLevels.isUnlocked(level, passed) -> LevelNodeState.OPEN
            else -> LevelNodeState.LOCKED
        }
    }

    /**
     * The path from the first world up: each world's banner, then its ten levels. Drawn
     * bottom to top, so the banner sits at the entrance to its world.
     */
    fun pathItems(levels: Map<Int, LevelProgress>): List<PathItem> = PuzzleWorld.entries.flatMap { world ->
        val passedHere = world.levels.count { levels[it]?.passed == true }
        listOf<PathItem>(PathItem.WorldBanner(world, passedHere)) + world.levels.map { level ->
            PathItem.Level(level, nodeState(level, levels), levels[level]?.takeIf { it.plays > 0 }?.bestScore, world)
        }
    }

    /** Sideways position of a node, -1 (left) to 1 (right), so the path winds. */
    fun sway(level: Int): Float = SWAY[(level - 1) % SWAY.size]

    private val SWAY = floatArrayOf(0f, 0.55f, 0.85f, 0.55f, 0f, -0.55f, -0.85f, -0.55f)

    /** The coach's line on the home screen: short and encouraging (plan section 6.3.1). */
    fun coachLine(stats: PuzzleStats, currentLevel: Int, solvedToday: Boolean): String = when {
        stats.solved == 0 -> "Welcome! Let's start with level 1. Five puzzles, and you only need four."
        stats.dayStreak >= 2 && !solvedToday -> "Your ${stats.dayStreak}-day streak is waiting. One puzzle keeps it going!"
        stats.dayStreak >= 2 -> "Day ${stats.dayStreak} in a row. You're on fire!"
        stats.cleanStreak >= 3 -> "${stats.cleanStreak} clean solves in a row. Keep that focus!"
        currentLevel % PuzzleLevels.PER_WORLD == 0 -> "Level $currentLevel finishes the ${PuzzleWorld.of(currentLevel).title}. You can do it!"
        else -> "Ready for level $currentLevel?"
    }
}
