package com.chesstutor.app.feature.account

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.model.GameSource
import com.chesstutor.app.data.model.ImportDepth
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.chesstutor.app.design.CtBadge
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtIconBubble
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtStatTile
import com.chesstutor.app.design.CtTextField
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.account.GameFilter
import com.chesstutor.app.domain.account.GameLibrary
import com.chesstutor.app.domain.account.GameOutcome
import com.chesstutor.app.domain.account.GameReview
import com.chesstutor.app.domain.account.TimeClass
import com.chesstutor.app.domain.account.TrainingCalculator
import com.chesstutor.app.domain.account.TrainingReport
import com.chesstutor.app.domain.account.label
import com.chesstutor.app.domain.puzzle.PuzzleStats
import com.chesstutor.app.viewmodel.AppUiState

/** The Account tab's pages (plan section 6.4). */
enum class AccountTab(val label: String) {
    OVERVIEW("Overview"),
    GAMES("Games"),
    PERFORMANCE("Performance"),
    TRAINING("Training"),
}

/** What the Account tab can do; the nav host wires each one. */
data class AccountActions(
    val onOpenGame: (gameId: String) -> Unit = {},
    /** Opens the spaced review of positions from the player's games (Puzzles > Mistakes review). */
    val onReviewMistakes: () -> Unit = {},
    val onLink: (RatingPlatform, String, RatingTimeControl) -> Unit = { _, _, _ -> },
    /** Settings > Linked account: which rating the app follows, and importing more of the history. */
    val onRatingTimeControl: (RatingTimeControl) -> Unit = {},
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
 * Account tab (plan section 6.4): the player's header, then Overview (ratings, linked
 * account, recent games), Games (every saved game with filters) and Performance (results,
 * accuracy and rating trend) and Training (puzzles, themes, lessons, mistakes due).
 */
