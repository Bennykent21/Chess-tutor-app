package com.chesstutor.app.domain.learn

import com.chesstutor.app.core.PieceColor

/** The six Learn hub buttons (plan section 6.2). [key] is the name used in lesson files. */
enum class LessonCategory(val key: String, val title: String) {
    BASICS("basics", "Basics"),
    OPENINGS("openings", "Openings"),
    TACTICS("tactics", "Tactics"),
    MIDDLEGAME("middlegame", "Middlegame"),
    ENDGAMES("endgames", "Endgames"),
    BLUNDERS("blunders", "Blunders");

    companion object {
        fun fromKey(key: String): LessonCategory? = entries.firstOrNull { it.key == key }
    }
}

enum class LessonLevel(val key: String, val label: String) {
    BEGINNER("beginner", "Beginner"),
    INTERMEDIATE("intermediate", "Intermediate"),
    ADVANCED("advanced", "Advanced");

    companion object {
        fun fromKey(key: String): LessonLevel? = entries.firstOrNull { it.key == key }
    }
}

/** How a mark is coloured on the board: a good idea, a mistake, a hint or an alternative. */
enum class MarkTone(val key: String) {
    GOOD("good"),
    BAD("bad"),
    HINT("hint"),
    IDEA("idea");

    companion object {
        fun fromKey(key: String): MarkTone? = entries.firstOrNull { it.key == key }
    }
}

/** An arrow drawn for teaching, such as "e2e4" or "f3g5:bad" in a lesson file. */
data class LessonArrow(val from: String, val to: String, val tone: MarkTone = MarkTone.GOOD)

/** A ringed square, such as "f7" or "f7:bad" in a lesson file. */
data class LessonHighlight(val square: String, val tone: MarkTone = MarkTone.HINT)

/** Moves that count as solving a "play" or "quiz" step without being listed one by one. */
enum class MoveGoal(val key: String) {
    /** Any move that checkmates. */
    MATE("mate"),
    /** Any move that gives check. */
    CHECK("check");

    companion object {
        fun fromKey(key: String): MoveGoal? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One turn of the player in a "play" or "quiz" step: the moves accepted (UCI) and the
 * opponent's reply that follows, if any.
 */
data class PlayerTurn(val accepted: List<String>, val reply: String? = null)

/**
 * One step of a lesson (plan section 11). [fen] is always filled in after parsing: a step
 * whose file entry leaves it out starts where the previous step ended.
 */
sealed interface LessonStep {
    val text: String
    val fen: String
    val arrows: List<LessonArrow>
    val highlights: List<LessonHighlight>

    /** The "Watch" part: a video slot, a placeholder until videos exist (plan Phase 4). */
    data class Watch(
        val title: String,
        override val text: String,
        override val fen: String,
        /** Asset name of the video; null shows the placeholder. */
        val video: String? = null,
    ) : LessonStep {
        override val arrows: List<LessonArrow> get() = emptyList()
        override val highlights: List<LessonHighlight> get() = emptyList()
    }

    /** The "Learn" part: the coach explains, optionally sliding one [move] on the board. */
    data class Explain(
        override val text: String,
        override val fen: String,
        override val arrows: List<LessonArrow> = emptyList(),
        override val highlights: List<LessonHighlight> = emptyList(),
        val move: String? = null,
    ) : LessonStep

    /**
     * "Try" ([quiz] false) or "Quiz" ([quiz] true): the player finds the move, or plays a
     * short line with the opponent's replies. Either [turns] or a [goal] says what is right.
     */
    data class Play(
        override val text: String,
        override val fen: String,
        override val arrows: List<LessonArrow> = emptyList(),
        override val highlights: List<LessonHighlight> = emptyList(),
        val turns: List<PlayerTurn> = emptyList(),
        val goal: MoveGoal? = null,
        val hint: String? = null,
        /** Said after any wrong move without its own [feedback]. */
        val ifWrong: String? = null,
        /** Said when the step is solved. */
        val ifRight: String? = null,
        /** What the coach says for particular moves (right or wrong), keyed by UCI. */
        val feedback: Map<String, String> = emptyMap(),
        val quiz: Boolean = false,
    ) : LessonStep
}

data class Lesson(
    val id: String,
    val title: String,
    val summary: String,
    val steps: List<LessonStep>,
    /** XP for finishing it the first time. */
    val xp: Int = DEFAULT_XP,
    /** The side at the bottom of the board. */
    val orientation: PieceColor = PieceColor.WHITE,
) {
    val quizCount: Int get() = steps.count { it is LessonStep.Play && it.quiz }

    companion object {
        const val DEFAULT_XP = 20
    }
}

/**
 * One lesson page (plan section 6.2), for example "Italian Game": an intro, numbered
 * lessons and the links to matching puzzles and Opening Battle. One file in assets/lessons.
 */
data class Course(
    val id: String,
    val category: LessonCategory,
    val title: String,
    val intro: String,
    val level: LessonLevel,
    /** Position on its category page. */
    val order: Int,
    val lessons: List<Lesson>,
    /** Puzzle themes (Lichess names, such as "fork") for "Related puzzles". */
    val puzzleThemes: List<String> = emptyList(),
    /** An opening name from the opening database, for "Practise in Opening Battle". */
    val battleLine: String? = null,
    /** For openings: the side the course teaches; null when it suits both. */
    val side: PieceColor? = null,
)

/** Every course in the app, in hub order. */
data class LessonLibrary(val courses: List<Course>) {
    private val lessonIndex: Map<String, Pair<Course, Int>> =
        courses.flatMap { course -> course.lessons.mapIndexed { i, lesson -> lesson.id to (course to i) } }.toMap()

    fun course(id: String): Course? = courses.firstOrNull { it.id == id }

    fun coursesIn(category: LessonCategory): List<Course> = courses.filter { it.category == category }.sortedBy { it.order }

    fun lesson(id: String): Lesson? = lessonIndex[id]?.let { (course, i) -> course.lessons[i] }

    fun courseOf(lessonId: String): Course? = lessonIndex[lessonId]?.first

    /** The lesson after [lessonId]: the next one in its course, else the first of the next course in the category. */
    fun nextAfter(lessonId: String): Lesson? {
        val (course, i) = lessonIndex[lessonId] ?: return null
        course.lessons.getOrNull(i + 1)?.let { return it }
        val siblings = coursesIn(course.category)
        val at = siblings.indexOfFirst { it.id == course.id }
        return siblings.drop(at + 1).firstOrNull { it.lessons.isNotEmpty() }?.lessons?.first()
    }

    val isEmpty: Boolean get() = courses.isEmpty()

    companion object {
        val EMPTY = LessonLibrary(emptyList())
    }
}
