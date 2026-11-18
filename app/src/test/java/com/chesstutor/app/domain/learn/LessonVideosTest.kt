package com.chesstutor.app.domain.learn

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bundled video list and which videos go where (D96). */
class LessonVideosTest {

    private val videos = LessonVideos.parse(File("src/main/assets/videos.json").readText())

    @Test
    fun everyVideoInTheListReadsAndBelongsToACourseOrATopic() {
        assertEquals(84, videos.all.size)
        val courses = File("src/main/assets/lessons").listFiles()!!.filter { it.name.endsWith(".json") }.map { LessonParser.parseCourse(it.readText(), it.name).id }.toSet()
        val topics = LessonCategory.entries.map { "topic:${it.key}" }.toSet()
        val strays = videos.all.map { it.course }.filter { it !in courses && it !in topics }
        assertTrue("videos for no course: $strays", strays.isEmpty())
        assertTrue(videos.all.all { it.url.startsWith("https://www.youtube.com/watch?v=") && it.thumbnail.endsWith("/mqdefault.jpg") })
    }

    private fun video(id: String, course: String, lesson: String? = null, start: Int = 0) =
        LessonVideo(course, LessonLevel.BEGINNER, id.padEnd(11, '_'), id, "Channel", 10, "", lesson, start)

    @Test
    fun aCoursesVideosOpenItsFirstLessonAndALessonsOwnVideoOpensThatLesson() {
        val course = LessonParser.parseCourse(File("src/main/assets/lessons/openings-caro-kann-advance.json").readText())
        val (first, second) = course.lessons.take(2).map { it.id }
        val list = LessonVideos(listOf(video("basics", course.id), video("plan", course.id, lesson = second, start = 95), video("other", "elsewhere")))
        assertEquals(listOf("basics"), list.forLesson(course, first).map { it.title })
        assertEquals(listOf("plan"), list.forLesson(course, second).map { it.title })
        assertEquals(95, list.forLesson(course, second).single().start)
        assertTrue(list.forLesson(course, course.lessons.last().id).isEmpty())
        assertEquals(2, videos.forCourse("openings-caro-kann").size)
        assertTrue(LessonVideos.parse("not json").all.isEmpty())
    }
}
