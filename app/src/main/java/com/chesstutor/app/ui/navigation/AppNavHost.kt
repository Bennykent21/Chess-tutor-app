package com.chesstutor.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
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
import com.chesstutor.app.domain.ReviewScheduler
import com.chesstutor.app.feature.account.AccountScreen
import com.chesstutor.app.data.puzzle.PuzzleAssetLoader
import com.chesstutor.app.di.AppContainer
import com.chesstutor.app.feature.account.BoardLabScreen
import com.chesstutor.app.feature.account.BoardSettingsActions
import com.chesstutor.app.feature.account.BoardSettingsScreen
import com.chesstutor.app.feature.account.DesignGalleryScreen
import com.chesstutor.app.feature.account.DeveloperMenuScreen
import com.chesstutor.app.feature.account.EngineHealthScreen
import com.chesstutor.app.feature.home.HomeScreen
import com.chesstutor.app.feature.learn.LearnScreen
import com.chesstutor.app.feature.puzzles.PuzzlesHomeScreen
import com.chesstutor.app.feature.puzzles.PuzzlesViewModel
import com.chesstutor.app.navigation.AppRoutes
import com.chesstutor.app.navigation.LegacyTab
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

    if (state.assessmentState != "COMPLETE") {
        OnboardingScreen(state = state, viewModel = viewModel, modifier = modifier)
        return
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
                    composable(TopTab.HOME.startRoute) {
                        HomeScreen(
                            state = state,
                            onQuickMatch = { openLegacy(LegacyTab.ARENA) },
                            onContinueLearning = { navController.navigateToTab(TopTab.LEARN) },
                            onOpenEngineHealth = { navController.navigate(AppRoutes.ENGINE_HEALTH) }
                        )
                    }
                }
                navigation(startDestination = TopTab.LEARN.startRoute, route = TopTab.LEARN.graphRoute) {
                    composable(TopTab.LEARN.startRoute) {
                        LearnScreen(
                            state = state,
                            onOpenCategory = { category -> openLegacy(LegacyTab.CURRICULUM, category) },
                            onOpenDrills = { openLegacy(LegacyTab.SESSION) }
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
                            // The new puzzle player arrives in step 2D; until then levels open the trainer.
                            onPlayLevel = { openLegacy(LegacyTab.PUZZLES) },
                            onOpenModes = { openLegacy(LegacyTab.PUZZLES) },
                            onReviewMistakes = {
                                viewModel.startTrain(TrainRequest.ReviewDue)
                                navController.navigate(AppRoutes.legacy()) { launchSingleTop = true }
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
                        initialCategory = AppRoutes.parseCategory(
                            entry.arguments?.getString(AppRoutes.LEGACY_CATEGORY_ARG)
                        ),
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
