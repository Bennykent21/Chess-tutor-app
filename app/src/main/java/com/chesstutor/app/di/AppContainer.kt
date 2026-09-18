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
import com.chesstutor.app.engine.OnlineStockfishEngineClient
import com.chesstutor.app.engine.StockfishProcessEngineClient

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
    var engineResolutionDiagnostic: String = "Local Stockfish primary; cloud Stockfish fallback; deterministic fallback last"
        private set

    fun provideEngineClient(context: Context): EngineClient {
        return engineClientInstance ?: synchronized(this) {
            val deterministicFallback = LocalFallbackEngineClient()
            val cloudFallback = OnlineStockfishEngineClient(fallback = deterministicFallback)
            val binaryPath = runCatching {
                com.chesstutor.app.engine.StockfishBinaryProvider.resolve(context)
            }.getOrElse { error ->
                engineResolutionDiagnostic = "Bundled Stockfish unavailable: " + error.message
                Log.e("AppContainer", engineResolutionDiagnostic, error)
                throw error
            }

            val client: EngineClient = StockfishProcessEngineClient(
                binaryPath = binaryPath.absolutePath,
                fallbackClient = cloudFallback
            )

            engineResolutionDiagnostic =
                "Engine chain: bundled Stockfish (${binaryPath.absolutePath}) -> cloud Stockfish -> deterministic local fallback"
            Log.i("AppContainer", engineResolutionDiagnostic)
            engineClientInstance = client
            client
        }
    }
}

