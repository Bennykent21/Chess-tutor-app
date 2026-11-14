package com.chesstutor.app.ui.navigation

import androidx.compose.runtime.getValue
import com.chesstutor.app.feature.train.PositionTrainerViewModel
import com.chesstutor.app.feature.train.PositionSetsScreen
import com.chesstutor.app.feature.train.PositionRunScreen
import com.chesstutor.app.domain.train.PositionKind
import com.chesstutor.app.di.AppContainer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.feature.learn.OpeningTrainerScreen
import com.chesstutor.app.feature.train.TrainActions
import com.chesstutor.app.feature.train.TrainScreen
import com.chesstutor.app.navigation.AppRoutes
import com.chesstutor.app.navigation.TopTab

/**
 * The Train tab (Phase 12): Train home and the openings of one side. The opening page, the runs
 * and the old line drills are still routes of the Learn graph; they open from here unchanged.
 */
internal fun NavGraphBuilder.trainGraph(shell: Shell) = with(shell) {
    navigation(startDestination = TopTab.TRAIN.startRoute, route = TopTab.TRAIN.graphRoute) {
        composable(TopTab.TRAIN.startRoute) {
            val repertoire = repertoireViewModel()
            val repertoires by repertoire.state.collectAsStateWithLifecycle()
            val trainer by trainerViewModel().trainer.collectAsStateWithLifecycle()
            TrainScreen(
                repertoires = repertoires,
                trainer = trainer,
                theme = state.appSettings.boardTheme,
                actions = TrainActions(
                    onToday = {
                        repertoire.startReview()
                        navController.navigate(AppRoutes.REPERTOIRE_RUN) { launchSingleTop = true }
                    },
                    onSide = { side -> navController.navigate(AppRoutes.trainSide(side == PieceColor.WHITE)) { launchSingleTop = true } },
                    onVsStockfish = {
                        navController.navigateToTab(TopTab.HOME)
                        navController.navigate(AppRoutes.BATTLE) { launchSingleTop = true }
                    },
                    onEndgames = { navController.navigate(AppRoutes.trainPositions(endgames = true)) { launchSingleTop = true } },
                    onMiddlegames = { navController.navigate(AppRoutes.trainPositions(endgames = false)) { launchSingleTop = true } },
                ),
            )
        }
        composable(
            route = AppRoutes.TRAIN_SIDE_PATTERN,
            arguments = listOf(navArgument(AppRoutes.TRAIN_SIDE_ARG) { type = NavType.StringType }),
        ) { entry ->
            val side = if (entry.arguments?.getString(AppRoutes.TRAIN_SIDE_ARG) == "black") PieceColor.BLACK else PieceColor.WHITE
            val model = trainerViewModel()
            val trainer by model.trainer.collectAsStateWithLifecycle()
            val repertoire = repertoireViewModel()
            val repertoires by repertoire.state.collectAsStateWithLifecycle()
            OpeningTrainerScreen(
                state = trainer,
                onOpen = { id ->
                    model.start(id)
                    navController.navigate(AppRoutes.TRAINER_DRILL) { launchSingleTop = true }
                },
                onBack = { navController.popBackStack() },
                repertoires = repertoires.books,
                onOpenRepertoire = { id -> navController.navigate(AppRoutes.repertoire(id)) { launchSingleTop = true } },
                onReviewRepertoires = {
                    repertoire.startReview()
                    navController.navigate(AppRoutes.REPERTOIRE_RUN) { launchSingleTop = true }
                },
                fixedSide = side,
                onOpenCourse = { id ->
                    navController.navigateToTab(TopTab.LEARN)
                    navController.navigate(AppRoutes.learnOpening(id)) { launchSingleTop = true }
                },
            )
        }
        composable(
            route = AppRoutes.TRAIN_POSITIONS_PATTERN,
            arguments = listOf(navArgument(AppRoutes.TRAIN_KIND_ARG) { type = NavType.StringType }),
        ) { entry ->
            val kind = if (entry.arguments?.getString(AppRoutes.TRAIN_KIND_ARG) == "middlegames") PositionKind.MIDDLEGAME else PositionKind.ENDGAME
            val model = positionViewModel()
            val positions by model.state.collectAsStateWithLifecycle()
            PositionSetsScreen(
                kind = kind,
                sets = positions.of(kind),
                theme = state.appSettings.boardTheme,
                onOpen = { id ->
                    model.start(id)
                    navController.navigate(AppRoutes.TRAIN_POSITION_RUN) { launchSingleTop = true }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.TRAIN_POSITION_RUN) {
            val model = positionViewModel()
            val run by model.run.collectAsStateWithLifecycle()
            val leave = {
                model.close()
                navController.popBackStack()
            }
            BackHandler { leave() }
            PositionRunScreen(
                run = run,
                settings = state.appSettings,
                onFeedback = viewModel::playBoardFeedback,
                onHint = model::hint,
                onRestart = model::again,
                onGiveUp = model::giveUp,
                onNext = model::next,
                onBack = { leave() },
            )
        }
    }
}

/** Train's endgames and middlegames (step 3), shared by the set list and the run (it lives as long as the app). */
@Composable
internal fun Shell.positionViewModel(): PositionTrainerViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel(
        viewModelStoreOwner = appOwner,
        factory = PositionTrainerViewModel.Factory(
            records = AppContainer.providePositionRecords(context),
            loadSets = { AppContainer.providePositionLoader(context).load() },
            engine = { fen -> AppContainer.provideEngineService(context).evaluate(fen, PositionTrainerViewModel.THINK_MS) },
        ),
    )
}
