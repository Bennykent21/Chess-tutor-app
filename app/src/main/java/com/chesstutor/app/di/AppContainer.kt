package com.chesstutor.app.di

import android.content.Context
import android.util.Log
import com.chesstutor.app.data.local.AppDatabase
import com.chesstutor.app.data.repository.GameRepository
import com.chesstutor.app.data.repository.RoomGameRepository
import com.chesstutor.app.data.repository.RatingRepository
import com.chesstutor.app.data.repository.LearningRepository
import com.chesstutor.app.data.repository.RoomLearningRepository
import com.chesstutor.app.data.repository.ReviewRepository
import com.chesstutor.app.data.repository.RoomRatingRepository
import com.chesstutor.app.data.repository.RoomReviewRepository
import com.chesstutor.app.engine.ChessEngineManager
import com.chesstutor.app.engine.EngineClient
import com.chesstutor.app.engine.LocalFallbackEngineClient
import com.chesstutor.app.engine.OnlineStockfishEngineClient

object AppContainer {
    @Volatile
    private var repositoryInstance: ReviewRepository? = null

    @Volatile
    private var ratingRepositoryInstance: RatingRepository? = null

    @Volatile
    private var learningRepositoryInstance: LearningRepository? = null

    @Volatile
    private var engineClientInstance: EngineClient? = null

    @Volatile
    private var engineManagerInstance: ChessEngineManager? = null

    @Volatile
    private var gameRepositoryInstance: GameRepository? = null

    @Volatile
    private var soundManagerInstance: com.chesstutor.app.audio.ChessSoundManager? = null

    fun provideSoundManager(context: Context): com.chesstutor.app.audio.ChessSoundManager {
        return soundManagerInstance ?: synchronized(this) {
            val sm = com.chesstutor.app.audio.ChessSoundManager(context.applicationContext)
            soundManagerInstance = sm
            sm
        }
    }

    fun provideGameRepository(context: Context): GameRepository {
        return gameRepositoryInstance ?: synchronized(this) {
            val db = AppDatabase.getInstance(context)
            val repo = RoomGameRepository(db.gameRecordDao())
            gameRepositoryInstance = repo
            repo
        }
    }

    fun provideReviewRepository(context: Context): ReviewRepository {
        return repositoryInstance ?: synchronized(this) {
            val db = AppDatabase.getInstance(context)
            val repo = RoomReviewRepository(db.reviewItemDao())
            repositoryInstance = repo
            repo
        }
    }

    fun provideLearningRepository(context: Context): LearningRepository {
        return learningRepositoryInstance ?: synchronized(this) {
            val db = AppDatabase.getInstance(context)
            val repo = RoomLearningRepository(db.learningDao())
            learningRepositoryInstance = repo
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
    var engineResolutionDiagnostic: String =
        "Local Stockfish primary; cloud Stockfish fallback; deterministic fallback last"
        private set

    fun provideEngineClient(context: Context): EngineClient {
        return engineClientInstance ?: synchronized(this) {
            val deterministicFallback = LocalFallbackEngineClient()
            val cloudFallback = OnlineStockfishEngineClient(fallback = deterministicFallback)
            engineResolutionDiagnostic =
                "Engine: Cloud Stockfish with calibrated local Kotlin fallback"
            Log.i("AppContainer", engineResolutionDiagnostic)
            engineClientInstance = cloudFallback
            cloudFallback
        }
    }

    fun provideChessEngineManager(context: Context): ChessEngineManager {
        return engineManagerInstance ?: synchronized(this) {
            val client = provideEngineClient(context)
            val manager = ChessEngineManager(client)
            engineManagerInstance = manager
            manager
        }
    }
}
