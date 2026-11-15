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
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.di.AppContainer
import com.chesstutor.app.domain.bot.BotOpeningChoices
import com.chesstutor.app.domain.bot.BotRoster
import com.chesstutor.app.domain.bot.CustomBot
import com.chesstutor.app.domain.puzzle.DailyPreview
import com.chesstutor.app.domain.puzzle.PuzzleModes
import com.chesstutor.app.feature.home.BattleSetupScreen
import com.chesstutor.app.feature.home.BotBuilderScreen
import com.chesstutor.app.feature.home.BotSetupScreen
import com.chesstutor.app.feature.home.BotsScreen
import com.chesstutor.app.feature.home.ExploreSetupScreen
import com.chesstutor.app.feature.home.HomeActions
import com.chesstutor.app.feature.home.HomeLearning
import com.chesstutor.app.feature.home.HomeScreen
import com.chesstutor.app.feature.home.HomeViewModel
import com.chesstutor.app.feature.home.OpeningPickerScreen
import com.chesstutor.app.feature.home.OpeningSelection
import com.chesstutor.app.feature.home.PositionSetupScreen
import com.chesstutor.app.feature.home.WatchSetupScreen
import com.chesstutor.app.feature.play.PlayScreen
import com.chesstutor.app.feature.play.SharedPrefsActiveGameStore
import com.chesstutor.app.navigation.AppRoutes
import com.chesstutor.app.navigation.StartingLevel
import com.chesstutor.app.navigation.TopTab
import java.time.LocalDate

/** The Home tab: Home, the bots and their pages, the game setups and the game itself. */
internal fun NavGraphBuilder.homeGraph(shell: Shell) = with(shell) {
    navigation(startDestination = TopTab.HOME.startRoute, route = TopTab.HOME.graphRoute) {
        friendPages(shell)
        composable(TopTab.HOME.startRoute) { entry ->
            val homeModel = homeViewModel()
            val home by homeModel.home.collectAsStateWithLifecycle()
            LaunchedEffect(state.effectiveUserRating) { homeModel.setPlayerRating(state.effectiveUserRating) }
            LaunchedEffect(dueReviews) { homeModel.setDueMistakes(dueReviews) }
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
                    onExplore = { navController.navigate(AppRoutes.EXPLORE) { launchSingleTop = true } },
                    onOpenBots = { navController.navigate(AppRoutes.BOTS) { launchSingleTop = true } },
                    onNewBot = {
                        homeModel.newBot()
                        navController.navigate(AppRoutes.builder()) { launchSingleTop = true }
                    },
                    onContinueLearning = { navController.navigateToTab(TopTab.LEARN) },
                    onDailyPuzzle = {
                        navController.navigateToTab(TopTab.PUZZLES)
                        navController.navigate(AppRoutes.PUZZLE_DAILY) { launchSingleTop = true }
                    },
                    onOpenEngineHealth = { navController.navigate(AppRoutes.ENGINE_HEALTH) },
                    onOpenLesson = { id ->
                        navController.navigateToTab(TopTab.LEARN)
                        navController.navigate(AppRoutes.openLesson(id)) { launchSingleTop = true }
                    },
                    onReviewMistakes = {
                        navController.navigateToTab(TopTab.PUZZLES)
                        navController.navigate(AppRoutes.MISTAKE_REVIEW) { launchSingleTop = true }
                    },
                    onPractiseTheme = { theme ->
                        navController.navigateToTab(TopTab.PUZZLES)
                        navController.navigate(AppRoutes.puzzleRelated(listOf(theme))) { launchSingleTop = true }
                    },
                    onName = viewModel::setPlayerName,
                    onStartingLevel = { level ->
                        viewModel.chooseStartingLevel(level.rating)
                        when (level) {
                            StartingLevel.NEW -> navController.navigateToTab(TopTab.LEARN)
                            StartingLevel.PLAYER -> navController.navigateToTab(TopTab.ACCOUNT)
                            StartingLevel.RULES -> Unit
                        }
                    }
                )
            )
        }
        composable(
            route = AppRoutes.BOT_PATTERN,
            arguments = listOf(navArgument(AppRoutes.BOT_ID_ARG) { type = NavType.StringType })
        ) { entry ->
            val homeModel = homeViewModel()
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
            val homeModel = homeViewModel()
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
            val homeModel = homeViewModel()
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
        composable(AppRoutes.BOTS) { entry ->
            val homeModel = homeViewModel()
            val home by homeModel.home.collectAsStateWithLifecycle()
            BotsScreen(
                roster = home.roster,
                myBots = home.myBots,
                onOpenBot = { id -> navController.navigate(AppRoutes.bot(id)) { launchSingleTop = true } },
                onNewBot = {
                    homeModel.newBot()
                    navController.navigate(AppRoutes.builder()) { launchSingleTop = true }
                },
                onWatch = { navController.navigate(AppRoutes.WATCH) { launchSingleTop = true } },
                onPlayFriend = { navController.navigate(AppRoutes.FRIEND) { launchSingleTop = true } },
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.WATCH) { entry ->
            val homeModel = homeViewModel()
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
            val homeModel = homeViewModel()
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
        composable(AppRoutes.EXPLORE) { entry ->
            val homeModel = homeViewModel()
            val draft by homeModel.exploreDraft.collectAsStateWithLifecycle()
            ExploreSetupScreen(
                draft = draft,
                theme = state.appSettings.boardTheme,
                onChange = homeModel::updateExplore,
                onStart = {
                    val engineSide = draft.side.engineSide?.let { if (it == PieceColor.WHITE) "white" else "black" }
                    navController.navigate(AppRoutes.analysis(draft.board.toFen(), engineSide = engineSide, engineElo = draft.strength))
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoutes.BATTLE) { entry ->
            val homeModel = homeViewModel()
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
            val homeModel = homeViewModel()
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
            val homeModel = homeViewModel()
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
                onReview = { gameId ->
                    // Leave the finished game first, so Home is not left showing it.
                    leave()
                    navController.navigateToTab(TopTab.ACCOUNT)
                    navController.navigate(AppRoutes.review(gameId, analyse = true, fromPlay = true)) { launchSingleTop = true }
                },
                onBack = { leave() }
            )
        }
    }
}

/** The Home tab's view model, shared by Home, the bot pages and the game (it lives as long as the app). */
@Composable
internal fun Shell.homeViewModel(): HomeViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel(
        viewModelStoreOwner = appOwner,
        factory = HomeViewModel.Factory(
            engine = AppContainer.provideEngineService(context),
            openings = { AppContainer.provideOpeningLoader(context).load() },
            games = AppContainer.provideGameRepository(context),
            bots = AppContainer.provideBotRepository(context),
            puzzleStats = AppContainer.providePuzzleRepository(context).stats(),
            store = SharedPrefsActiveGameStore(context),
            maia = AppContainer.provideMaia(context),
            learning = HomeLearning(
                training = trainingSources(context).reports(),
                loadLessons = { AppContainer.provideLessonLoader(context).load() },
                lessonProgress = AppContainer.provideLessonProgress(context).observe(),
                loadDaily = { PuzzleModes.daily(AppContainer.loadPuzzlePack(context), LocalDate.now())?.let(DailyPreview::of) },
                puzzleHistory = AppContainer.providePuzzleRepository(context).history(),
            ),
        ),
    )
}
