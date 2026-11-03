package com.chesstutor.app.ui.navigation

import android.os.SystemClock
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.diagnostics.PerfLog
import com.chesstutor.app.design.CtBottomBar
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtMotion
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.di.AppContainer
import com.chesstutor.app.domain.ReviewScheduler
import com.chesstutor.app.feature.account.AccountActions
import com.chesstutor.app.feature.analysis.AnalysisScreen
import com.chesstutor.app.navigation.AppRoutes
import com.chesstutor.app.navigation.StartScreen
import com.chesstutor.app.navigation.TopTab
import com.chesstutor.app.navigation.needsWelcome
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel
import java.time.Instant

/**
 * The app shell (plan section 3): four tabs (Home, Learn, Puzzles, Account), each with its
 * own back stack, built with Navigation Compose.
 */
@Composable
fun AppNavHost(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.appSettings
    CtTheme(theme = settings.appTheme, appearance = settings.appearance, reduceMotion = settings.reduceMotion) {
        when (StartScreen.of(state.profileLoaded)) {
            // A blank screen for the moment it takes to read the saved profile.
            StartScreen.LOADING -> Box(modifier.fillMaxSize().background(CtColors.Bg))
            StartScreen.APP -> AppShell(viewModel, state, modifier)
        }
    }
}

@Composable
private fun AppShell(
    viewModel: AppViewModel,
    state: AppUiState,
    modifier: Modifier,
) {
    // Start-up time: from the process starting to the first frame with the profile read (plan Phase 6D).
    LaunchedEffect(Unit) { PerfLog.startup(SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()) }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val reduceMotion = state.appSettings.reduceMotion
    val dueReviews = remember(state.reviews) {
        val now = Instant.now()
        state.reviews.count { ReviewScheduler.isDue(it, now) }
    }
    // Linking Chess.com or Lichess, shared by the Account tab and the Settings page.
    val linkActions = remember(viewModel) {
        AccountActions(
            onLink = { platform, username -> viewModel.linkRatingAccount(platform, username) },
            onImportMore = { viewModel.refreshLinkedRating() },
            onCancelImport = viewModel::cancelAccountImport,
            onUnlink = viewModel::unlinkRatingAccount,
            onImportDepth = viewModel::setImportDepth,
            onDismissLinkStatus = viewModel::clearLinkingStatus,
        )
    }

    val appOwner = checkNotNull(LocalViewModelStoreOwner.current) { "The shell needs the activity's view models." }
    val shell = Shell(navController, viewModel, state, dueReviews, linkActions, appOwner)
    // Every tab starts loading at launch, in the background, so switching to one is instant.
    shell.homeViewModel()
    shell.learnViewModel()
    shell.trainerViewModel()
    shell.puzzlesViewModel()
    shell.accountViewModel()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CtColors.BgDeep,
        bottomBar = {
            // The first run is a full screen of its own (D46).
            if (AppRoutes.showsBottomBar(currentRoute) && !needsWelcome(state.assessmentState)) {
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
            enterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(260)) + slideInVertically(tween(340, easing = CtMotion.EaseOut)) { it / 16 } },
            exitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(160)) },
            popEnterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(260)) + slideInVertically(tween(340, easing = CtMotion.EaseOut)) { it / 16 } },
            popExitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(160)) }
        ) {
            homeGraph(shell)
            learnGraph(shell)
            puzzlesGraph(shell)
            accountGraph(shell)
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
                    },
                    navArgument(AppRoutes.ANALYSIS_ENGINE_ARG) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(AppRoutes.ANALYSIS_ELO_ARG) {
                        type = NavType.IntType
                        defaultValue = 0
                    },
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
                    onBack = { navController.popBackStack() },
                    engineSide = when (entry.arguments?.getString(AppRoutes.ANALYSIS_ENGINE_ARG)) {
                        "white" -> PieceColor.WHITE
                        "black" -> PieceColor.BLACK
                        else -> null
                    },
                    engineElo = entry.arguments?.getInt(AppRoutes.ANALYSIS_ELO_ARG)?.takeIf { it > 0 },
                )
            }
        }
    }
}

/** What every tab's routes need from the shell. */
internal class Shell(
    val navController: NavHostController,
    val viewModel: AppViewModel,
    val state: AppUiState,
    val dueReviews: Int,
    val linkActions: AccountActions,
    /** The activity: every tab's view model lives here, so all of them load from launch. */
    val appOwner: ViewModelStoreOwner,
)

/** Switches tab, keeping each tab's own back stack (plan section 3). */
internal fun NavHostController.navigateToTab(tab: TopTab) {
    navigate(tab.graphRoute) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
