package com.chesstutor.app.engine

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Resolves the bundled Stockfish executable from app assets into an executable
 * private-files location. Assets are not executable directly and are not part
 * of Android nativeLibraryDir, so the process engine must use this resolver.
 */
object StockfishBinaryProvider {

    private const val ASSET_ROOT = "stockfish"
    private const val FILE_NAME = "libstockfish.so"

    fun resolve(context: Context): File {
        val abi = selectAbi()
        val targetDir = File(context.filesDir, "stockfish/$abi")
        val target = File(targetDir, FILE_NAME)

        if (!target.exists() || target.length() == 0L) {
            targetDir.mkdirs()
            val assetPath = "$ASSET_ROOT/$abi/$FILE_NAME"
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }

        check(target.exists() && target.length() > 0L) {
            "Bundled Stockfish binary was not copied to ${target.absolutePath}"
        }
        check(target.setExecutable(true, true) || target.canExecute()) {
            "Stockfish binary could not be marked executable at ${target.absolutePath}"
        }
        return target
    }

    private fun selectAbi(): String {
        val supported = Build.SUPPORTED_ABIS.toSet()
        return when {
            "arm64-v8a" in supported -> "arm64-v8a"
            "x86_64" in supported -> "x86_64"
            else -> error("No bundled Stockfish binary for supported ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        }
    }
}