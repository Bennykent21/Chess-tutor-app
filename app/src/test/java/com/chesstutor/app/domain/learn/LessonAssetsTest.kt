package com.chesstutor.app.domain.learn

import com.chesstutor.app.domain.opening.OpeningBattles
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.domain.puzzle.PuzzleModes
import com.chesstutor.app.domain.puzzle.PuzzlePack
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

    /** "Practise in Opening Battle" must find each course's opening, on the course's side of the board. */
    @Test
    fun battleLinesExistInTheOpeningDatabase() {
        val tree = File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream)
        val withLines = courses.filter { it.battleLine != null }
        assertEquals("every opening except the principles course links to Opening Battle", 8, withLines.size)
        withLines.forEach { course ->
            val line = OpeningBattles.presetLine(tree, course.battleLine!!)
            assertTrue("${course.id}: no line named \"${course.battleLine}\"", line != null)
            assertEquals("${course.id}: the main line", course.battleLine, line!!.name)
        }
    }

    /** "Related puzzles" must have a full run of puzzles for every course that offers it. */
    @Test
    fun relatedPuzzlesExistForEveryCourse() {
        val pack = File("src/main/assets/puzzles.tsv.gz").inputStream().use(PuzzlePack::fromStream)
        courses.filter { it.puzzleThemes.isNotEmpty() }.forEach { course ->
            val run = PuzzleModes.related(pack, course.puzzleThemes, rating = 1200, count = 10, seed = 1)
            assertEquals("${course.id}: puzzles for ${course.puzzleThemes}", 10, run.size)
        }
    }

    @Test
    fun relatedPuzzleThemesExistInThePuzzlePack() {
        courses.forEach { course ->
            course.puzzleThemes.forEach { theme -> assertTrue("${course.id}: unknown puzzle theme \"$theme\"", PuzzleThemes.isKnown(theme)) }
        }
    }
}
