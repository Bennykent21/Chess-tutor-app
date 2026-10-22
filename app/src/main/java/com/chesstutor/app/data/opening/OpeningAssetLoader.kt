package com.chesstutor.app.data.opening

import android.content.res.AssetManager
import com.chesstutor.app.domain.opening.OpeningTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Loads the bundled opening database `app/src/main/assets/openings.tsv.gz` (built by
 * tools/build_openings.py) once per process. A missing or unreadable asset gives an empty
 * tree, so bots simply play without a book instead of crashing.
 */
class OpeningAssetLoader(private val assets: AssetManager, private val assetName: String? = null) {
    private val lock = Mutex()
    @Volatile private var cached: OpeningTree? = null

    suspend fun load(): OpeningTree = cached ?: lock.withLock {
        cached ?: withContext(Dispatchers.Default) { read() }.also { cached = it }
    }

    private fun read(): OpeningTree {
        // The asset merger unpacks .gz files and drops the extension, so try both names.
        val candidates = assetName?.let(::listOf) ?: listOf(DEFAULT_ASSET, DEFAULT_ASSET.removeSuffix(".gz"))
        for (candidate in candidates) {
            try {
                val tree = assets.open(candidate).use(OpeningTree::fromStream)
                if (!tree.isEmpty) return tree
            } catch (_: IOException) {
                // Try the next name.
            } catch (e: Exception) {
                android.util.Log.w("OpeningAssetLoader", "Error reading opening asset '$candidate'", e)
            }
        }
        return OpeningTree.EMPTY
    }

    companion object {
        const val DEFAULT_ASSET = "openings.tsv.gz"
    }
}
