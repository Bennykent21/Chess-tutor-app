package com.chesstutor.app.domain.learn

import com.squareup.moshi.Moshi

/**
 * A free YouTube video that teaches a course's topic (D96), from the bundled `videos.json`:
 * links only, every one checked against YouTube when the list was made.
 */
data class LessonVideo(
    /** The course it belongs to, or "topic:<category key>" for a topic's extra videos. */
    val course: String,
    val level: LessonLevel,
    val id: String,
    val title: String,
    val channel: String,
    val minutes: Int,
    /** One line on what it teaches. */
    val why: String,
    /** The lesson it opens, when it is meant for one lesson; else the course's first lesson. */
    val lesson: String? = null,
    /** Where in the video that lesson's part starts, in seconds. */
    val start: Int = 0,
) {
    val url: String get() = "https://www.youtube.com/watch?v=$id"

    /** YouTube's own 320 by 180 picture of the video. */
    val thumbnail: String get() = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
}

/**
 * The videos and where each shows (D100): a topic's tier and an opening list theirs before a course
 * is chosen, a course's general video heads its page, and each lesson opens on its own.
 */
class LessonVideos(val all: List<LessonVideo>) {

    fun forCourse(courseId: String): List<LessonVideo> = all.filter { it.course == courseId }

    /** The course's general video, at the top of its page in place of the art. */
    fun courseVideo(courseId: String): LessonVideo? = all.firstOrNull { it.course == courseId && it.lesson == null }

    /**
     * The videos a lesson opens on: the ones made for it; until those are set, the first lesson
     * gets the course's other general videos. Other lessons go straight to the board.
     */
    fun forLesson(course: Course, lessonId: String): List<LessonVideo> {
        val own = all.filter { it.lesson == lessonId }
        if (own.isNotEmpty()) return own
        if (course.lessons.firstOrNull()?.id != lessonId) return emptyList()
        return forCourse(course.id).filter { it.lesson == null }.drop(1)
    }

    /** A topic's videos at [level], one per video: its courses' general ones, then the topic's own, shortest first. */
    fun forTopic(category: LessonCategory, level: LessonLevel, courseIds: Set<String>): List<LessonVideo> =
        all.filter { it.level == level && it.lesson == null && (it.course in courseIds || it.course == "topic:${category.key}") }
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.course.startsWith("topic:") }, { it.minutes }))

    /** An opening's videos: every course's general one, its ideas first. */
    fun forCourses(courseIds: List<String>): List<LessonVideo> = courseIds.flatMap { forCourse(it).filter { v -> v.lesson == null } }.distinctBy { it.id }

    fun byId(id: String): LessonVideo? = all.firstOrNull { it.id == id }

    companion object {
        val EMPTY = LessonVideos(emptyList())

        private val adapter = Moshi.Builder().build().adapter(Any::class.java)

        /** Reads `videos.json`; anything unreadable is left out. */
        fun parse(json: String): LessonVideos {
            val rows = runCatching { adapter.fromJson(json) as? List<*> }.getOrNull().orEmpty()
            return LessonVideos(
                rows.mapNotNull { row ->
                    val m = row as? Map<*, *> ?: return@mapNotNull null
                    LessonVideo(
                        course = m["course"] as? String ?: return@mapNotNull null,
                        level = LessonLevel.fromKey(m["level"] as? String ?: "") ?: return@mapNotNull null,
                        id = (m["id"] as? String)?.takeIf { it.matches(VIDEO_ID) } ?: return@mapNotNull null,
                        title = m["title"] as? String ?: return@mapNotNull null,
                        channel = m["channel"] as? String ?: "",
                        minutes = (m["minutes"] as? Number)?.toInt() ?: 0,
                        why = m["why"] as? String ?: "",
                        lesson = m["lesson"] as? String,
                        start = (m["start"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                    )
                },
            )
        }

        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    }
}
