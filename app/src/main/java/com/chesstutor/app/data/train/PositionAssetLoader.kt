package com.chesstutor.app.data.train

import android.content.res.AssetManager
import com.chesstutor.app.domain.train.PositionParser
import com.chesstutor.app.domain.train.PositionSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Loads Train's positions (`assets/positions/positions.json`, checked by tools/check_positions.py) once per process. */
class PositionAssetLoader(private val assets: AssetManager) {
    private val lock = Mutex()
    @Volatile private var cached: List<PositionSet>? = null

    suspend fun load(): List<PositionSet> = cached ?: lock.withLock {
        cached ?: withContext(Dispatchers.IO) {
            assets.open(FILE).bufferedReader(Charsets.UTF_8).use { PositionParser.parse(it.readText(), FILE) }
        }.also { cached = it }
    }

    companion object {
        const val FILE = "positions/positions.json"
    }
}
