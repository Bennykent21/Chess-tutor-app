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
) {
    val url: String get() = "https://www.youtube.com/watch?v=$id"

    /** YouTube's own 320 by 180 picture of the video. */
    val thumbnail: String get() = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
}

/** The videos, and which ones go with a course, a topic's tier or an opening (D96). */
class LessonVideos(val all: List<LessonVideo>) {

    fun forCourse(courseId: String): List<LessonVideo> = all.filter { it.course == courseId }

    /** A topic's videos at [level]: its courses' own, then the topic's extra ones, longest last. */
    fun forTopic(category: LessonCategory, level: LessonLevel, courseIds: Set<String>): List<LessonVideo> =
        all.filter { it.level == level && (it.course in courseIds || it.course == "topic:${category.key}") }
            .sortedWith(compareBy({ it.course.startsWith("topic:") }, { it.minutes }))

    /** An opening's videos: every course of it, its ideas first. */
    fun forCourses(courseIds: List<String>): List<LessonVideo> = courseIds.flatMap(::forCourse)

    fun byId(id: String): LessonVideo? = all.firstOrNull { it.id == id }

    /**
     * What to watch after [video] (D98): the same topic only, its course first, then its level,
     * then the levels nearest to it, shortest first within each.
     */
    fun upNext(video: LessonVideo, topicOf: (courseId: String) -> String?, limit: Int = 8): List<LessonVideo> {
        val topic = topicKey(video, topicOf) ?: return forCourse(video.course).filter { it.id != video.id }.take(limit)
        return all.asSequence()
            .filter { it.id != video.id && topicKey(it, topicOf) == topic }
            .sortedWith(compareBy({ it.course != video.course }, { kotlin.math.abs(it.level.ordinal - video.level.ordinal) }, { it.minutes }))
            .distinctBy { it.id }
            .take(limit)
            .toList()
    }

    /** "For you" (D98): the videos of the courses given, in that order, each video once. */
    fun forYou(courseIds: List<String>, limit: Int = 8): List<LessonVideo> =
        courseIds.distinct().asSequence().flatMap { forCourse(it).asSequence() }.distinctBy { it.id }.take(limit).toList()

    private fun topicKey(video: LessonVideo, topicOf: (String) -> String?): String? =
        if (video.course.startsWith("topic:")) video.course.removePrefix("topic:") else topicOf(video.course)

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
                    )
                },
            )
        }

        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    }
}
