package com.chesstutor.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.chesstutor.app.di.AppContainer
import com.chesstutor.app.feature.friend.FriendPlayActions
import com.chesstutor.app.feature.friend.FriendPlayScreen
import com.chesstutor.app.feature.friend.FriendSetupScreen
import com.chesstutor.app.feature.friend.FriendStart
import com.chesstutor.app.feature.friend.FriendViewModel
import com.chesstutor.app.feature.home.OpeningPickerScreen
import com.chesstutor.app.feature.home.SetupBoardScreen
import com.chesstutor.app.navigation.AppRoutes
import com.chesstutor.app.navigation.TopTab

/** Play a friend (Phase 11), inside the Home tab: the setup page, its two pickers and the game. */
internal fun NavGraphBuilder.friendPages(shell: Shell) = with(shell) {
    composable(AppRoutes.FRIEND) {
        val friendModel = friendViewModel()
        val draft by friendModel.draft.collectAsStateWithLifecycle()
        FriendSetupScreen(
            draft = draft,
            ownerName = state.appSettings.playerName,
            theme = state.appSettings.boardTheme,
            onChange = friendModel::update,
            onPickOpening = { navController.navigate(AppRoutes.FRIEND_OPENING) { launchSingleTop = true } },
            onSetPosition = { navController.navigate(AppRoutes.FRIEND_POSITION) { launchSingleTop = true } },
            onStart = {
                if (friendModel.start(state.appSettings.playerName)) navController.navigate(AppRoutes.FRIEND_PLAY) { launchSingleTop = true }
            },
            onBack = { navController.popBackStack() },
        )
    }
    composable(AppRoutes.FRIEND_OPENING) {
        val friendModel = friendViewModel()
        val tree by friendModel.openingTree.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { friendModel.loadOpenings() }
        OpeningPickerScreen(
            tree = tree,
            onPick = { line ->
                friendModel.update { it.copy(opening = line, start = FriendStart.OPENING) }
                navController.popBackStack()
            },
            onBack = { navController.popBackStack() },
        )
    }
    composable(AppRoutes.FRIEND_POSITION) {
        val friendModel = friendViewModel()
        val draft by friendModel.draft.collectAsStateWithLifecycle()
        SetupBoardScreen(
            title = "Position",
            board = draft.board,
            brush = draft.brush,
            theme = state.appSettings.boardTheme,
            onBoard = { next -> friendModel.update { it.copy(board = next) } },
            onBrush = { brush -> friendModel.update { it.copy(brush = brush) } },
            onBack = { navController.popBackStack() },
            nextLabel = "Use this position",
            startLabel = "Use this position",
            onStart = { navController.popBackStack() },
            options = null,
        )
    }
    composable(AppRoutes.FRIEND_PLAY) {
        val friendModel = friendViewModel()
        val game by friendModel.game.collectAsStateWithLifecycle()
        val current = game
        if (current == null) {
            LaunchedEffect(Unit) { navController.popBackStack() }
            return@composable
        }
        val leave = {
            friendModel.close()
            navController.popBackStack()
        }
        FriendPlayScreen(
            game = current,
            settings = state.appSettings,
            actions = FriendPlayActions(
                onFeedback = viewModel::playBoardFeedback,
                onRematch = friendModel::rematch,
                onReview = { gameId ->
                    // Leave the finished game first, so Home is not left showing it.
                    leave()
                    navController.navigateToTab(TopTab.ACCOUNT)
                    navController.navigate(AppRoutes.review(gameId, analyse = true, fromPlay = true)) { launchSingleTop = true }
                },
                onAnalyse = { fen, moves -> navController.navigate(AppRoutes.analysis(fen, moves)) },
                onBack = { leave() },
            ),
        )
    }
}

/** The friend pages' view model; it lives as long as the app, so a game survives a look at Explore. */
@Composable
internal fun Shell.friendViewModel(): FriendViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel(
        viewModelStoreOwner = appOwner,
        factory = FriendViewModel.Factory(
            openings = { AppContainer.provideOpeningLoader(context).load() },
            games = AppContainer.provideGameRepository(context),
        ),
    )
}
