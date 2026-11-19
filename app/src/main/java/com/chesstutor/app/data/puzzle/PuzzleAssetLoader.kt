package com.chesstutor.app.data.puzzle

import com.chesstutor.app.data.diagnostics.PerfLog
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
    private val assetName: String? = null
) {
    suspend fun load(): PuzzlePack = withContext(Dispatchers.IO) {
        val candidates = if (assetName != null) {
            listOf(assetName)
        } else {
            listOf(DEFAULT_ASSET, "puzzles.tsv")
        }

        for (candidate in candidates) {
            try {
                context.assets.open(candidate).use { stream ->
                    val pack = PerfLog.timed("Puzzles") { PuzzlePack.fromStream(stream) }
                    if (!pack.isEmpty) {
                        return@withContext if (assetName == null) withOpenings(pack) else pack
                    }
                }
            } catch (_: IOException) {
                // Try next candidate
            } catch (e: Exception) {
                android.util.Log.w("PuzzleAssetLoader", "Error reading puzzle asset '$candidate'", e)
            }
        }
        PuzzlePack.EMPTY
    }

    /** The pack with the opening tactics pack's puzzles added (D112); a puzzle in both keeps its openings. */
    private fun withOpenings(pack: PuzzlePack): PuzzlePack {
        val extra = try {
            context.assets.open(OPENINGS_ASSET).use { PuzzlePack.fromStream(it) }
        } catch (_: IOException) {
            return pack
        }
        if (extra.isEmpty) return pack
        val byId = pack.puzzles.associateBy { it.id } + extra.puzzles.associateBy { it.id }
        return PuzzlePack(byId.values.toList(), pack.skippedLines + extra.skippedLines)
    }

    companion object {
        const val DEFAULT_ASSET = "puzzles.tsv.gz"
        const val OPENINGS_ASSET = "puzzles_openings.tsv.gz"
    }
}
