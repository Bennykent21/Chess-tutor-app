package com.chesstutor.app.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CrashLogTest {

    @Test
    fun keepsTheNewestCrashesWithTheirStackTraceAndCanBeCleared() {
        val dir = Files.createTempDirectory("crash").toFile()
        val log = CrashLog(File(dir, CrashLog.FILE_NAME))
        assertEquals("", log.read())
        assertEquals(0, log.count())

        repeat(CrashLog.MAX_CRASHES + 2) { i ->
            log.record("main", IllegalStateException("boom $i"), at = 1_700_000_000_000L + i, about = "Chess Tutor test · Android 14")
        }
        assertEquals(CrashLog.MAX_CRASHES, log.count())
        val text = log.read()
        assertTrue("the oldest crashes were dropped", !Regex("boom [01]\\b").containsMatchIn(text))
        assertTrue(text, "boom ${CrashLog.MAX_CRASHES + 1}" in text)
        assertTrue("the stack trace is kept", "at com.chesstutor.app.data.diagnostics.CrashLogTest" in text)
        assertTrue("2023-11-14 22:13:20 UTC" in text)
        assertTrue("Chess Tutor test · Android 14" in text)

        log.clear()
        assertEquals("", log.read())
        dir.deleteRecursively()
    }
}
