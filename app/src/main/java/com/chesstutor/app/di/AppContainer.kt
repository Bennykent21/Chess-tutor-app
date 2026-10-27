package com.chesstutor.app.di

import android.content.Context
import android.util.Log
import com.chesstutor.app.data.account.RoomGameAnalysisRepository
import com.chesstutor.app.data.bot.RoomBotRepository
import com.chesstutor.app.data.learn.LessonAssetLoader
import com.chesstutor.app.data.learn.RoomLessonProgressRepository
import com.chesstutor.app.data.learn.RoomTrainingRepository
import com.chesstutor.app.data.local.AppDatabase
import com.chesstutor.app.domain.learn.LessonProgressRepository
import com.chesstutor.app.data.maia.MaiaAssetLoader
import com.chesstutor.app.domain.bot.BotRepository
import com.chesstutor.app.data.opening.OpeningAssetLoader
import com.chesstutor.app.data.puzzle.PuzzleAssetLoader
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
import com.chesstutor.app.domain.account.GameAnalysisRepository
import com.chesstutor.app.domain.puzzle.PuzzlePack
import com.chesstutor.app.domain.puzzle.PuzzleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.chesstutor.app.engine.EngineService
import com.chesstutor.app.engine.ManagerEngineService
import com.chesstutor.app.engine.ChessEngineManager
import com.chesstutor.app.engine.EngineClient
import com.chesstutor.app.engine.LocalFallbackEngineClient
import com.chesstutor.app.engine.OnlineStockfishEngineClient
import com.chesstutor.app.engine.StockfishProcessEngineClient
import java.io.File

object AppContainer {
    /** Work that lives as long as the app process (following settings). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
    private var puzzlePackInstance: PuzzlePack? = null
    private val puzzlePackLock = Mutex()

    /** The bundled puzzle pack, read once and shared by the Puzzles and Account tabs. */
    suspend fun loadPuzzlePack(context: Context): PuzzlePack = puzzlePackInstance ?: puzzlePackLock.withLock {
        puzzlePackInstance ?: PuzzleAssetLoader(context.applicationContext).load().also { if (!it.isEmpty) puzzlePackInstance = it }
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
    private var lessonLoaderInstance: LessonAssetLoader? = null

    /** The bundled lessons (assets/lessons), read on first use and kept for the process. */
    fun provideLessonLoader(context: Context): LessonAssetLoader {
        return lessonLoaderInstance ?: synchronized(this) {
            lessonLoaderInstance ?: LessonAssetLoader(context.applicationContext.assets).also { lessonLoaderInstance = it }
        }
    }

    @Volatile
    private var lessonProgressInstance: LessonProgressRepository? = null

    @Volatile
    private var gameAnalysisInstance: GameAnalysisRepository? = null

    /** Cached game analysis (Room `analysis_result` and `game_analysis`). */
    fun provideGameAnalysis(context: Context): GameAnalysisRepository {
        return gameAnalysisInstance ?: synchronized(this) {
            gameAnalysisInstance ?: RoomGameAnalysisRepository(AppDatabase.getInstance(context)).also { gameAnalysisInstance = it }
        }
    }

    @Volatile
    private var trainingInstance: RoomTrainingRepository? = null

    /** Practice-set scores and opening trainer progress (Room `course_practice`, `opening_drill`). */
    fun provideTraining(context: Context): RoomTrainingRepository {
        return trainingInstance ?: synchronized(this) {
            trainingInstance ?: RoomTrainingRepository(AppDatabase.getInstance(context)).also { trainingInstance = it }
        }
    }

    /** Progress through the lessons (Room `lesson_progress`). */
    fun provideLessonProgress(context: Context): LessonProgressRepository {
        return lessonProgressInstance ?: synchronized(this) {
            lessonProgressInstance ?: RoomLessonProgressRepository(AppDatabase.getInstance(context)).also { lessonProgressInstance = it }
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
            // Settings > Engine can turn the online engine off; follow it for the life of the process.
            val settings = provideSettingsStore(context).settings
            appScope.launch { settings.collect { cloudFallback.enabled = it.onlineEngine } }
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
