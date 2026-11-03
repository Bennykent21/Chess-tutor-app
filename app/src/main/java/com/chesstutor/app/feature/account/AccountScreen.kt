package com.chesstutor.app.feature.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.chesstutor.app.data.model.ImportDepth
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtScreenTitle
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtStreak
import com.chesstutor.app.domain.account.GameFilter
import com.chesstutor.app.domain.account.RatingRank
import com.chesstutor.app.domain.account.TimeClass
import com.chesstutor.app.domain.puzzle.PuzzleStats
import com.chesstutor.app.viewmodel.AppUiState

/** What the Account pages can do; the nav host wires each one. */
data class AccountActions(
    val onOpenGame: (gameId: String) -> Unit = {},
    /** Opens the games page, with [GameFilter] already set (from "See all" or from Stats). */
    val onOpenGames: (GameFilter) -> Unit = {},
    /** A game rating opens Stats for its clock (D60). */
    val onOpenStats: (TimeClass) -> Unit = {},
    /** The puzzle rating opens Training (D60). */
    val onOpenTraining: () -> Unit = {},
    /** Opens the spaced review of positions from the player's games (Puzzles > Mistakes review). */
    val onReviewMistakes: () -> Unit = {},
    /** Stats: an opening row opens the Learn course that teaches it (D44). */
    val onOpenCourse: (courseId: String) -> Unit = {},
    /** Training: a theme row plays puzzles of that theme. */
    val onPracticeTheme: (theme: String) -> Unit = {},
    val onLink: (RatingPlatform, String) -> Unit = { _, _ -> },
    /** Settings > Linked account: which rating the app follows, and importing more of the history. */
    val onImportMore: () -> Unit = {},
    /** The Account tab came on screen: a chance for the automatic sync (throttled). */
    val onShown: () -> Unit = {},
    val onCancelImport: () -> Unit = {},
    val onUnlink: () -> Unit = {},
    val onImportDepth: (ImportDepth) -> Unit = {},
    val onDismissLinkStatus: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

/**
 * The Account tab (plan section 6.4, D60): the player's earned piece, each rating with its icon,
 * the level, the latest games and the badges. There are no tabs: a game rating opens Stats for
 * its clock, the puzzle rating opens Training, and "See all" opens every game.
 */
@Composable
fun AccountScreen(
    state: AppUiState,
    games: GamesState,
    stats: PuzzleStats,
    actions: AccountActions,
    modifier: Modifier = Modifier,
    overview: OverviewState = OverviewState(),
) {
    var badgeId by rememberSaveable { mutableStateOf<String?>(null) }
    var ranksOpen by rememberSaveable { mutableStateOf(false) }
    val rank = RatingRank.of(state.effectiveUserRating)
    LaunchedEffect(Unit) { actions.onShown() }

    CtScreenBackground(modifier) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = CtSpacing.xl),
            ) {
                item(key = "top") {
                    CtScreenTitle(title = "Account") {
                        CtStreak(stats.dayStreak)
                        Spacer(Modifier.width(CtSpacing.md))
                        SettingsButton(onClick = actions.onOpenSettings)
                    }
                }
                item(key = "hero") { AccountHero(state, rank, onOpenRanks = { ranksOpen = true }) }
                item(key = "numbers") {
                    Numbers(
                        state = state,
                        stats = stats,
                        onOpenClock = actions.onOpenStats,
                        onOpenPuzzles = actions.onOpenTraining,
                        modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                    )
                }
                if (showsLinkCard(state)) {
                    item(key = "linked") { LinkAccountCard(state = state, actions = actions, modifier = Modifier.padding(start = CtSpacing.gutter, end = CtSpacing.gutter, top = CtSpacing.lg)) }
                }
                item(key = "games-header") {
                    CtSectionHeader(
                        title = "Recent games",
                        actionLabel = if (games.games.isNotEmpty()) "See all" else null,
                        onAction = { actions.onOpenGames(GameFilter()) },
                        modifier = Modifier.padding(start = CtSpacing.gutter, end = CtSpacing.gutter, top = CtSpacing.md),
                    )
                }
                if (games.games.isEmpty()) {
                    item(key = "games-empty") { NoGames(loading = games.loading) }
                } else {
                    items(games.games.take(RECENT_GAMES), key = { "recent-${it.id}" }) { game ->
                        GameListRow(
                            game = game,
                            onClick = { actions.onOpenGame(game.id) },
                            accuracy = games.analysed[game.id]?.accuracyFor(game.userColor),
                            modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                        )
                    }
                }
                if (overview.badges.isNotEmpty()) {
                    item(key = "badges") { BadgesRow(overview.badges, onOpen = { badgeId = it.id }, modifier = Modifier.padding(top = CtSpacing.sm)) }
                }
            }
            BadgeSheet(badge = overview.badges.firstOrNull { it.id == badgeId }, onDismiss = { badgeId = null })
            RankSheet(visible = ranksOpen, rank = rank, onDismiss = { ranksOpen = false })
        }
    }
}

private const val RECENT_GAMES = 3
