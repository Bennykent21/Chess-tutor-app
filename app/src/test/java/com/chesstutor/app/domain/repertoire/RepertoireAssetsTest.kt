package com.chesstutor.app.domain.repertoire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every bundled repertoire (app/src/main/assets/repertoire, and the debug-only previews in
 * app/src/debug/assets/repertoire) must load: the parser replays every node with the app's own
 * move generator, so a key written differently by the build script fails here, not on a phone.
 * The file name must be the opening's id.
 */
class RepertoireAssetsTest {
    private fun files(folder: String) =
        File(folder).listFiles { f -> f.name.endsWith(".json.gz") }.orEmpty().sortedBy { it.name }

    @Test
    fun everyBundledRepertoireLoads() {
        for (file in files("src/main/assets/repertoire") + files("src/debug/assets/repertoire")) {
            val book = file.inputStream().use { RepertoireParser.fromStream(it, file.name) }
            assertEquals(file.name, file.name.removeSuffix(".json.gz"), book.id)
            assertTrue("${file.name} has no Core cards", book.cards(RepertoireLevel.CORE).isNotEmpty())
        }
    }

    @Test
    fun theDebugPreviewsAreThereAndSaySo() {
        val previews = files("src/debug/assets/repertoire")
        assertEquals(listOf("caro-kann.json.gz", "italian.json.gz"), previews.map { it.name })
        for (file in previews) {
            val book = file.inputStream().use { RepertoireParser.fromStream(it, file.name) }
            assertTrue(book.title.endsWith("(preview)"))
        }
    }
}
