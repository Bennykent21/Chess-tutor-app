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

    private fun video(id: String, course: String, level: LessonLevel = LessonLevel.BEGINNER, minutes: Int = 10) =
        LessonVideo(course, level, id.padEnd(11, '_'), id, "Channel", minutes, "")

    @Test
    fun upNextStaysInTheTopicItsCourseFirstThenTheNearestLevel() {
        val list = LessonVideos(
            listOf(
                video("now", "rook"),
                video("sameCourse", "rook", minutes = 30),
                video("topicExtra", "topic:endgames", LessonLevel.ADVANCED),
                video("otherCourse", "pawn", LessonLevel.BEGINNER, minutes = 5),
                video("middle", "pawn", LessonLevel.INTERMEDIATE),
                video("tactics", "forks"),
            ),
        )
        val topics = mapOf("rook" to "endgames", "pawn" to "endgames", "forks" to "tactics")
        val next = list.upNext(list.byId("now".padEnd(11, '_'))!!, topicOf = topics::get).map { it.title }
        assertEquals(listOf("sameCourse", "otherCourse", "middle", "topicExtra"), next)
    }

    @Test
    fun forYouFollowsTheCoursesInOrderAndShowsEachVideoOnce() {
        val list = LessonVideos(listOf(video("a", "one"), video("b", "two"), video("a", "two"), video("c", "three")))
        assertEquals(listOf("b", "a", "c"), list.forYou(listOf("two", "one", "three", "two")).map { it.title })
        assertEquals(2, list.forYou(listOf("two", "one", "three"), limit = 2).size)
    }
}
