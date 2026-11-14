package com.chesstutor.app.ui.navigation

import androidx.compose.runtime.getValue
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
            )
        }
    }
}