@Composable
fun AccountScreen(
    state: AppUiState,
    games: GamesState,
    stats: PuzzleStats,
    dueReviewCount: Int,
    actions: AccountActions,
    modifier: Modifier = Modifier,
    initialTab: AccountTab = AccountTab.OVERVIEW,
    /** Reviews of the analysed games, for the Performance page. */
    reviews: Map<String, GameReview> = emptyMap(),
    training: TrainingReport = TrainingCalculator.report(emptyList(), { null }, 0, 0),
) {
    var tabName by rememberSaveable { mutableStateOf(initialTab.name) }
    val tab = AccountTab.valueOf(tabName)
    // The games filter lives here so it survives opening a game and coming back.
    var filterState by rememberSaveable(stateSaver = GameFilterSaver) { mutableStateOf(GameFilter()) }
    LaunchedEffect(Unit) { actions.onShown() }

    CtScreenBackground(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = CtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
        ) {
            item(key = "top") {
                CtTopBar(
                    title = "Account",
                    trailing = {
                        SettingsButton(onClick = actions.onOpenSettings)
                    },
                )
            }
            item(key = "header") { ProfileHeader(state, stats, Modifier.padding(horizontal = CtSpacing.gutter)) }
            item(key = "tabs") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = CtSpacing.gutter),
                    horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
                ) {
                    AccountTab.entries.forEach { entry ->
                        CtChip(text = entry.label, color = CtColors.Gold, selected = entry == tab, onClick = { tabName = entry.name })
                    }
                }
            }
            when (tab) {
                AccountTab.OVERVIEW -> overview(state, games, stats, dueReviewCount, actions, onSeeAllGames = { tabName = AccountTab.GAMES.name })
                AccountTab.GAMES -> gamesList(games, filterState, onFilter = { filterState = it }, onOpenGame = actions.onOpenGame)
                AccountTab.PERFORMANCE -> item(key = "performance") {
                    PerformancePage(
                        games = games,
                        reviews = reviews,
                        onShowGames = { filter ->
                            filterState = filter
                            tabName = AccountTab.GAMES.name
                        },
                        modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                    )
                }
                AccountTab.TRAINING -> item(key = "training") {
                    TrainingPage(
                        stats = stats,
                        training = training,
                        dueReviewCount = dueReviewCount,
                        onReviewMistakes = actions.onReviewMistakes,
                        modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(state: AppUiState, stats: PuzzleStats, modifier: Modifier = Modifier) {
    val linked = state.linkedProfile
    CtCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CtIconBubble(icon = Icons.Rounded.AccountCircle, tint = CtColors.Gold, size = 56.dp)
            Spacer(Modifier.width(CtSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = linked?.let { "@${it.username}" } ?: "Your profile",
                    style = CtType.Title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = headerLine(state, stats), style = CtType.BodySmall)
            }
        }
    }
}

/** "1848 rapid on Chess.com · 5-day streak · Level 11". */
internal fun headerLine(state: AppUiState, stats: PuzzleStats): String {
    val linked = state.linkedProfile
    val rating = linked?.activeRating?.let { "$it ${linked.selectedTimeControl.displayName.lowercase()} on ${linked.platform.displayName}" }
        ?: "${state.effectiveUserRating} estimated"
    val streak = if (stats.dayStreak > 0) " · ${stats.dayStreak}-day streak" else ""
    return "$rating$streak · Level ${stats.playerLevel.level}"
}

// ------------------------------------------------------------------ overview

private fun LazyListScope.overview(
    state: AppUiState,
    games: GamesState,
    stats: PuzzleStats,
    dueReviewCount: Int,
    actions: AccountActions,
    onSeeAllGames: () -> Unit,
) {
    item(key = "tiles") {
        Column(Modifier.padding(horizontal = CtSpacing.gutter), verticalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                CtStatTile(value = "${state.effectiveUserRating}", label = "Rating", icon = Icons.Rounded.EmojiEvents, accent = CtColors.Gold, modifier = Modifier.weight(1f))
                CtStatTile(value = "${stats.rating}", label = "Puzzle rating", icon = Icons.Rounded.Extension, accent = CtColors.Teal, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                CtStatTile(value = "${stats.playerLevel.level}", label = "Level · ${stats.totalXp} XP", icon = Icons.Rounded.Star, accent = CtColors.Violet, modifier = Modifier.weight(1f))
                CtStatTile(value = "${stats.dayStreak}", label = "Day streak", icon = Icons.Rounded.LocalFireDepartment, accent = CtColors.Coral, modifier = Modifier.weight(1f))
            }
            if (dueReviewCount > 0) {
                Text("$dueReviewCount mistake${if (dueReviewCount == 1) "" else "s"} due for review in Puzzles", style = CtType.Caption)
            }
        }
    }
    if (showsLinkCard(state)) {
        item(key = "linked") { LinkAccountCard(state = state, actions = actions, modifier = Modifier.padding(horizontal = CtSpacing.gutter)) }
    }
    item(key = "games-header") {
        CtSectionHeader(
            title = "Recent games",
            actionLabel = if (games.games.isNotEmpty()) "See all" else null,
            onAction = onSeeAllGames,
            modifier = Modifier.padding(horizontal = CtSpacing.gutter),
        )
    }
    if (games.games.isEmpty()) {
        item(key = "games-empty") { NoGames(loading = games.loading) }
    } else {
        item(key = "totals") { TotalsRow(games, Modifier.padding(horizontal = CtSpacing.gutter)) }
        items(games.games.take(3), key = { "recent-${it.id}" }) { game ->
            GameRow(game, onClick = { actions.onOpenGame(game.id) }, modifier = Modifier.padding(horizontal = CtSpacing.gutter), accuracy = games.analysed[game.id]?.accuracyFor(game.userColor))
        }
    }
}

@Composable
private fun TotalsRow(games: GamesState, modifier: Modifier = Modifier) {
    val totals = remember(games.games) { GameLibrary.totals(games.games) }
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text("${totals.games} game${if (totals.games == 1) "" else "s"}", style = CtType.Label, modifier = Modifier.weight(1f))
        CtChip(text = "${totals.wins} won", color = CtColors.Lime)
        CtChip(text = "${totals.draws} drawn", color = CtColors.Text2)
        CtChip(text = "${totals.losses} lost", color = CtColors.Coral)
    }
}

@Composable
private fun NoGames(loading: Boolean) {
    CtEmptyState(
        icon = Icons.Rounded.SportsEsports,
        title = if (loading) "Loading your games" else "No games yet",
        body = if (loading) "Naming the openings…" else "Play a bot from Home, or link Chess.com or Lichess above to bring in your games.",
    )
}

// ------------------------------------------------------------------ games

