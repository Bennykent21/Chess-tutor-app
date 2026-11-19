package com.chesstutor.app.feature.learn

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.domain.learn.Course
import com.chesstutor.app.domain.learn.Lesson
import com.chesstutor.app.domain.learn.LessonCategory
import com.chesstutor.app.domain.learn.LessonLevel
import com.chesstutor.app.domain.learn.LessonLibrary
import com.chesstutor.app.domain.learn.LessonProgress

/** One big button on the Learn hub (plan section 6.2). */
data class LearnHubTile(
    val category: LessonCategory,
    val subtitle: String,
    val totalLessons: Int,
    val completedLessons: Int,
    val startedLessons: Int,
) {
    val key: String get() = category.key
    val title: String get() = category.title
    val isAvailable: Boolean get() = totalLessons > 0
    val progress: Float get() = if (totalLessons == 0) 0f else completedLessons.toFloat() / totalLessons
}

/** Lessons finished, started and in total, for a course or a category. */
data class LessonCount(val completed: Int, val started: Int, val total: Int) {
    val progress: Float get() = if (total == 0) 0f else completed.toFloat() / total
}

enum class LessonStatus { NEW, STARTED, DONE }

/** The two filters on a category page (plan section 6.2). Null means "all". */
data class CourseFilter(
    val query: String = "",
    val level: LessonLevel? = null,
    val side: PieceColor? = null,
    /** For Black's courses: only those that answer this first move of White's ("e2e4"), D111. */
    val against: String? = null,
)

/**
 * The Learn tab's pages as plain data: hub tiles with progress, the "continue" lesson,
 * course progress and the category page's search and filters.
 */
object LearnHubModel {

    private val subtitles = mapOf(
        LessonCategory.BASICS to "How pieces move, check and mate",
        LessonCategory.OPENINGS to "Start every game well",
        LessonCategory.TACTICS to "Forks, pins and combinations",
        LessonCategory.MIDDLEGAME to "Plans and piece activity",
        LessonCategory.ENDGAMES to "Turn advantages into wins",
        LessonCategory.BLUNDERS to "Spot the traps before they spring",
    )

    /** The six hub tiles, with lesson counts from [progress]. */
    fun tiles(library: LessonLibrary, progress: Map<String, LessonProgress>): List<LearnHubTile> =
        LessonCategory.entries.map { category ->
            val count = countOf(library.coursesIn(category).flatMap { it.lessons }, progress)
            LearnHubTile(category, subtitles.getValue(category), count.total, count.completed, count.started)
        }

    /** Overall progress across every available lesson, 0..1. */
    fun overallProgress(tiles: List<LearnHubTile>): Float {
        val total = tiles.sumOf { it.totalLessons }
        return if (total == 0) 0f else tiles.sumOf { it.completedLessons }.toFloat() / total
    }

    fun countOf(lessons: List<Lesson>, progress: Map<String, LessonProgress>): LessonCount = LessonCount(
        completed = lessons.count { progress[it.id]?.completed == true },
        started = lessons.count { status(it, progress) == LessonStatus.STARTED },
        total = lessons.size,
    )

    fun status(lesson: Lesson, progress: Map<String, LessonProgress>): LessonStatus {
        val row = progress[lesson.id] ?: return LessonStatus.NEW
        return when {
            row.completed -> LessonStatus.DONE
            row.started -> LessonStatus.STARTED
            else -> LessonStatus.NEW
        }
    }

    /**
     * The lesson for the "Continue" card: the first started but unfinished lesson in hub
     * order, else the first lesson not yet finished. Null once everything is done.
     */
    fun continueLesson(library: LessonLibrary, progress: Map<String, LessonProgress>): Pair<Course, Lesson>? {
        val all = library.courses.flatMap { course -> course.lessons.map { course to it } }
        // Where the learner left off (D112): the lesson played last, or the next one of its course once it is done.
        progress.values.filter { it.updatedAt != null }.maxByOrNull { it.updatedAt!! }?.let { last ->
            val (course, lesson) = all.firstOrNull { it.second.id == last.lessonId } ?: return@let
            if (status(lesson, progress) != LessonStatus.DONE) return course to lesson
            course.lessons.dropWhile { it.id != lesson.id }.drop(1).firstOrNull { status(it, progress) != LessonStatus.DONE }?.let { return course to it }
        }
        return all.firstOrNull { (_, lesson) -> status(lesson, progress) == LessonStatus.STARTED }
            ?: all.firstOrNull { (_, lesson) -> status(lesson, progress) != LessonStatus.DONE }
    }

    /** Courses matching the search text (title, intro or a lesson title) and the chips. */
    fun filter(courses: List<Course>, filter: CourseFilter): List<Course> {
        val words = filter.query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return courses.filter { course ->
            val text = (listOf(course.title, course.intro) + course.lessons.map { it.title }).joinToString(" ").lowercase()
            words.all { it in text } &&
                (filter.level == null || course.level == filter.level) &&
                (filter.side == null || course.side == null || course.side == filter.side) &&
                (filter.against == null || (course.side == PieceColor.BLACK && firstMoveAnswered(course, courses) == filter.against))
        }
    }

    /** The first move of White's that a course answers ("e2e4"): from its own lines, else from its opening's other courses. */
    fun firstMoveAnswered(course: Course, all: List<Course>): String? {
        val line = course.variations.firstOrNull()
            ?: all.firstOrNull { it.opening != null && it.opening == course.opening && it.variations.isNotEmpty() }?.variations?.first()
        return line?.firstOrNull()
    }

    /** True when the side chips make sense for these courses (some teach one side only). */
    fun offersSideFilter(courses: List<Course>): Boolean = courses.any { it.side != null }
}
