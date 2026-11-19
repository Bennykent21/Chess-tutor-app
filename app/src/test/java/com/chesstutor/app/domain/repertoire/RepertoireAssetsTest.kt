package com.chesstutor.app.domain.repertoire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every bundled repertoire (app/src/main/assets/repertoire) must load: the parser replays every node with the app's own
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
    fun thePilotOpeningsAreRealDataAndNoPreviewHidesThem() {
        val bundled = files("src/main/assets/repertoire")
        assertTrue(bundled.map { it.name }.containsAll(listOf("caro-kann.json.gz", "italian.json.gz")))
        for (file in bundled) {
            val book = file.inputStream().use { RepertoireParser.fromStream(it, file.name) }
            assertTrue("${file.name} is a preview", !book.title.endsWith("(preview)"))
        }
        // A debug asset of the same name would replace the real one in debug builds.
        assertTrue(files("src/debug/assets/repertoire").isEmpty())
    }

    /** Punish mistakes (D112): the positions right after a common club mistake, most common first, each with our answer. */
    @Test
    fun theItaliansCommonMistakesCanBePunished() {
        val book = File("src/main/assets/repertoire/italian.json.gz").inputStream().use { RepertoireParser.fromStream(it, "italian.json.gz") }
        val index = RepertoireIndex(book)
        val found = Drills.punish(index, emptyList(), java.time.Instant.EPOCH)
        assertTrue("found ${found.size}", found.size >= 5)
        assertEquals(found.map { it.second }.sortedDescending(), found.map { it.second })
        for ((card, _) in found) {
            assertTrue(card.node.own && card.node.move != null)
            val before = index.positionOf(book.nodes.values.first { node -> !node.own && node.replies.any { it.punish && RepertoireIndex.play(index.positionOf(node.key), it.uci)?.let(book::at) == card.node } }.key)
            assertTrue(card.path.isNotEmpty() && before.sideToMove != card.position.sideToMove)
        }
    }
}
