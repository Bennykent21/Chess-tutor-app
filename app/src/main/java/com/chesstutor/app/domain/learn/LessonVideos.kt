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

/** The videos, and which lesson each one opens (D99): every lesson has its own, no feed. */
class LessonVideos(val all: List<LessonVideo>) {

    fun forCourse(courseId: String): List<LessonVideo> = all.filter { it.course == courseId }

    /**
     * The videos a lesson opens on: the ones made for it, or, for a course's first lesson, the
     * course's videos not tied to a lesson. Other lessons go straight to the board.
     */
    fun forLesson(course: Course, lessonId: String): List<LessonVideo> {
        val own = all.filter { it.lesson == lessonId }
        if (own.isNotEmpty()) return own
        return if (course.lessons.firstOrNull()?.id == lessonId) forCourse(course.id).filter { it.lesson == null } else emptyList()
    }

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
