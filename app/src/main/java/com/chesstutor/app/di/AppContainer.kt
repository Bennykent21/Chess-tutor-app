package com.chesstutor.app.di

import android.content.Context
import android.util.Log
import com.chesstutor.app.data.local.AppDatabase
import com.chesstutor.app.data.repository.RatingRepository
import com.chesstutor.app.data.repository.ReviewRepository
import com.chesstutor.app.data.repository.RoomRatingRepository
import com.chesstutor.app.data.repository.RoomReviewRepository
import com.chesstutor.app.engine.EngineClient
import com.chesstutor.app.engine.LocalFallbackEngineClient
import com.chesstutor.app.engine.StockfishProcessEngineClient
import java.io.File

object AppContainer {
    @Volatile
    private var repositoryInstance: ReviewRepository? = null

    @Volatile
    private var ratingRepositoryInstance: RatingRepository? = null

    @Volatile
    private var engineClientInstance: EngineClient? = null

    fun provideReviewRepository(context: Context): ReviewRepository {
        return repositoryInstance ?: synchronized(this) {
            val db = AppDatabase.getInstance(context)
            val repo = RoomReviewRepository(db.reviewItemDao())
            repositoryInstance = repo
            repo
        }
    }

    fun provideRatingRepository(context: Context): RatingRepository {
        return ratingRepositoryInstance ?: synchronized(this) {
            val db = AppDatabase.getInstance(context)
            val repo = RoomRatingRepository(db.linkedProfileDao())
            ratingRepositoryInstance = repo
            repo
        }
    }

    @Volatile
    var engineResolutionDiagnostic: String = "Engine initializing..."
        private set

    fun provideEngineClient(context: Context): EngineClient {
        return engineClientInstance ?: synchronized(this) {
            val nativeDir = context.applicationInfo.nativeLibraryDir
            val primaryPath = "$nativeDir/libstockfish.so"
            val candidatePaths = mutableListOf(
                primaryPath,
                File(context.noBackupFilesDir, "libstockfish.so").absolutePath,
                File(context.filesDir, "libstockfish.so").absolutePath,
                "src/main/jniLibs/x86_64/libstockfish.so",
                "app/src/main/jniLibs/x86_64/libstockfish.so",
                "src/main/jniLibs/arm64-v8a/libstockfish.so",
                "app/src/main/jniLibs/arm64-v8a/libstockfish.so"
            )

            var resolvedBinary: File? = null
            for (path in candidatePaths) {
                val candidate = File(path)
                if (candidate.exists() && candidate.length() > 0) {
                    resolvedBinary = candidate
                    break
                }
            }

            // Fallback: If not found in nativeLibraryDir or file paths, extract from APK directly
            if (resolvedBinary == null) {
                try {
                    val apkFile = File(context.applicationInfo.sourceDir)
                    if (apkFile.exists()) {
                        java.util.zip.ZipFile(apkFile).use { zip ->
                            val supportedAbis = android.os.Build.SUPPORTED_ABIS
                            var matchedEntry: java.util.zip.ZipEntry? = null
                            for (abi in supportedAbis) {
                                val entry = zip.getEntry("lib/$abi/libstockfish.so")
                                if (entry != null) {
                                    matchedEntry = entry
                                    Log.i("AppContainer", "Found Stockfish entry in APK: lib/$abi/libstockfish.so")
                                    break
                                }
                            }
                            if (matchedEntry != null) {
                                val dest = File(context.noBackupFilesDir, "libstockfish.so")
                                zip.getInputStream(matchedEntry).use { input ->
                                    dest.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                dest.setExecutable(true, false)
                                if (dest.exists() && dest.length() > 0) {
                                    resolvedBinary = dest
                                    Log.i("AppContainer", "Successfully extracted Stockfish to ${dest.absolutePath} (${dest.length()} bytes)")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("AppContainer", "Could not extract Stockfish from APK sourceDir", e)
                }
            }

            val targetBinary = resolvedBinary
            val client: EngineClient = if (targetBinary != null) {
                val absPath = targetBinary.absolutePath
                if (!targetBinary.canExecute()) {
                    try {
                        val ok = targetBinary.setExecutable(true, false)
                        Log.i("AppContainer", "setExecutable on $absPath result: $ok")
                    } catch (e: Exception) {
                        Log.w("AppContainer", "Could not setExecutable on $absPath", e)
                    }
                }
                val canExec = targetBinary.canExecute()
                val sizeMb = targetBinary.length() / (1024 * 1024)
                engineResolutionDiagnostic = "Stockfish binary found at $absPath ($sizeMb MB, canExecute=$canExec)"
                Log.i("AppContainer", engineResolutionDiagnostic)
                StockfishProcessEngineClient(absPath)
            } else {
                engineResolutionDiagnostic = "No Stockfish binary found. nativeLibraryDir=$nativeDir, sourceDir=${context.applicationInfo.sourceDir}, supportedAbis=${android.os.Build.SUPPORTED_ABIS.joinToString()}"
                Log.w("AppContainer", engineResolutionDiagnostic)
                LocalFallbackEngineClient()
            }
            engineClientInstance = client
            client
        }
    }
}

