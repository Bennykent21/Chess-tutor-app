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

    fun provideEngineClient(context: Context): EngineClient {
        return engineClientInstance ?: synchronized(this) {
            val nativePath = "${context.applicationInfo.nativeLibraryDir}/libstockfish.so"
            val binary = File(nativePath)
            if (binary.exists() && !binary.canExecute()) {
                try {
                    val ok = binary.setExecutable(true, false)
                    Log.i("AppContainer", "Attempted setExecutable on $nativePath: $ok")
                } catch (e: Exception) {
                    Log.w("AppContainer", "Could not setExecutable on $nativePath", e)
                }
            }
            val exists = binary.exists()
            val canExec = binary.canExecute()
            Log.i("AppContainer", "Stockfish resolution at $nativePath: exists=$exists, canExecute=$canExec")
            val client: EngineClient = if (exists && canExec) {
                StockfishProcessEngineClient(nativePath)
            } else {
                Log.w("AppContainer", "Falling back to LocalFallbackEngineClient (exists=$exists, canExec=$canExec)")
                LocalFallbackEngineClient()
            }
            engineClientInstance = client
            client
        }
    }
}

