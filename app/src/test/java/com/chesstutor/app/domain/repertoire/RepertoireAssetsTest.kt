package com.chesstutor.app.domain.repertoire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every bundled repertoire (app/src/main/assets/repertoire) must load: the parser replays
 * every node with the app's own move generator, so a key written differently by the build
 * script fails here, not on a phone. The file name must be the opening's id.
 */
class RepertoireAssetsTest {
    private val files = File("src/main/assets/repertoire").listFiles { f -> f.name.endsWith(".json.gz") }.orEmpty().sortedBy { it.name }

    @Test
    fun everyBundledRepertoireLoads() {
        for (file in files) {
            val book = file.inputStream().use { RepertoireParser.fromStream(it, file.name) }
            assertEquals(file.name, file.name.removeSuffix(".json.gz"), book.id)
            assertTrue("${file.name} has no Core cards", book.cards(RepertoireLevel.CORE).isNotEmpty())
        }
    }
}
