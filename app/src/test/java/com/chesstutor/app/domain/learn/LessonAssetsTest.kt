package com.chesstutor.app.domain.learn

import com.chesstutor.app.domain.puzzle.PuzzleThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every bundled lesson file (app/src/main/assets/lessons) must load: the parser checks each
 * position, move and mark, so a broken lesson fails the build here instead of on a phone.
 */
class LessonAssetsTest {

    private val files = File("src/main/assets/lessons").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
    private val courses by lazy { files.map { LessonParser.parseCourse(it.readText(), it.name) } }

    @Test
    fun everyLessonFileLoads() {
        assertTrue("no lesson files found", files.isNotEmpty())
        assertEquals(files.size, courses.size)
        courses.forEach { course -> assertTrue("${course.id} has no lessons", course.lessons.isNotEmpty()) }
    }

    @Test
    fun courseIdsMatchFileNamesAndLessonIdsAreUnique() {
        files.zip(courses).forEach { (file, course) -> assertEquals("file name", "${course.id}.json", file.name) }
        val lessonIds = courses.flatMap { c -> c.lessons.map { it.id } }
        assertEquals("duplicate lesson ids: ${lessonIds.groupBy { it }.filter { it.value.size > 1 }.keys}", lessonIds.size, lessonIds.toSet().size)
    }

    @Test
    fun relatedPuzzleThemesExistInThePuzzlePack() {
        courses.forEach { course ->
            course.puzzleThemes.forEach { theme -> assertTrue("${course.id}: unknown puzzle theme \"$theme\"", PuzzleThemes.isKnown(theme)) }
        }
    }
}
