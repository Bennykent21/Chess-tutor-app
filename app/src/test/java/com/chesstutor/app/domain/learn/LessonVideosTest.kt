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

    @Test
    fun aTopicTierGetsItsCoursesVideosThenTheTopicsOwn() {
        val endgames = setOf("endgames-king-and-pawn", "endgames-queen", "endgames-rook-technique", "endgames-rook", "endgames-technique")
        val advanced = videos.forTopic(LessonCategory.ENDGAMES, LessonLevel.ADVANCED, endgames)
        assertTrue(advanced.all { it.level == LessonLevel.ADVANCED })
        assertTrue("the topic's extra videos come last", advanced.takeLast(2).all { it.course == "topic:endgames" })
        assertEquals(2, videos.forCourse("openings-caro-kann").size)
        assertTrue(LessonVideos.parse("not json").all.isEmpty())
    }
}
