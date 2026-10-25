package com.chesstutor.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chesstutor.app.design.CtBottomBar
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.LearnCategory
import com.chesstutor.app.domain.learn.Course
import com.chesstutor.app.domain.learn.LessonCategory
import com.chesstutor.app.domain.ReviewScheduler
import com.chesstutor.app.feature.account.AccountScreen
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.puzzle.PuzzleAssetLoader
import com.chesstutor.app.di.AppContainer
import com.chesstutor.app.feature.account.BoardLabScreen
import com.chesstutor.app.feature.account.BoardSettingsActions
import com.chesstutor.app.feature.account.BoardSettingsScreen
import com.chesstutor.app.feature.account.DesignGalleryScreen
import com.chesstutor.app.feature.account.DeveloperMenuScreen
import com.chesstutor.app.feature.account.EngineHealthScreen
import com.chesstutor.app.domain.bot.CustomBot
import com.chesstutor.app.feature.home.BattleSetupScreen
import com.chesstutor.app.feature.home.BotBuilderScreen
import com.chesstutor.app.feature.home.OpeningPickerScreen
import com.chesstutor.app.feature.home.OpeningSelection
import com.chesstutor.app.domain.bot.BotOpeningChoices
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.feature.home.PositionSetupScreen
import com.chesstutor.app.feature.home.WatchSetupScreen
import com.chesstutor.app.domain.bot.BotRoster
import com.chesstutor.app.feature.home.BotSetupScreen
import com.chesstutor.app.feature.home.HomeActions
import com.chesstutor.app.feature.home.HomeScreen
import com.chesstutor.app.feature.home.HomeViewModel
import com.chesstutor.app.feature.play.PlayScreen
import com.chesstutor.app.feature.play.SharedPrefsActiveGameStore
import com.chesstutor.app.feature.learn.CourseScreen
import com.chesstutor.app.feature.learn.LearnCategoryScreen
import com.chesstutor.app.feature.learn.LearnHubActions
import com.chesstutor.app.feature.learn.LearnScreen
import com.chesstutor.app.feature.learn.LearnViewModel
import com.chesstutor.app.feature.learn.LessonPlayerScreen
import com.chesstutor.app.feature.analysis.AnalysisScreen
import com.chesstutor.app.feature.puzzles.PuzzleModeActions
import com.chesstutor.app.feature.puzzles.PuzzleModesScreen
import com.chesstutor.app.feature.puzzles.PuzzlePlayerScreen
import com.chesstutor.app.feature.puzzles.PuzzlesHomeScreen
import com.chesstutor.app.feature.puzzles.PuzzlesViewModel
import com.chesstutor.app.navigation.AppRoutes
import com.chesstutor.app.navigation.LegacyTab
import com.chesstutor.app.navigation.StartScreen
import com.chesstutor.app.navigation.TopTab
import com.chesstutor.app.navigation.TrainRequest
import com.chesstutor.app.ui.components.PgnViewerDialog
import com.chesstutor.app.ui.components.PromotionDialog
import com.chesstutor.app.ui.onboarding.OnboardingScreen
import com.chesstutor.app.ui.settings.SettingsSheet
import com.chesstutor.app.viewmodel.AppViewModel
import java.time.Instant

/**
 * The app shell (plan section 3): four tabs (Home, Learn, Puzzles, Account), each with its
 * own back stack, built with Navigation Compose. The pre-revamp screens open full screen
 * through [LegacyHost] until each is replaced.
 */
