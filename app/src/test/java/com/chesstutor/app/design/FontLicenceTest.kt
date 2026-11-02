package com.chesstutor.app.design

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every bundled font is recorded in LICENSES.md, and the shipped OFL text names its authors. */
class FontLicenceTest {

    private val fonts = File("src/main/res/font").listFiles()!!.filter { it.extension == "ttf" }
    private val licences = File("../LICENSES.md").readText()
    private val ofl = File("src/main/assets/licenses/OFL-1.1.txt").readText()

    /** Paths in LICENSES.md, where `*` stands for any text (`inter_*.ttf`). */
    private val recordedPatterns = Regex("`app/src/main/res/font/([^`]+)`").findAll(licences)
        .map { Regex(Regex.escape(it.groupValues[1]).replace("*", "\\E.*\\Q")) }
        .toList()

    @Test
    fun everyBundledFontIsRecorded() {
        assertTrue("fonts are bundled", fonts.isNotEmpty())
        val missing = fonts.map { it.name }.filter { name -> recordedPatterns.none { it.matches(name) } }
        assertTrue("Fonts missing from LICENSES.md: $missing", missing.isEmpty())
    }

    @Test
    fun theOflTextNamesEveryFontFamily() {
        listOf("Schibsted-Grotesk Project Authors", "Inter Project Authors", "JetBrains Mono Project Authors").forEach {
            assertTrue("OFL text should name $it", ofl.contains(it))
        }
        assertTrue("Nunito is no longer bundled", !ofl.contains("Nunito"))
    }
}
