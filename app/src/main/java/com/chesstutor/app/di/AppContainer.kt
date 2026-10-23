package com.chesstutor.app.di

import android.content.Context
import android.util.Log
import com.chesstutor.app.data.bot.RoomBotRepository
import com.chesstutor.app.data.local.AppDatabase
import com.chesstutor.app.data.maia.MaiaAssetLoader
import com.chesstutor.app.domain.bot.BotRepository
import com.chesstutor.app.data.opening.OpeningAssetLoader
import com.chesstutor.app.data.puzzle.RoomPuzzleRepository
import com.chesstutor.app.data.puzzle.SharedPrefsPuzzleProgressStore
import com.chesstutor.app.data.repository.GameRepository
import com.chesstutor.app.data.repository.RoomGameRepository
import com.chesstutor.app.data.repository.RatingRepository
import com.chesstutor.app.data.repository.LearningRepository
import com.chesstutor.app.data.repository.RoomLearningRepository
import com.chesstutor.app.data.repository.ReviewRepository
import com.chesstutor.app.data.repository.RoomRatingRepository
import com.chesstutor.app.data.repository.RoomReviewRepository
import com.chesstutor.app.data.settings.DataStoreSettingsStore
import com.chesstutor.app.data.settings.SettingsStore
import com.chesstutor.app.domain.puzzle.PuzzleRepository
import com.chesstutor.app.engine.EngineService
import com.chesstutor.app.engine.ManagerEngineService
import com.chesstutor.app.engine.ChessEngineManager
import com.chesstutor.app.engine.EngineClient
import com.chesstutor.app.engine.LocalFallbackEngineClient
import com.chesstutor.app.engine.OnlineStockfishEngineClient
import com.chesstutor.app.engine.StockfishProcessEngineClient
import java.io.File

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

    @Volatile
    private var settingsStoreInstance: SettingsStore? = null

    /** Player settings (DataStore). One instance per process, as DataStore requires. */
    fun provideSettingsStore(context: Context): SettingsStore {
        return settingsStoreInstance ?: synchronized(this) {
            settingsStoreInstance ?: DataStoreSettingsStore(context.applicationContext).also {
                settingsStoreInstance = it
            }
        }
    }

    @Volatile
    private var puzzleRepositoryInstance: PuzzleRepository? = null

    /** Puzzle history in Room; copies the old SharedPreferences progress in once (plan 7.4). */
    fun providePuzzleRepository(context: Context): PuzzleRepository {
        return puzzleRepositoryInstance ?: synchronized(this) {
            puzzleRepositoryInstance ?: RoomPuzzleRepository(
                db = AppDatabase.getInstance(context),
                legacy = { SharedPrefsPuzzleProgressStore(context.applicationContext).readLegacy() },
            ).also { puzzleRepositoryInstance = it }
        }
    }

    @Volatile
    private var openingLoaderInstance: OpeningAssetLoader? = null

    /** The opening database, read from its asset on first use and kept for the process. */
    fun provideOpeningLoader(context: Context): OpeningAssetLoader {
        return openingLoaderInstance ?: synchronized(this) {
            openingLoaderInstance ?: OpeningAssetLoader(context.applicationContext.assets).also { openingLoaderInstance = it }
        }
    }

    @Volatile
    private var botRepositoryInstance: BotRepository? = null

    /** The player's own bots ("My bots", Room `bot_profile`). */
    fun provideBotRepository(context: Context): BotRepository {
        return botRepositoryInstance ?: synchronized(this) {
            botRepositoryInstance ?: RoomBotRepository(AppDatabase.getInstance(context).botProfileDao()).also { botRepositoryInstance = it }
        }
    }

    @Volatile
    private var maiaInstance: MaiaAssetLoader? = null

    /** Maia's human-like networks for the bots (plan D26), loaded on first use. */
    fun provideMaia(context: Context): MaiaAssetLoader {
        return maiaInstance ?: synchronized(this) {
            maiaInstance ?: MaiaAssetLoader(context.applicationContext.assets).also { maiaInstance = it }
        }
    }

    /** The engine API for new features (plan section 4.4). */
    fun provideEngineService(context: Context): EngineService =
        ManagerEngineService(provideChessEngineManager(context))

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
        "On-device Stockfish first; online engine second; built-in Lite engine last"
        private set

    fun provideEngineClient(context: Context): EngineClient {
        return engineClientInstance ?: synchronized(this) {
            val deterministicFallback = LocalFallbackEngineClient()
            val cloudFallback = OnlineStockfishEngineClient(fallback = deterministicFallback)
            val nativeLibFile = File(context.applicationContext.applicationInfo.nativeLibraryDir, "libstockfish.so")
            val primaryClient = StockfishProcessEngineClient(
                binaryPath = nativeLibFile.absolutePath,
                fallbackClient = cloudFallback
            )
            engineResolutionDiagnostic =
                "Engine: on-device Stockfish, with online and Lite engine fallbacks"
            runCatching { Log.i("AppContainer", engineResolutionDiagnostic) }
            engineClientInstance = primaryClient
            primaryClient
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