@Composable
fun AppNavHost(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (StartScreen.of(state.profileLoaded, state.assessmentState)) {
        StartScreen.LOADING -> {
            // A blank screen for the moment it takes to read the saved profile.
            Box(modifier.fillMaxSize().background(CtColors.BgDeep))
            return
        }
        StartScreen.ONBOARDING -> {
            OnboardingScreen(state = state, viewModel = viewModel, modifier = modifier)
            return
        }
        StartScreen.APP -> Unit
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val reduceMotion = state.appSettings.reduceMotion
    val dueReviews = remember(state.reviews) {
        val now = Instant.now()
        state.reviews.count { ReviewScheduler.isDue(it, now) }
    }

    fun openLegacy(index: Int, category: LearnCategory? = null) {
        viewModel.selectTab(index)
        navController.navigate(AppRoutes.legacy(category)) { launchSingleTop = true }
    }

    CtTheme(reduceMotion = reduceMotion) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = CtColors.BgDeep,
            bottomBar = {
                if (AppRoutes.showsBottomBar(currentRoute)) {
                    CtBottomBar(
                        selected = AppRoutes.tabFor(currentRoute),
                        onSelect = { tab -> navController.navigateToTab(tab) }
                    )
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TopTab.HOME.graphRoute,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                enterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(220)) },
                exitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(160)) },
                popEnterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(220)) },
                popExitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(160)) }
            ) {
                navigation(startDestination = TopTab.HOME.startRoute, route = TopTab.HOME.graphRoute) {
                    composable(TopTab.HOME.startRoute) { entry ->
                        val homeModel = homeViewModel(navController, entry)
                        val home by homeModel.home.collectAsStateWithLifecycle()
                        LaunchedEffect(state.effectiveUserRating) { homeModel.setPlayerRating(state.effectiveUserRating) }
                        HomeScreen(
                            state = state,
                            home = home,
                            actions = HomeActions(
                                onQuickMatch = {
                                    homeModel.quickMatch()
                                    navController.navigate(AppRoutes.PLAY) { launchSingleTop = true }
                                },
                                onResume = {
                                    homeModel.resume()
                                    navController.navigate(AppRoutes.PLAY) { launchSingleTop = true }
                                },
                                onOpenBot = { id -> navController.navigate(AppRoutes.bot(id)) { launchSingleTop = true } },
                                onOpeningBattle = { navController.navigate(AppRoutes.BATTLE) { launchSingleTop = true } },
                                onFromPosition = { navController.navigate(AppRoutes.POSITION) { launchSingleTop = true } },
                                onWatch = { navController.navigate(AppRoutes.WATCH) { launchSingleTop = true } },
                                onNewBot = {
                                    homeModel.newBot()
                                    navController.navigate(AppRoutes.builder()) { launchSingleTop = true }
                                },
                                onContinueLearning = { navController.navigateToTab(TopTab.LEARN) },
                                onDailyPuzzle = {
                                    navController.navigateToTab(TopTab.PUZZLES)
                                    navController.navigate(AppRoutes.PUZZLE_DAILY) { launchSingleTop = true }
                                },
                                onOpenEngineHealth = { navController.navigate(AppRoutes.ENGINE_HEALTH) }
                            )
                        )
                    }
                    composable(
                        route = AppRoutes.BOT_PATTERN,
                        arguments = listOf(navArgument(AppRoutes.BOT_ID_ARG) { type = NavType.StringType })
                    ) { entry ->
                        val homeModel = homeViewModel(navController, entry)
                        val myBots by homeModel.myBots.collectAsStateWithLifecycle()
                        val botId = entry.arguments?.getString(AppRoutes.BOT_ID_ARG).orEmpty()
                        val bot = remember(botId, myBots) { homeModel.findBot(botId) }
                        if (bot == null) {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                            return@composable
                        }
                        val isMine = bot.id.startsWith(CustomBot.ID_PREFIX)
                        // Keyed on the saved bot, so its page shows its latest rules after an edit.
                        androidx.compose.runtime.key(myBots.firstOrNull { it.id == bot.id }) {
                            BotSetupScreen(
                                bot = bot,
                                initialRules = homeModel.rulesFor(bot.id),
                                onEdit = if (isMine) {
                                    {
                                        homeModel.editBot(bot.id)
                                        navController.navigate(AppRoutes.builder(bot.id)) { launchSingleTop = true }
                                    }
                                } else null,
                                onDelete = if (isMine) {
                                    {
                                        homeModel.deleteBot(bot.id)
                                        navController.popBackStack()
                                    }
                                } else null,
                                onPlay = { side, rules ->
                                    homeModel.start(bot, side, rules)
                                    navController.navigate(AppRoutes.PLAY) {
                                        popUpTo(AppRoutes.BOT_PATTERN) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                    composable(
                        route = AppRoutes.BUILDER_PATTERN,
                        arguments = listOf(navArgument(AppRoutes.BOT_ID_ARG) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        })
                    ) { entry ->
                        val homeModel = homeViewModel(navController, entry)
                        val draft by homeModel.draft.collectAsStateWithLifecycle()
                        BotBuilderScreen(
                            draft = draft,
                            onChange = homeModel::updateDraft,
                            onChooseOpenings = { side ->
                                homeModel.loadOpenings()
                                navController.navigate(AppRoutes.builderOpenings(if (side == PieceColor.WHITE) "white" else "black")) { launchSingleTop = true }
                            },
                            onSave = {
                                val wasNew = draft.isNew
                                val id = homeModel.saveDraft()
                                if (id != null) {
                                    navController.popBackStack()
                                    // A new bot opens its page, ready to play; an edit returns to it.
                                    if (wasNew) navController.navigate(AppRoutes.bot(id)) { launchSingleTop = true }
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = AppRoutes.BUILDER_OPENINGS_PATTERN,
                        arguments = listOf(navArgument(AppRoutes.SIDE_ARG) { type = NavType.StringType })
                    ) { entry ->
                        val homeModel = homeViewModel(navController, entry)
                        val draft by homeModel.draft.collectAsStateWithLifecycle()
                        val tree by homeModel.openingTree.collectAsStateWithLifecycle()
                        LaunchedEffect(Unit) { homeModel.loadOpenings() }
                        val white = entry.arguments?.getString(AppRoutes.SIDE_ARG) == "white"
                        OpeningPickerScreen(
                            tree = tree,
                            onPick = {},
                            onBack = { navController.popBackStack() },
                            title = if (white) "Openings as White" else "Openings as Black",
                            selection = OpeningSelection(
                                selected = if (white) draft.white else draft.black,
                                onChange = { names -> homeModel.updateDraft { if (white) it.copy(white = names) else it.copy(black = names) } },
                                popular = if (white) BotOpeningChoices.white else BotOpeningChoices.black,
                            ),
                        )
                    }
                    composable(AppRoutes.WATCH) { entry ->
                        val homeModel = homeViewModel(navController, entry)
                        val draft by homeModel.watchDraft.collectAsStateWithLifecycle()
                        val myBots by homeModel.myBots.collectAsStateWithLifecycle()
                        WatchSetupScreen(
                            draft = draft,
                            bots = BotRoster.bots + myBots.map { it.asRosterBot() },
                            onChange = homeModel::updateWatch,
                            onStart = {
                                if (homeModel.startWatch()) navController.navigate(AppRoutes.PLAY) { launchSingleTop = true }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(AppRoutes.POSITION) { entry ->
                        val homeModel = homeViewModel(navController, entry)
                        val draft by homeModel.positionDraft.collectAsStateWithLifecycle()
                        val myBots by homeModel.myBots.collectAsStateWithLifecycle()
                        PositionSetupScreen(
                            draft = draft,
                            theme = state.appSettings.boardTheme,
                            opponents = BotRoster.bots + myBots.map { it.asRosterBot() },
                            onChange = homeModel::updatePosition,
                            onStart = {
                                if (homeModel.startFromPosition()) navController.navigate(AppRoutes.PLAY) { launchSingleTop = true }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(AppRoutes.BATTLE) { entry ->
                        val homeModel = homeViewModel(navController, entry)
                        val draft by homeModel.battle.collectAsStateWithLifecycle()
                        BattleSetupScreen(
                            draft = draft,
                            onChange = homeModel::updateBattle,
                            onPickOpening = { navController.navigate(AppRoutes.BATTLE_PICK) { launchSingleTop = true } },
                            onStart = {
                                if (homeModel.startBattle()) navController.navigate(AppRoutes.PLAY) { launchSingleTop = true }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(AppRoutes.BATTLE_PICK) { entry ->
                        val homeModel = homeViewModel(navController, entry)
                        val tree by homeModel.openingTree.collectAsStateWithLifecycle()
                        LaunchedEffect(Unit) { homeModel.loadOpenings() }
                        OpeningPickerScreen(
                            tree = tree,
                            onPick = { line ->
                                homeModel.updateBattle { it.copy(line = line) }
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(AppRoutes.PLAY) { entry ->
                        val homeModel = homeViewModel(navController, entry)
                        val game by homeModel.game.collectAsStateWithLifecycle()
                        val current = game
                        if (current == null) {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                            return@composable
                        }
                        val leave = {
                            homeModel.closeGame()
                            navController.popBackStack()
                        }
                        BackHandler { leave() }
                        LaunchedEffect(state.appSettings.chatLevel) { homeModel.setChatLevel(state.appSettings.chatLevel) }
                        PlayScreen(
                            onChatLevel = viewModel::setChatLevel,
                            game = current,
                            settings = state.appSettings,
                            onFeedback = viewModel::playBoardFeedback,
                            onRematch = homeModel::rematch,
                            onAnalyse = { fen, moves -> navController.navigate(AppRoutes.analysis(fen, moves)) },
                            onBack = { leave() }
                        )
                    }
                }
                navigation(startDestination = TopTab.LEARN.startRoute, route = TopTab.LEARN.graphRoute) {
                    composable(TopTab.LEARN.startRoute) { entry ->
                        val learn = learnViewModel(navController, entry)
                        val lessons by learn.learn.collectAsStateWithLifecycle()
                        LearnScreen(
                            learn = lessons,
                            actions = LearnHubActions(
                                onOpenCategory = { category -> navController.navigate(AppRoutes.learnCategory(category.key)) { launchSingleTop = true } },
                                onOpenLesson = { id ->
                                    learn.openLesson(id)
                                    navController.navigate(AppRoutes.LESSON) { launchSingleTop = true }
                                }
                            )
                        )
                    }
                    composable(
                        route = AppRoutes.LEARN_CATEGORY_PATTERN,
                        arguments = listOf(navArgument(AppRoutes.CATEGORY_ARG) { type = NavType.StringType })
                    ) { entry ->
                        val learn = learnViewModel(navController, entry)
                        val lessons by learn.learn.collectAsStateWithLifecycle()
                        val category = LessonCategory.fromKey(entry.arguments?.getString(AppRoutes.CATEGORY_ARG).orEmpty())
                        if (category == null) {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                            return@composable
                        }
                        LearnCategoryScreen(
                            category = category,
                            learn = lessons,
                            onOpenCourse = { id -> navController.navigate(AppRoutes.course(id)) { launchSingleTop = true } },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = AppRoutes.COURSE_PATTERN,
                        arguments = listOf(navArgument(AppRoutes.COURSE_ID_ARG) { type = NavType.StringType })
                    ) { entry ->
                        val learn = learnViewModel(navController, entry)
                        val homeModel = homeViewModel(navController, entry)
                        val lessons by learn.learn.collectAsStateWithLifecycle()
                        val courseId = entry.arguments?.getString(AppRoutes.COURSE_ID_ARG).orEmpty()
                        val course = lessons.library.course(courseId)
                        CourseScreen(
                            course = course,
                            progress = lessons.progress,
                            onOpenLesson = { id ->
                                learn.openLesson(id)
                                navController.navigate(AppRoutes.LESSON) { launchSingleTop = true }
                            },
                            onBack = { navController.popBackStack() },
                            onPractise = course?.let { navController.practiseInBattle(it, homeModel) },
                            onRelatedPuzzles = course?.let { navController.relatedPuzzles(it) }
                        )
                    }
                    composable(AppRoutes.LESSON) { entry ->
                        val learn = learnViewModel(navController, entry)
                        val homeModel = homeViewModel(navController, entry)
                        val player by learn.player.collectAsStateWithLifecycle()
                        val course = player?.course
                        LessonPlayerScreen(
                            player = player,
                            settings = state.appSettings,
                            onFeedback = viewModel::playBoardFeedback,
                            onNextLesson = { id -> learn.openLesson(id) },
                            onReplay = { player?.lesson?.id?.let { learn.openLesson(it, fromStart = true) } },
                            onBack = {
                                learn.closePlayer()
                                navController.popBackStack()
                            },
                            onPractise = course?.let { navController.practiseInBattle(it, homeModel) },
                            onRelatedPuzzles = course?.let { navController.relatedPuzzles(it) }
                        )
                    }
                }
                navigation(startDestination = TopTab.PUZZLES.startRoute, route = TopTab.PUZZLES.graphRoute) {
                    composable(TopTab.PUZZLES.startRoute) { entry ->
                        val puzzles = puzzlesViewModel(navController, entry)
                        val home by puzzles.home.collectAsStateWithLifecycle()
                        PuzzlesHomeScreen(
                            state = home,
                            mascot = state.appSettings.mascot,
                            dueReviewCount = dueReviews,
                            onPlayLevel = { level ->
                                puzzles.startLevel(level)
                                navController.navigate(AppRoutes.PUZZLE_PLAYER) { launchSingleTop = true }
                            },
                            onOpenModes = { navController.navigate(AppRoutes.PUZZLE_MODES) { launchSingleTop = true } },
                            onReviewMistakes = {
                                viewModel.startTrain(TrainRequest.ReviewDue)
                                navController.navigate(AppRoutes.legacy()) { launchSingleTop = true }
                            }
                        )
                    }
                    // Home's daily challenge card: starts today's puzzle, then shows the player.
                    composable(AppRoutes.PUZZLE_DAILY) { entry ->
                        val puzzles = puzzlesViewModel(navController, entry)
                        LaunchedEffect(Unit) {
                            puzzles.startDaily()
                            navController.navigate(AppRoutes.PUZZLE_PLAYER) {
                                popUpTo(AppRoutes.PUZZLE_DAILY) { inclusive = true }
                            }
                        }
                    }
                    // A lesson's "Related puzzles": starts puzzles with its themes, then shows the player.
                    composable(
                        route = AppRoutes.PUZZLE_RELATED_PATTERN,
                        arguments = listOf(navArgument(AppRoutes.THEMES_ARG) { type = NavType.StringType })
                    ) { entry ->
                        val puzzles = puzzlesViewModel(navController, entry)
                        val themes = AppRoutes.parseThemes(entry.arguments?.getString(AppRoutes.THEMES_ARG))
                        LaunchedEffect(Unit) {
                            puzzles.startRelated(themes)
                            navController.navigate(AppRoutes.PUZZLE_PLAYER) {
                                popUpTo(AppRoutes.PUZZLE_RELATED_PATTERN) { inclusive = true }
                            }
                        }
                    }
                    composable(AppRoutes.PUZZLE_MODES) { entry ->
                        val puzzles = puzzlesViewModel(navController, entry)
                        fun play(start: () -> Unit) {
                            start()
                            navController.navigate(AppRoutes.PUZZLE_PLAYER) { launchSingleTop = true }
                        }
                        PuzzleModesScreen(
                            patternsFor = puzzles::patternsFor,
                            actions = PuzzleModeActions(
                                onRush = { minutes -> play { puzzles.startRush(minutes) } },
                                onDaily = { play { puzzles.startDaily() } },
                                onThemed = { goal, pattern, difficulty -> play { puzzles.startThemed(goal, pattern, difficulty) } },
                                onCustom = { band, pattern, count, timed -> play { puzzles.startCustom(band, pattern, count, timed) } },
                                onMistakes = { play { puzzles.startMistakes() } }
                            ),
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(AppRoutes.PUZZLE_PLAYER) { entry ->
                        val puzzles = puzzlesViewModel(navController, entry)
                        val player by puzzles.player.collectAsStateWithLifecycle()
                        PuzzlePlayerScreen(
                            player = player,
                            settings = state.appSettings,
                            onFeedback = viewModel::playBoardFeedback,
                            onAnalyse = { fen, line -> navController.navigate(AppRoutes.analysis(fen, line)) },
                            onPlayLevel = puzzles::startLevel,
                            onBack = {
                                puzzles.closePlayer()
                                navController.popBackStack()
                            }
                        )
                    }
                }
                navigation(startDestination = TopTab.ACCOUNT.startRoute, route = TopTab.ACCOUNT.graphRoute) {
                    composable(TopTab.ACCOUNT.startRoute) {
                        AccountScreen(
                            state = state,
                            dueReviewCount = dueReviews,
                            onOpenGames = { openLegacy(LegacyTab.REVIEW) },
                            onOpenSettings = { viewModel.setSettingsVisible(true) },
                            onOpenEngineHealth = { navController.navigate(AppRoutes.ENGINE_HEALTH) },
                            onOpenDesignGallery = { navController.navigate(AppRoutes.DESIGN_GALLERY) },
                            onOpenBoardLab = { navController.navigate(AppRoutes.BOARD_LAB) },
                            onOpenBoardSettings = { navController.navigate(AppRoutes.BOARD_SETTINGS) },
                            onOpenDeveloperMenu = { navController.navigate(AppRoutes.DEVELOPER_MENU) }
                        )
                    }
                    composable(AppRoutes.ENGINE_HEALTH) {
                        EngineHealthScreen(
                            status = state.engineStatus,
                            report = state.engineSelfTestReport,
                            isRunning = state.isRunningEngineSelfTest,
                            onRunCheck = viewModel::runEngineSelfTest,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(AppRoutes.DEVELOPER_MENU) {
                        DeveloperMenuScreen(
                            onOpenLegacy = { index -> openLegacy(index) },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(AppRoutes.DESIGN_GALLERY) {
                        DesignGalleryScreen(onBack = { navController.popBackStack() })
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
                                onSound = viewModel::setSoundEnabled,
                                onHaptics = viewModel::setHapticsEnabled,
                                onReduceMotion = viewModel::setReduceMotion
                            ),
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(AppRoutes.BOARD_LAB) {
                        val context = LocalContext.current
                        val engine = remember(context) { AppContainer.provideEngineService(context) }
                        BoardLabScreen(
                            settings = state.appSettings,
                            engine = engine,
                            onFeedback = viewModel::playBoardFeedback,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
                composable(
                    route = AppRoutes.ANALYSIS_PATTERN,
                    arguments = listOf(
                        navArgument(AppRoutes.ANALYSIS_FEN_ARG) {
                            type = NavType.StringType
                            defaultValue = Position.STARTING_FEN
                        },
                        navArgument(AppRoutes.ANALYSIS_LINE_ARG) {
                            type = NavType.StringType
                            defaultValue = ""
                        }
                    )
                ) { entry ->
                    val context = LocalContext.current
                    val engine = remember(context) { AppContainer.provideEngineService(context) }
                    AnalysisScreen(
                        startFen = entry.arguments?.getString(AppRoutes.ANALYSIS_FEN_ARG) ?: Position.STARTING_FEN,
                        line = entry.arguments?.getString(AppRoutes.ANALYSIS_LINE_ARG).orEmpty().split(' ').filter { it.isNotBlank() },
                        settings = state.appSettings,
                        engine = engine,
                        onFeedback = viewModel::playBoardFeedback,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = AppRoutes.LEGACY_PATTERN,
                    arguments = listOf(
                        navArgument(AppRoutes.LEGACY_CATEGORY_ARG) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { entry ->
                    LegacyHost(
                        state = state,
                        viewModel = viewModel,
                        onExit = { navController.popBackStack() }
                    )
                }
            }

            // Overlays shared by every screen. Their Back handlers are registered last, so
            // Back closes them first.
            if (state.selectedGameForPgn != null) {
                BackHandler { viewModel.selectGameForPgn(null) }
                PgnViewerDialog(
                    game = state.selectedGameForPgn!!,
                    onDismiss = { viewModel.selectGameForPgn(null) },
                    onDelete = {
                        viewModel.deleteSavedGame(state.selectedGameForPgn!!.id)
                        viewModel.selectGameForPgn(null)
                    }
                )
            } else if (state.isGameHistorySheetOpen) {
                BackHandler { viewModel.setGameHistorySheetVisible(false) }
            } else if (state.isDrillSheetVisible) {
                BackHandler { viewModel.setDrillSheetVisible(false) }
            } else if (state.isSettingsVisible) {
                BackHandler { viewModel.setSettingsVisible(false) }
                SettingsSheet(
                    state = state,
                    viewModel = viewModel,
                    onDismiss = { viewModel.setSettingsVisible(false) }
                )
            } else if (state.pendingPromotion != null) {
                BackHandler { viewModel.cancelPromotion() }
                PromotionDialog(
                    request = state.pendingPromotion!!,
                    onChoose = viewModel::choosePromotion,
                    onDismiss = viewModel::cancelPromotion
                )
            }
        }
    }
}

/** Switches tab, keeping each tab's own back stack (plan section 3). */
private fun NavHostController.navigateToTab(tab: TopTab) {
    navigate(tab.graphRoute) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** The Home tab's view model, shared by Home, the bot pages and the game (scoped to its graph). */
@Composable
private fun homeViewModel(navController: NavHostController, entry: NavBackStackEntry): HomeViewModel {
    val owner = remember(entry) { navController.getBackStackEntry(TopTab.HOME.graphRoute) }
    val context = LocalContext.current.applicationContext
    return viewModel(
        viewModelStoreOwner = owner,
        factory = HomeViewModel.Factory(
            engine = AppContainer.provideEngineService(context),
            openings = { AppContainer.provideOpeningLoader(context).load() },
            games = AppContainer.provideGameRepository(context),
            bots = AppContainer.provideBotRepository(context),
            puzzleStats = AppContainer.providePuzzleRepository(context).stats(),
            store = SharedPrefsActiveGameStore(context),
            maia = AppContainer.provideMaia(context),
        ),
    )
}

/** "Practise in Opening Battle" for [course]: presets Home's battle page and opens it. Null without a line. */
private fun NavHostController.practiseInBattle(course: Course, home: HomeViewModel): (() -> Unit)? {
    val line = course.battleLine ?: return null
    return {
        home.presetBattle(line, course.side)
        navigateToTab(TopTab.HOME)
        navigate(AppRoutes.BATTLE) { launchSingleTop = true }
    }
}

/** "Related puzzles" for [course]: puzzles with its themes in the Puzzles tab. Null without themes. */
private fun NavHostController.relatedPuzzles(course: Course): (() -> Unit)? {
    if (course.puzzleThemes.isEmpty()) return null
    return {
        navigateToTab(TopTab.PUZZLES)
        navigate(AppRoutes.puzzleRelated(course.puzzleThemes)) { launchSingleTop = true }
    }
}

/** The Learn tab's view model, shared by the hub, its pages and the lesson player (scoped to its graph). */
@Composable
private fun learnViewModel(navController: NavHostController, entry: NavBackStackEntry): LearnViewModel {
    val owner = remember(entry) { navController.getBackStackEntry(TopTab.LEARN.graphRoute) }
    val context = LocalContext.current.applicationContext
    return viewModel(
        viewModelStoreOwner = owner,
        factory = LearnViewModel.Factory(AppContainer.provideLessonProgress(context)) { AppContainer.provideLessonLoader(context).load() },
    )
}

/** The Puzzles tab's view model, shared by every screen in the tab (scoped to its graph). */
@Composable
private fun puzzlesViewModel(navController: NavHostController, entry: NavBackStackEntry): PuzzlesViewModel {
    val owner = remember(entry) { navController.getBackStackEntry(TopTab.PUZZLES.graphRoute) }
    val context = LocalContext.current.applicationContext
    return viewModel(
        viewModelStoreOwner = owner,
        factory = PuzzlesViewModel.Factory(AppContainer.providePuzzleRepository(context)) { PuzzleAssetLoader(context).load() },
    )
}
