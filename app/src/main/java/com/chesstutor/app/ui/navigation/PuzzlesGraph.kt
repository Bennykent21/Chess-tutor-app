package com.chesstutor.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.chesstutor.app.di.AppContainer
import com.chesstutor.app.feature.puzzles.MistakeReviewActions
import com.chesstutor.app.feature.puzzles.MistakeReviewScreen
import com.chesstutor.app.feature.puzzles.MistakeReviewViewModel
import com.chesstutor.app.feature.puzzles.PuzzleLevelsScreen
import com.chesstutor.app.feature.puzzles.PuzzleModeActions
import com.chesstutor.app.feature.puzzles.CustomRunScreen
import com.chesstutor.app.feature.puzzles.PuzzleModesScreen
import com.chesstutor.app.feature.puzzles.PuzzlePlayerScreen
import com.chesstutor.app.feature.puzzles.PuzzlesHomeScreen
import com.chesstutor.app.feature.puzzles.PuzzlesViewModel
import com.chesstutor.app.navigation.AppRoutes
import com.chesstutor.app.navigation.TopTab

/** The Puzzles tab: the hub, levels, modes, mistake review and the puzzle player. */
internal fun NavGraphBuilder.puzzlesGraph(shell: Shell) = with(shell) {
    navigation(startDestination = TopTab.PUZZLES.startRoute, route = TopTab.PUZZLES.graphRoute) {
        composable(TopTab.PUZZLES.startRoute) { entry ->
            val puzzles = puzzlesViewModel()
            val home by puzzles.home.collectAsStateWithLifecycle()
            PuzzlesHomeScreen(
                state = home,
                dueReviewCount = dueReviews,
                onPlayLevel = { level ->
                    puzzles.startLevel(level)
                    navController.navigate(AppRoutes.PUZZLE_PLAYER) { launchSingleTop = true }
                },
                onOpenModes = { navController.navigate(AppRoutes.PUZZLE_MODES) { launchSingleTop = true } },
                onReviewMistakes = { navController.navigate(AppRoutes.MISTAKE_REVIEW) { launchSingleTop = true } },
                onOpenLevels = { navController.navigate(AppRoutes.PUZZLE_LEVELS) { launchSingleTop = true } },
                onDailyPuzzle = { navController.navigate(AppRoutes.PUZZLE_DAILY) { launchSingleTop = true } },
                boardTheme = state.appSettings.boardTheme,
            )
        }
        composable(AppRoutes.PUZZLE_LEVELS) { entry ->
            val puzzles = puzzlesViewModel()
            val levels by puzzles.home.collectAsStateWithLifecycle()
            PuzzleLevelsScreen(
                state = levels,
                onPlayLevel = { level ->
                    puzzles.startLevel(level)
                    navController.navigate(AppRoutes.PUZZLE_PLAYER) { launchSingleTop = true }
                },
                onBack = { navController.popBackStack() },
            )
        }
        // Home's daily challenge card: starts today's puzzle, then shows the player.
        composable(AppRoutes.PUZZLE_DAILY) { entry ->
            val puzzles = puzzlesViewModel()
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
            val puzzles = puzzlesViewModel()
            val themes = AppRoutes.parseThemes(entry.arguments?.getString(AppRoutes.THEMES_ARG))
            LaunchedEffect(Unit) {
                puzzles.startRelated(themes)
                navController.navigate(AppRoutes.PUZZLE_PLAYER) {
                    popUpTo(AppRoutes.PUZZLE_RELATED_PATTERN) { inclusive = true }
                }
            }
        }
        // A course's "Practice set": starts its fixed puzzles, then shows the player.
        composable(
            route = AppRoutes.PUZZLE_PRACTICE_PATTERN,
            arguments = listOf(navArgument(AppRoutes.COURSE_ID_ARG) { type = NavType.StringType })
        ) { entry ->
            val puzzles = puzzlesViewModel()
            val courseId = entry.arguments?.getString(AppRoutes.COURSE_ID_ARG).orEmpty()
            val context = LocalContext.current.applicationContext
            LaunchedEffect(courseId) {
                val course = AppContainer.provideLessonLoader(context).load().course(courseId)
                if (course == null) {
                    navController.popBackStack()
                    return@LaunchedEffect
                }
                puzzles.startPractice(course)
                navController.navigate(AppRoutes.PUZZLE_PLAYER) {
                    popUpTo(AppRoutes.PUZZLE_PRACTICE_PATTERN) { inclusive = true }
                }
            }
        }
        composable(AppRoutes.PUZZLE_MODES) { entry ->
            val puzzles = puzzlesViewModel()
            fun play(start: () -> Unit) {
                start()
                navController.navigate(AppRoutes.PUZZLE_PLAYER) { launchSingleTop = true }
            }
            PuzzleModesScreen(
                patternsFor = puzzles::patternsFor,
                actions = PuzzleModeActions(
                    onRush = { minutes -> play { puzzles.startRush(minutes) } },
                    onThemed = { goal, pattern, difficulty -> play { puzzles.startThemed(goal, pattern, difficulty) } },
                    onOpenCustom = { navController.navigate(AppRoutes.PUZZLE_CUSTOM) { launchSingleTop = true } },
                    onMistakes = { play { puzzles.startMistakes() } }
                ),
                onBack = { navController.popBackStack() },
                pieceSet = state.appSettings.boardTheme.pieceSet,
            )
        }
        composable(AppRoutes.PUZZLE_CUSTOM) { entry ->
            val puzzles = puzzlesViewModel()
            val home by puzzles.home.collectAsStateWithLifecycle()
            CustomRunScreen(
                patternsFor = puzzles::patternsFor,
                puzzleRating = home.stats.rating,
                onStart = { band, pattern, count, timed ->
                    puzzles.startCustom(band, pattern, count, timed)
                    navController.navigate(AppRoutes.PUZZLE_PLAYER) { launchSingleTop = true }
                },
                onBack = { navController.popBackStack() },
                pieceSet = state.appSettings.boardTheme.pieceSet,
            )
        }
        composable(AppRoutes.MISTAKE_REVIEW) {
            val context = LocalContext.current.applicationContext
            // Scoped to this screen: each visit starts from the positions due now.
            val review: MistakeReviewViewModel = viewModel(
                factory = MistakeReviewViewModel.Factory(AppContainer.provideReviewRepository(context), onChanged = viewModel::refreshReviews)
            )
            val ui by review.ui.collectAsStateWithLifecycle()
            MistakeReviewScreen(
                ui = ui,
                session = review,
                settings = state.appSettings,
                actions = MistakeReviewActions(
                    onHint = review::hint,
                    onShowAnswer = review::showAnswer,
                    onNext = review::next,
                    onFeedback = viewModel::playBoardFeedback,
                    onBack = { navController.popBackStack() }
                )
            )
        }
        composable(AppRoutes.PUZZLE_PLAYER) { entry ->
            val puzzles = puzzlesViewModel()
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
}

/** The Puzzles tab's view model, shared by every screen in the tab (it lives as long as the app). */
@Composable
internal fun Shell.puzzlesViewModel(): PuzzlesViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel(
        viewModelStoreOwner = appOwner,
        factory = PuzzlesViewModel.Factory(
            repository = AppContainer.providePuzzleRepository(context),
            loadPack = { AppContainer.loadPuzzlePack(context) },
            practice = AppContainer.provideTraining(context),
        ),
    )
}
