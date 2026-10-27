package com.chesstutor.app.domain.learn

import com.chesstutor.app.domain.puzzle.PuzzlePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PracticeSetsTest {

    private val pack by lazy { File("src/main/assets/puzzles.tsv.gz").inputStream().use(PuzzlePack::fromStream) }
    private val courses by lazy {
        File("src/main/assets/lessons").listFiles()!!.filter { it.name.endsWith(".json") }.map { LessonParser.parseCourse(it.readText(), it.name) }
    }

    @Test
    fun everyCourseWithThemesGetsTheSameTenPuzzlesEachTime() {
        val withThemes = courses.filter(PracticeSets::offers)
        assertTrue(withThemes.size >= 10)
        withThemes.forEach { course ->
            val set = PracticeSets.puzzlesFor(course, pack)
            assertEquals(course.id, PracticeSets.SIZE, set.size)
            assertEquals("no repeats in ${course.id}", set.size, set.map { it.id }.toSet().size)
            assertEquals("the same set every time", set, PracticeSets.puzzlesFor(course, pack))
            assertEquals("easiest first", set.sortedBy { it.rating }, set)
            val wanted = course.puzzleThemes.toSet()
            assertTrue(set.all { p -> p.primaryTheme in wanted || p.themes.any { it in wanted } })
        }
        courses.filterNot(PracticeSets::offers).forEach { assertTrue(PracticeSets.puzzlesFor(it, pack).isEmpty()) }
    }

    @Test
    fun eightOfTenMastersASet() {
        assertEquals(8, PracticeSets.passMark(10))
        assertEquals(4, PracticeSets.passMark(5))
        assertTrue(PracticeResult("c", best = 8, total = 10, plays = 2, lastPlayedAt = 0).mastered)
        assertFalse(PracticeResult("c", best = 7, total = 10, plays = 2, lastPlayedAt = 0).mastered)
    }
}
