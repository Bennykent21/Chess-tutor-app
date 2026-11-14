package com.chesstutor.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.di.AppContainer
import com.chesstutor.app.domain.account.GameLibrary
import com.chesstutor.app.domain.account.GameReviewer
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.domain.train.GameFindings
import com.chesstutor.app.domain.train.PositionKind
import com.chesstutor.app.feature.learn.OpeningTrainerScreen
import com.chesstutor.app.feature.train.FindingsReport
import com.chesstutor.app.feature.train.FromYourGamesScreen
import com.chesstutor.app.feature.train.GameFindingsViewModel
import com.chesstutor.app.feature.train.LineBuilderScreen
import com.chesstutor.app.feature.train.MyLineScreen
import com.chesstutor.app.feature.train.MyLinesScreen
import com.chesstutor.app.feature.train.MyLinesViewModel
import com.chesstutor.app.feature.train.PositionRunScreen
import com.chesstutor.app.feature.train.PositionSetsScreen
import com.chesstutor.app.feature.train.PositionTrainerViewModel
import com.chesstutor.app.feature.train.TrainActions
import com.chesstutor.app.feature.train.TrainScreen
import com.chesstutor.app.navigation.AppRoutes
import com.chesstutor.app.navigation.TopTab
import kotlinx.coroutines.flow.first

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
            val findings by findingsViewModel().state.collectAsStateWithLifecycle()
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
                    onVsStockfish = { navController.navigate(AppRoutes.TRAIN_LINES) { launchSingleTop = true } },
                    onEndgames = { navController.navigate(AppRoutes.trainPositions(endgames = true)) { launchSingleTop = true } },
                    onMiddlegames = { navController.navigate(AppRoutes.trainPositions(endgames = false)) { launchSingleTop = true } },
                    onFromGames = { navController.navigate(AppRoutes.TRAIN_FROM_GAMES) { launchSingleTop = true } },
                ),
                findings = findings.report.findings.size.takeIf { !findings.loading },
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
        composable(AppRoutes.TRAIN_LINES) {
            val model = myLinesViewModel()
            val lines by model.lines.collectAsStateWithLifecycle()
            MyLinesScreen(
                lines = lines,
                theme = state.appSettings.boardTheme,
                onNew = {
                    model.newLine()
                    navController.navigate(AppRoutes.TRAIN_LINE_NEW) { launchSingleTop = true }
                },
                onOpen = { line -> navController.navigate(AppRoutes.trainLine(line.id)) { launchSingleTop = true } },
                onAnyOpening = {
                    navController.navigateToTab(TopTab.HOME)
                    navController.navigate(AppRoutes.BATTLE) { launchSingleTop = true }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = AppRoutes.TRAIN_LINE_PATTERN,
            arguments = listOf(navArgument(AppRoutes.TRAIN_LINE_ARG) { type = NavType.StringType }),
        ) { entry ->
            val model = myLinesViewModel()
            val lines by model.lines.collectAsStateWithLifecycle()
            val line = lines.firstOrNull { it.id == entry.arguments?.getString(AppRoutes.TRAIN_LINE_ARG) }
            if (line == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            val trainer = trainerViewModel()
            val homeModel = homeViewModel()
            MyLineScreen(
                line = line,
                theme = state.appSettings.boardTheme,
                onDrill = {
                    trainer.startMine(line)
                    navController.navigate(AppRoutes.TRAINER_DRILL) { launchSingleTop = true }
                },
                onPlay = {
                    homeModel.presetBattleLine(line.toOpening(), line.side)
                    navController.navigateToTab(TopTab.HOME)
                    navController.navigate(AppRoutes.BATTLE) { launchSingleTop = true }
                },
                onDelete = {
                    model.delete(line.id)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.TRAIN_LINE_NEW) {
            val model = myLinesViewModel()
            val draft by model.draft.collectAsStateWithLifecycle()
            val leave = {
                model.discard()
                navController.popBackStack()
            }
            BackHandler { leave() }
            LineBuilderScreen(
                draft = draft,
                settings = state.appSettings,
                onSide = model::setSide,
                onUndo = model::undo,
                onSave = { name ->
                    model.save(name)?.let { saved ->
                        navController.navigate(AppRoutes.trainLine(saved.id)) {
                            popUpTo(AppRoutes.TRAIN_LINE_NEW) { inclusive = true }
                        }
                    }
                },
                onFeedback = viewModel::playBoardFeedback,
                onBack = { leave() },
            )
        }
        composable(AppRoutes.TRAIN_FROM_GAMES) {
            val model = findingsViewModel()
            val findings by model.state.collectAsStateWithLifecycle()
            val repertoire = repertoireViewModel()
            val repertoires by repertoire.state.collectAsStateWithLifecycle()
            val positions = positionViewModel()
            LaunchedEffect(Unit) { model.refresh() }
            FromYourGamesScreen(
                state = findings,
                theme = state.appSettings.boardTheme,
                onTrainLine = { bookId, path ->
                    // Straight into the line when it can be learnt; else the opening's page says why not.
                    val book = repertoires.book(bookId)
                    if (book != null && !book.locked && book.fresh > 0) {
                        repertoire.startLearn(bookId, path)
                        navController.navigate(AppRoutes.REPERTOIRE_RUN) { launchSingleTop = true }
                    } else {
                        navController.navigate(AppRoutes.repertoire(bookId)) { launchSingleTop = true }
                    }
                },
                onTrainEnding = { setId ->
                    positions.start(setId)
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

/** "From your games" (step 4): findings from the saved games, their reviews and the taught openings (it lives as long as the app). */
@Composable
internal fun Shell.findingsViewModel(): GameFindingsViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel(
        viewModelStoreOwner = appOwner,
        factory = GameFindingsViewModel.Factory {
            val games = GameLibrary.summarise(AppContainer.provideGameRepository(context).observeGames().first(), OpeningTree.EMPTY)
            val analysis = AppContainer.provideGameAnalysis(context)
            val analysed = analysis.observeSummaries().first()
            val reviews = games.filter { it.id in analysed }.associate { game ->
                game.id to GameReviewer.review(game.startFen ?: Position.STARTING_FEN, game.moves, analysis.evals(game.id), game.bookPlies)
            }
            val books = AppContainer.provideRepertoireLoader(context).load()
            FindingsReport(games.size, reviews.size, GameFindings.of(games, books, reviews))
        },
    )
}

/** Your openings (it lives as long as the app, so a line being built survives a look elsewhere). */
@Composable
internal fun Shell.myLinesViewModel(): MyLinesViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel(
        viewModelStoreOwner = appOwner,
        factory = MyLinesViewModel.Factory(AppContainer.provideMyLines(context)) { AppContainer.provideOpeningLoader(context).load() },
    )
}
