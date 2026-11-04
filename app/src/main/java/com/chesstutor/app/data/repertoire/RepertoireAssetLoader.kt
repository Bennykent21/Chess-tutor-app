package com.chesstutor.app.data.repertoire

import android.content.res.AssetManager
import com.chesstutor.app.data.diagnostics.PerfLog
import com.chesstutor.app.domain.repertoire.RepertoireBook
import com.chesstutor.app.domain.repertoire.RepertoireParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Loads the taught openings in `app/src/main/assets/repertoire/` (built by
 * tools/build_repertoire.py) once per process. A file that does not load is skipped with a
 * warning, so one bad opening never takes the others down; RepertoireAssetsTest makes sure
 * every bundled file loads.
 */
class RepertoireAssetLoader(private val assets: AssetManager, private val folder: String = FOLDER) {
    private val lock = Mutex()
    @Volatile private var cached: List<RepertoireBook>? = null

    suspend fun load(): List<RepertoireBook> = cached ?: lock.withLock {
        cached ?: withContext(Dispatchers.Default) { PerfLog.timed("Repertoires") { read() } }.also { cached = it }
    }

    private fun read(): List<RepertoireBook> {
        // The asset merger may unpack .gz files and drop the extension, so take both names.
        val names = assets.list(folder).orEmpty().filter { it.endsWith(".json.gz") || it.endsWith(".json") }.sorted()
        return names.mapNotNull { name ->
            try {
                assets.open("$folder/$name").use { RepertoireParser.fromStream(it, name) }
            } catch (e: Exception) {
                android.util.Log.w("RepertoireAssetLoader", "Skipping repertoire '$name'", e)
                null
            }
        }.distinctBy { it.id }
    }

    companion object {
        const val FOLDER = "repertoire"
    }
}
