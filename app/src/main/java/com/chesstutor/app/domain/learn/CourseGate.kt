package com.chesstutor.app.domain.learn

/**
 * The lines of one opening open for drilling. [open] and [taught] are UCI moves from the start:
 * the variations of finished courses, and of every course. Lines no variation course teaches
 * open with the opening as a whole ([rest]).
 */
data class OpenLines(
    val open: List<List<String>>,
    val taught: List<List<String>>,
    val rest: Boolean,
) {
    /** True when [path] can be drilled: it lies inside an open variation or leads into one. */
    fun allows(path: List<String>): Boolean {
        if (open.any { p -> path.startsWith(p) || p.startsWith(path) }) return true
        return rest && taught.none { path.startsWith(it) }
    }

    private fun List<String>.startsWith(prefix: List<String>) = size >= prefix.size && take(prefix.size) == prefix
}

/**
 * Which variations of the trainer's openings are open for drilling (Phase 12, design round 4):
 * a variation opens when a course that teaches it is finished, the lines no course teaches open
 * with the opening's ideas course, and a whole opening opens when its test is passed. An opening
 * that has no variation course yet stays fully open, so drilling never waits for content that
 * does not exist.
 */
object CourseGate {
    /** For each trainer opening that has variation courses, its open lines; openings missing from the map are fully open. */
    fun opened(library: LessonLibrary, progress: Map<String, LessonProgress>, passed: Set<String>): Map<String, OpenLines> {
        val byOpening = library.courses.filter { it.opening != null }.groupBy { it.opening!! }
        return byOpening.filterKeys { it !in passed }
            .filterValues { courses -> courses.any { it.variations.isNotEmpty() } }
            .mapValues { (_, courses) ->
                val (variations, ideas) = courses.partition { it.variations.isNotEmpty() }
                OpenLines(
                    open = variations.filter { isDone(it, progress) }.flatMap { it.variations },
                    taught = variations.flatMap { it.variations },
                    rest = ideas.all { isDone(it, progress) },
                )
            }
    }

    /** True when [path] can be drilled under [lines]; no gate means open. */
    fun isOpen(path: List<String>, lines: OpenLines?): Boolean = lines == null || lines.allows(path)

    /** Every lesson of [course] finished at least once. */
    fun isDone(course: Course, progress: Map<String, LessonProgress>): Boolean =
        course.lessons.isNotEmpty() && course.lessons.all { progress[it.id]?.completed == true }
}

/** Openings whose test was passed (Phase 12): stored on the phone, so drills stay open. */
interface OpeningTests {
    fun passed(): kotlinx.coroutines.flow.StateFlow<Set<String>>

    fun pass(openingId: String)
}

/** For tests and previews. */
class InMemoryOpeningTests(initial: Set<String> = emptySet()) : OpeningTests {
    private val state = kotlinx.coroutines.flow.MutableStateFlow(initial)

    override fun passed(): kotlinx.coroutines.flow.StateFlow<Set<String>> = state

    override fun pass(openingId: String) {
        state.value = state.value + openingId
    }
}
