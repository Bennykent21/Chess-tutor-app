package com.chesstutor.app.domain.learn

import com.chesstutor.app.domain.puzzle.Puzzle
import kotlinx.coroutines.flow.Flow
import com.chesstutor.app.domain.puzzle.PuzzlePack
import kotlin.random.Random

/** The player's best go at a course's practice set (plan 6C). */
data class PracticeResult(val courseId: String, val best: Int, val total: Int, val plays: Int, val lastPlayedAt: Long) {
    /** At least [PracticeSets.PASS] of the set solved without help. */
    val mastered: Boolean get() = total > 0 && best >= PracticeSets.passMark(total)
}

/**
 * Each course's practice set (plan 6C): the same [SIZE] puzzles every time, with the course's
 * themes and near its level, so the best score shows real progress and "mastered" means
 * something. The Related puzzles button stays for endless fresh practice.
 */
object PracticeSets {
    const val SIZE = 10
    const val PASS = 8

    /** The score that masters a set of [total] puzzles (8 of 10, scaled for a smaller set). */
    fun passMark(total: Int): Int = (total * PASS + SIZE - 1) / SIZE

    /** Puzzle ratings that suit a course's level. */
    fun bandFor(level: LessonLevel): IntRange = when (level) {
        LessonLevel.BEGINNER -> 600..1300
        LessonLevel.INTERMEDIATE -> 1000..1700
        LessonLevel.ADVANCED -> 1400..2200
    }

    /** True when the course has themes to build a set from. */
    fun offers(course: Course): Boolean = course.puzzleThemes.isNotEmpty()

    /**
     * The course's set: puzzles with any of its themes, inside its level's band when enough
     * are there, chosen the same way every time (seeded by the course id), easiest first.
     */
    fun puzzlesFor(course: Course, pack: PuzzlePack): List<Puzzle> {
        if (!offers(course)) return emptyList()
        val wanted = course.puzzleThemes.toSet()
        val matching = pack.puzzles.filter { p -> p.primaryTheme in wanted || p.themes.any { it in wanted } }
        val band = bandFor(course.level)
        val inBand = matching.filter { it.rating in band }
        val pool = if (inBand.size >= SIZE) inBand else matching
        return pool.sortedBy { it.id }.shuffled(Random(course.id.hashCode())).take(SIZE).sortedBy { it.rating }
    }
}

/** Where practice-set scores are kept. */
interface PracticeRepository {
    fun observePractice(): Flow<Map<String, PracticeResult>>

    /** Saves a finished run; the best score is kept. */
    suspend fun recordPractice(courseId: String, score: Int, total: Int, at: Long): PracticeResult
}
