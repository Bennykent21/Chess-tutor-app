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
