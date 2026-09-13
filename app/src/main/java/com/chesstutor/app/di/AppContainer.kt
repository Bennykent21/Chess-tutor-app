package com.chesstutor.app.di

import android.content.Context
import com.chesstutor.app.data.local.AppDatabase
import com.chesstutor.app.data.repository.ReviewRepository
import com.chesstutor.app.data.repository.RoomReviewRepository
import com.chesstutor.app.engine.EngineClient
import com.chesstutor.app.engine.LocalFallbackEngineClient
import com.chesstutor.app.engine.StockfishProcessEngineClient
import java.io.File

object AppContainer {
    @Volatile
    private var repositoryInstance: ReviewRepository? = null

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

    fun provideEngineClient(context: Context): EngineClient {
        return engineClientInstance ?: synchronized(this) {
            val nativePath = "${context.applicationInfo.nativeLibraryDir}/libstockfish.so"
            val binary = File(nativePath)
            val client: EngineClient = if (binary.exists() && binary.canExecute()) {
                StockfishProcessEngineClient(nativePath)
            } else {
                LocalFallbackEngineClient()
            }
            engineClientInstance = client
            client
        }
    }
}
