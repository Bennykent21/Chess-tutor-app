package com.chesstutor.app.data.opening

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpeningAssetLoaderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theBundledAssetLoadsOnceAndIsShared() = runBlocking {
        val loader = OpeningAssetLoader(context.assets)
        val tree = loader.load()
        assertEquals(0, tree.skippedLines)
        assertTrue(tree.openings.size > 3_500)
        assertSame("read once per process", tree, loader.load())
    }

    @Test
    fun aMissingAssetGivesAnEmptyTree() = runBlocking {
        assertTrue(OpeningAssetLoader(context.assets, "missing.tsv.gz").load().isEmpty)
    }
}
