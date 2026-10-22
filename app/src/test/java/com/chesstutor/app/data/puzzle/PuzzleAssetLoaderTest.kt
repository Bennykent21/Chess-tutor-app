package com.chesstutor.app.data.puzzle

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chesstutor.app.domain.puzzle.PuzzlePack
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
class PuzzleAssetLoaderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun loadBundledAsset_returnsNonEmptyPack() = runBlocking {
        val loader = PuzzleAssetLoader(context)
        val pack = loader.load()
        assertFalse("Bundled puzzle pack should not be empty", pack.isEmpty)
        assertTrue("Expected hundreds of puzzles in pack, found ${pack.size}", pack.size > 100)

        // Verify themes and ratings are loaded
        assertTrue(pack.themes.isNotEmpty())
        val first = pack.puzzles.first()
        assertNotNull(first.id)
        assertNotNull(first.fen)
        assertTrue(first.moves.size >= 2)
    }

    /** Pack v2: every line parses, it covers the level path, and the alternatives come through. */
    @Test
    fun bundledPackV2_parsesFullyAndCarriesAlternatives() = runBlocking {
        val pack = PuzzleAssetLoader(context).load()
        assertEquals("no line may be dropped by the parser", 0, pack.skippedLines)
        assertTrue("expected the v2 pack (13,485 puzzles), found ${pack.size}", pack.size >= 13_000)
        assertTrue(pack.puzzles.any { it.alternatives.isNotEmpty() })
        // Every level of the path has puzzles near its rating.
        (1..50).forEach { level ->
            val levelPuzzles = com.chesstutor.app.domain.puzzle.PuzzleLevels.puzzlesFor(level, 0, pack)
            assertEquals("level $level", 5, levelPuzzles.size)
        }
        // Every puzzle can be set up (its opening and first solution move are legal).
        val unplayable = pack.puzzles.count { com.chesstutor.app.domain.puzzle.PuzzleSession.start(it) == null }
        assertEquals("unplayable puzzles", 0, unplayable)
    }

    @Test
    fun fromStream_handlesBothGzipAndPlainText() {
        val line = "p1\trnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1\te2e4 e7e5\t1000\tfork\tfork\t90\n"

        // 1. Plain text stream
        val plainStream = ByteArrayInputStream(line.toByteArray(Charsets.UTF_8))
        val plainPack = PuzzlePack.fromStream(plainStream)
        assertEquals(1, plainPack.size)
        assertEquals("p1", plainPack.puzzles[0].id)

        // 2. Gzipped stream
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(line.toByteArray(Charsets.UTF_8)) }
        val gzipStream = ByteArrayInputStream(bos.toByteArray())
        val gzipPack = PuzzlePack.fromStream(gzipStream)
        assertEquals(1, gzipPack.size)
        assertEquals("p1", gzipPack.puzzles[0].id)
    }

    @Test
    fun loadMissingAsset_returnsEmptyPackGracefully() = runBlocking {
        val loader = PuzzleAssetLoader(context, "non_existent_file.tsv.gz")
        val pack = loader.load()
        assertTrue(pack.isEmpty)
    }
}
