package com.chesstutor.app.data.puzzle

import android.content.Context
import com.chesstutor.app.domain.puzzle.PuzzlePack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Loads the bundled puzzle pack from `app/src/main/assets/puzzles.tsv.gz`
 * (generate it with tools/build_puzzle_pack.py). If the asset is missing or unreadable the app gets
 * an empty pack instead of crashing, so the UI can show a "puzzles unavailable" state.
 */
class PuzzleAssetLoader(
    private val context: Context,
    private val assetName: String = DEFAULT_ASSET
) {
    suspend fun load(): PuzzlePack = withContext(Dispatchers.IO) {
        try {
            context.assets.open(assetName).use { PuzzlePack.fromGzipStream(it) }
        } catch (_: IOException) {
            PuzzlePack.EMPTY
        }
    }

    companion object {
        const val DEFAULT_ASSET = "puzzles.tsv.gz"
    }
}
