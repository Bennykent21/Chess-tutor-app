package com.chesstutor.app.data.diagnostics

import com.chesstutor.app.domain.learn.LessonParser
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.domain.puzzle.PuzzlePack
import com.chesstutor.app.feature.account.speedLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PerfLogTest {

    @Test
    fun theSpeedCardSaysWhatWasMeasured() {
        PerfLog.reset()
        assertEquals("not yet", speedLines(PerfLog.snapshot.value).first().second)
        PerfLog.startup(1_840)
        PerfLog.startup(9_999)
        PerfLog.timed("Puzzles") { Thread.sleep(5) }
        PerfLog.analysis(AnalysisTiming(positions = 80, millis = 96_000, depth = 14))
        PerfLog.import(ImportTiming(games = 500, millis = 61_000, source = "Lichess"))
        val lines = speedLines(PerfLog.snapshot.value).toMap()
        assertEquals("the first start-up counts", "1.8 s", lines["App start"])
        assertTrue(lines.getValue("Loading puzzles").endsWith("ms"))
        assertEquals("not yet", lines["Loading openings"])
        assertEquals("80 positions in 96.0 s (1.2 s each, depth 14)", lines["Last game review"])
        assertEquals("500 games from Lichess in 61.0 s", lines["Last import"])
        PerfLog.reset()
    }

    /** How long the bundled data takes to read on this machine, and the pack's memory (a phone is a few times slower). */
    @Test
    fun theBundledDataLoadsQuickly() {
        val runtime = Runtime.getRuntime()
        System.gc()
        val before = runtime.totalMemory() - runtime.freeMemory()
        var start = System.nanoTime()
        val pack = File("src/main/assets/puzzles.tsv.gz").inputStream().use(PuzzlePack::fromStream)
        val packMs = (System.nanoTime() - start) / 1_000_000
        System.gc()
        val packMb = (runtime.totalMemory() - runtime.freeMemory() - before) / 1_048_576
        start = System.nanoTime()
        val tree = File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream)
        val treeMs = (System.nanoTime() - start) / 1_000_000
        start = System.nanoTime()
        val courses = File("src/main/assets/lessons").listFiles()!!.filter { it.name.endsWith(".json") }.map { LessonParser.parseCourse(it.readText(), it.name) }
        val lessonsMs = (System.nanoTime() - start) / 1_000_000
        println("pack: ${pack.puzzles.size} puzzles in $packMs ms, about $packMb MB; openings: ${tree.openings.size} lines in $treeMs ms; lessons: ${courses.size} courses in $lessonsMs ms")
        assertTrue("the pack loads in under 5 s even on a slow CI machine ($packMs ms)", packMs < 5_000)
        assertTrue("the opening tree loads in under 5 s ($treeMs ms)", treeMs < 5_000)
    }
}