private fun LazyListScope.gamesList(
    games: GamesState,
    filter: GameFilter,
    onFilter: (GameFilter) -> Unit,
    onOpenGame: (String) -> Unit,
) {
    if (games.games.isEmpty()) {
        item(key = "games-empty") { NoGames(loading = games.loading) }
        return
    }
    val shown = GameLibrary.filter(games.games, filter)
    item(key = "filters") { GameFilters(games, filter, onFilter, Modifier.padding(horizontal = CtSpacing.gutter)) }
    item(key = "count") {
        Row(Modifier.padding(horizontal = CtSpacing.gutter), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (filter.isEmpty) "${shown.size} games" else "${shown.size} of ${games.games.size} games",
                style = CtType.Caption,
                modifier = Modifier.weight(1f),
            )
            if (!filter.isEmpty) CtChip(text = "Clear filters", color = CtColors.Coral, onClick = { onFilter(GameFilter()) })
        }
    }
    if (shown.isEmpty()) {
        item(key = "no-match") {
            CtEmptyState(icon = Icons.Rounded.History, title = "Nothing matches", body = "Try another word or clear the filters.")
        }
    }
    items(shown, key = { it.id }) { game ->
        GameRow(game, onClick = { onOpenGame(game.id) }, modifier = Modifier.padding(horizontal = CtSpacing.gutter), accuracy = games.analysed[game.id]?.accuracyFor(game.userColor))
    }
}

@Composable
private fun GameFilters(games: GamesState, filter: GameFilter, onFilter: (GameFilter) -> Unit, modifier: Modifier = Modifier) {
    val sources = remember(games.games) { GameLibrary.sources(games.games) }
    val timeClasses = remember(games.games) { GameLibrary.timeClasses(games.games) }
    val openings = remember(games.games) { GameLibrary.topOpenings(games.games) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        CtTextField(value = filter.query, onValueChange = { onFilter(filter.copy(query = it)) }, label = "Search", placeholder = "Opponent or opening")
        if (sources.size > 1) {
            ChipRow {
                CtChip(text = "All", selected = filter.source == null, onClick = { onFilter(filter.copy(source = null)) })
                sources.forEach { source ->
                    CtChip(text = source.label, selected = filter.source == source, onClick = { onFilter(filter.copy(source = source.takeUnless { it == filter.source })) })
                }
            }
        }
        ChipRow {
            listOf(GameOutcome.WIN to "Wins", GameOutcome.DRAW to "Draws", GameOutcome.LOSS to "Losses").forEach { (outcome, label) ->
                CtChip(text = label, color = CtColors.Lime, selected = filter.outcome == outcome, onClick = { onFilter(filter.copy(outcome = outcome.takeUnless { it == filter.outcome })) })
            }
            listOf(PieceColor.WHITE to "As White", PieceColor.BLACK to "As Black").forEach { (color, label) ->
                CtChip(text = label, color = CtColors.Gold, selected = filter.color == color, onClick = { onFilter(filter.copy(color = color.takeUnless { it == filter.color })) })
            }
        }
        if (timeClasses.size > 1) {
            ChipRow {
                timeClasses.forEach { time ->
                    CtChip(text = time.label, color = CtColors.Violet, selected = filter.timeClass == time, onClick = { onFilter(filter.copy(timeClass = time.takeUnless { it == filter.timeClass })) })
                }
            }
        }
        if (openings.isNotEmpty()) {
            ChipRow {
                openings.forEach { family ->
                    CtChip(text = family, color = CtColors.Teal, selected = filter.openingFamily == family, onClick = { onFilter(filter.copy(openingFamily = family.takeUnless { it == filter.openingFamily })) })
                }
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
    ) { content() }
}

/** Saves the games filter across navigation and process death as plain strings. */
private val GameFilterSaver = androidx.compose.runtime.saveable.listSaver<GameFilter, String>(
    save = { f ->
        listOf(f.source?.name.orEmpty(), f.outcome?.name.orEmpty(), f.color?.name.orEmpty(), f.timeClass?.name.orEmpty(), f.openingFamily.orEmpty(), f.query)
    },
    restore = { v ->
        GameFilter(
            source = v[0].takeIf { it.isNotEmpty() }?.let(GameSource::valueOf),
            outcome = v[1].takeIf { it.isNotEmpty() }?.let(GameOutcome::valueOf),
            color = v[2].takeIf { it.isNotEmpty() }?.let(PieceColor::valueOf),
            timeClass = v[3].takeIf { it.isNotEmpty() }?.let(TimeClass::valueOf),
            openingFamily = v[4].ifEmpty { null },
            query = v[5],
        )
    },
)

/** The Settings entry: a gear at the top right of Account, as players expect. */
@Composable
private fun SettingsButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(CtSpacing.touchTarget)
            .clip(CtShapes.Pill)
            .background(CtColors.Surface)
            .ctPressable(onClickLabel = "Open settings", onClick = onClick)
            .semantics { contentDescription = "Settings" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Settings, contentDescription = null, tint = CtColors.Text)
    }
}
