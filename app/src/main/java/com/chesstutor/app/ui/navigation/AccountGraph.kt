package com.chesstutor.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.chesstutor.app.data.diagnostics.CrashLog
import com.chesstutor.app.data.diagnostics.DataReset
import com.chesstutor.app.data.diagnostics.PerfLog
import com.chesstutor.app.data.local.AppDatabase
import com.chesstutor.app.data.network.RatingApiClient
import com.chesstutor.app.data.settings.PlayerPhoto
import com.chesstutor.app.data.settings.PlayerPicture
import com.chesstutor.app.di.AppContainer
import com.chesstutor.app.domain.PgnFormatter
import com.chesstutor.app.domain.account.GameFilter
import com.chesstutor.app.domain.account.TimeClass
import com.chesstutor.app.domain.learn.OpeningCourses
import com.chesstutor.app.feature.account.AccountScreen
import com.chesstutor.app.feature.account.AccountViewModel
import com.chesstutor.app.feature.account.BoardSettingsActions
import com.chesstutor.app.feature.account.BoardSettingsScreen
import com.chesstutor.app.feature.account.EngineHealthScreen
import com.chesstutor.app.feature.account.GameDetailActions
import com.chesstutor.app.feature.account.GameDetailScreen
import com.chesstutor.app.feature.account.GameReviewScreen
import com.chesstutor.app.feature.account.GamesScreen
import com.chesstutor.app.feature.account.LicencesScreen
import com.chesstutor.app.feature.account.ReviewActions
import com.chesstutor.app.feature.account.SettingsActions
import com.chesstutor.app.feature.account.SettingsScreen
import com.chesstutor.app.feature.account.StatsScreen
import com.chesstutor.app.feature.account.TrainingScreen
import com.chesstutor.app.feature.account.TrainingSources
import com.chesstutor.app.navigation.AppRoutes
import com.chesstutor.app.navigation.TopTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** The Account tab: the overview, games, stats, training, game pages and Settings. */
internal fun NavGraphBuilder.accountGraph(shell: Shell) = with(shell) {
    navigation(startDestination = TopTab.ACCOUNT.startRoute, route = TopTab.ACCOUNT.graphRoute) {
        composable(TopTab.ACCOUNT.startRoute) { entry ->
            val account = accountViewModel()
            val games by account.games.collectAsStateWithLifecycle()
            val stats by account.stats.collectAsStateWithLifecycle()
            val overview by account.overview.collectAsStateWithLifecycle()
            AccountScreen(
                state = state,
                games = games,
                stats = stats,
                overview = overview,
                actions = linkActions.copy(
                    onOpenGame = { id -> navController.navigate(AppRoutes.game(id)) { launchSingleTop = true } },
                    onOpenGames = { filter -> navController.navigate(AppRoutes.games(filter)) { launchSingleTop = true } },
                    onOpenStats = { clock -> navController.navigate(AppRoutes.stats(clock)) { launchSingleTop = true } },
                    onOpenTraining = { navController.navigate(AppRoutes.TRAINING) { launchSingleTop = true } },
                    onReviewMistakes = {
                        navController.navigateToTab(TopTab.PUZZLES)
                        navController.navigate(AppRoutes.MISTAKE_REVIEW) { launchSingleTop = true }
                    },
                    onOpenCourse = { id ->
                        navController.navigateToTab(TopTab.LEARN)
                        navController.navigate(AppRoutes.course(id)) { launchSingleTop = true }
                    },
                    onPracticeTheme = { theme ->
                        navController.navigateToTab(TopTab.PUZZLES)
                        navController.navigate(AppRoutes.puzzleRelated(listOf(theme))) { launchSingleTop = true }
                    },
                    onOpenSettings = { navController.navigate(AppRoutes.SETTINGS) { launchSingleTop = true } },
                    onShown = { viewModel.autoSyncLinkedAccount() }
                )
            )
        }
        composable(
            route = AppRoutes.GAMES_PATTERN,
            arguments = listOf(navArgument(AppRoutes.GAMES_FILTER_ARG) { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            val account = accountViewModel()
            val games by account.games.collectAsStateWithLifecycle()
            GamesScreen(
                games = games,
                initialFilter = GameFilter.decode(entry.arguments?.getString(AppRoutes.GAMES_FILTER_ARG).orEmpty()),
                onOpenGame = { id -> navController.navigate(AppRoutes.game(id)) { launchSingleTop = true } },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = AppRoutes.STATS_PATTERN,
            arguments = listOf(navArgument(AppRoutes.CLOCK_ARG) { type = NavType.StringType }),
        ) { entry ->
            val account = accountViewModel()
            val games by account.games.collectAsStateWithLifecycle()
            val openingCourses by account.openingCourses.collectAsStateWithLifecycle()
            val clock = entry.arguments?.getString(AppRoutes.CLOCK_ARG)?.let { name -> TimeClass.entries.firstOrNull { it.name == name } } ?: TimeClass.RAPID
            StatsScreen(
                games = games,
                state = state,
                initialClock = clock,
                now = remember { System.currentTimeMillis() },
                courseIdFor = { family -> OpeningCourses.courseFor(family, openingCourses)?.id },
                onOpenCourse = { id ->
                    navController.navigateToTab(TopTab.LEARN)
                    navController.navigate(AppRoutes.course(id)) { launchSingleTop = true }
                },
                onShowGames = { filter -> navController.navigate(AppRoutes.games(filter)) { launchSingleTop = true } },
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.TRAINING) { entry ->
            val account = accountViewModel()
            val stats by account.stats.collectAsStateWithLifecycle()
            val training by account.training.collectAsStateWithLifecycle()
            TrainingScreen(
                training = training,
                stats = stats,
                dueReviewCount = dueReviews,
                now = remember { System.currentTimeMillis() },
                onReviewMistakes = {
                    navController.navigateToTab(TopTab.PUZZLES)
                    navController.navigate(AppRoutes.MISTAKE_REVIEW) { launchSingleTop = true }
                },
                onPracticeTheme = { theme ->
                    navController.navigateToTab(TopTab.PUZZLES)
                    navController.navigate(AppRoutes.puzzleRelated(listOf(theme))) { launchSingleTop = true }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.SETTINGS) {
            val context = LocalContext.current.applicationContext
            SettingsScreen(
                state = state,
                accountActions = linkActions,
                actions = SettingsActions(
                    onMascot = viewModel::setMascot,
                    onChatLevel = viewModel::setChatLevel,
                    onOpenBoardSettings = { navController.navigate(AppRoutes.BOARD_SETTINGS) },
                    onOpenEngineHealth = { navController.navigate(AppRoutes.ENGINE_HEALTH) },
                    onOpenLicences = { navController.navigate(AppRoutes.LICENCES) },
                    onReviewDepth = viewModel::setReviewDepth,
                    onName = viewModel::setPlayerName,
                    onPicture = { picture ->
                        if (picture !is PlayerPicture.Photo) PlayerPhoto.delete(context)
                        viewModel.setPlayerPicture(picture)
                    },
                    savePhoto = { image -> PlayerPhoto.save(context, image) },
                    chessComPicture = { username -> RatingApiClient().fetchChessComPicture(username) },
                    onOnlineEngine = viewModel::setOnlineEngine,
                    onAppTheme = viewModel::setAppTheme,
                    onAppearance = viewModel::setAppearance,
                    onSound = viewModel::setSoundEnabled,
                    onHaptics = viewModel::setHapticsEnabled,
                    onReduceMotion = viewModel::setReduceMotion,
                    exportPgn = {
                        AppContainer.provideGameRepository(context).getRecentGames(Int.MAX_VALUE)
                            .joinToString("\n\n") { PgnFormatter.reconstructPgnFromGameRecord(it).trim() }
                    },
                    crashLog = { withContext(Dispatchers.IO) { CrashLog.of(context).read() } },
                    clearCrashLog = { withContext(Dispatchers.IO) { CrashLog.of(context).clear() } },
                    puzzleReports = {
                        AppContainer.providePuzzleRepository(context).savedReports().joinToString("\n") { r ->
                            "${r.puzzleId}\t${r.moveUci}\t${r.fen}\t${r.note}"
                        }
                    },
                    clearPuzzleReports = { AppContainer.providePuzzleRepository(context).clearReports() },
                    eraseEverything = {
                        DataReset.eraseEverything(context, AppDatabase.getInstance(context), AppContainer.provideSettingsStore(context))
                        DataReset.restart(context)
                    },
                    onBack = { navController.popBackStack() }
                )
            )
        }
        composable(
            route = AppRoutes.GAME_PATTERN,
            arguments = listOf(navArgument(AppRoutes.GAME_ID_ARG) { type = NavType.StringType })
        ) { entry ->
            val account = accountViewModel()
            val games by account.games.collectAsStateWithLifecycle()
            val gameId = entry.arguments?.getString(AppRoutes.GAME_ID_ARG).orEmpty()
            val openingCourses by account.openingCourses.collectAsStateWithLifecycle()
            val game = games.game(gameId)
            GameDetailScreen(
                game = game,
                settings = state.appSettings,
                courseId = game?.openingFamily?.let { OpeningCourses.courseFor(it, openingCourses)?.id },
                actions = GameDetailActions(
                    onReview = {
                        account.openReview(gameId)
                        if (games.analysed[gameId] == null) account.analyse(gameId)
                        navController.navigate(AppRoutes.review(gameId)) { launchSingleTop = true }
                    },
                    onDelete = {
                        account.deleteGame(gameId)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() },
                    onOpenCourse = { id ->
                        navController.navigateToTab(TopTab.LEARN)
                        navController.navigate(AppRoutes.course(id)) { launchSingleTop = true }
                    },
                )
            )
        }
        composable(
            route = AppRoutes.REVIEW_PATTERN,
            arguments = listOf(
                navArgument(AppRoutes.GAME_ID_ARG) { type = NavType.StringType },
                navArgument(AppRoutes.ANALYSE_ARG) {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument(AppRoutes.FROM_PLAY_ARG) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            )
        ) { entry ->
            val account = accountViewModel()
            val gameId = entry.arguments?.getString(AppRoutes.GAME_ID_ARG).orEmpty()
            val analyseNow = entry.arguments?.getBoolean(AppRoutes.ANALYSE_ARG) == true
            val fromPlay = entry.arguments?.getBoolean(AppRoutes.FROM_PLAY_ARG) == true
            val leaveReview = {
                navController.popBackStack()
                if (fromPlay) navController.navigateToTab(TopTab.HOME)
            }
            BackHandler(enabled = fromPlay) { leaveReview() }
            LaunchedEffect(gameId) {
                account.openReview(gameId)
                // A finished analysis is not redone, so this only starts new ones.
                if (analyseNow) account.analyse(gameId)
            }
            val review by account.review.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val engine = remember(context) { AppContainer.provideEngineService(context) }
            GameReviewScreen(
                state = review,
                settings = state.appSettings,
                engine = engine,
                actions = ReviewActions(
                    onAnalyse = { account.analyse(gameId) },
                    onAnalyseAgain = { account.analyse(gameId, again = true) },
                    onStop = { account.stopAnalysis(gameId) },
                    onPractise = { move ->
                        review.game?.let { game -> account.practise(game, move, onSaved = viewModel::refreshReviews) }
                    },
                    onFeedback = viewModel::playBoardFeedback,
                    onBack = { leaveReview() },
                    onExplore = { fen, line -> navController.navigate(AppRoutes.analysis(fen, line)) },
                )
            )
        }
        composable(AppRoutes.ENGINE_HEALTH) {
            EngineHealthScreen(
                status = state.engineStatus,
                report = state.engineSelfTestReport,
                isRunning = state.isRunningEngineSelfTest,
                onRunCheck = viewModel::runEngineSelfTest,
                onBack = { navController.popBackStack() },
                perf = PerfLog.snapshot.collectAsStateWithLifecycle().value,
            )
        }
        composable(AppRoutes.LICENCES) {
            LicencesScreen(onBack = { navController.popBackStack() })
        }
        composable(AppRoutes.BOARD_SETTINGS) {
            BoardSettingsScreen(
                settings = state.appSettings,
                actions = BoardSettingsActions(
                    onPieceSet = viewModel::setPieceSet,
                    onBoardColors = viewModel::setBoardColors,
                    onShowCoordinates = viewModel::setShowCoordinates,
                    onShowLegalDots = viewModel::setShowLegalDots,
                    onAutoQueen = viewModel::setAutoQueen,
                    onOrientation = viewModel::setBoardOrientation,
                ),
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/** The Account tab's view model, shared by the tab and its game pages (it lives as long as the app). */
@Composable
internal fun Shell.accountViewModel(): AccountViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel(
        viewModelStoreOwner = appOwner,
        factory = AccountViewModel.Factory(
            repository = AppContainer.provideGameRepository(context),
            loadOpenings = { AppContainer.provideOpeningLoader(context).load() },
            puzzleStats = AppContainer.providePuzzleRepository(context).stats(),
            analysis = AppContainer.provideGameAnalysis(context),
            engine = AppContainer.provideEngineService(context),
            reviews = AppContainer.provideReviewRepository(context),
            trainingSources = trainingSources(context),
            reviewDepth = { AppContainer.provideSettingsStore(context).settings.first().reviewDepth.depth },
        ),
    )
}

/** Where the Training page (and Home's Recommended card) get their numbers. */
internal fun trainingSources(context: android.content.Context) = TrainingSources(
    puzzleHistory = AppContainer.providePuzzleRepository(context).history(),
    loadPack = { AppContainer.loadPuzzlePack(context) },
    lessonProgress = AppContainer.provideLessonProgress(context).observe(),
    loadLessons = { AppContainer.provideLessonLoader(context).load() },
)
