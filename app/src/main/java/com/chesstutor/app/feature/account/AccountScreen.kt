package com.chesstutor.app.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.model.GameSource
import com.chesstutor.app.data.model.ImportDepth
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtScreenTitle
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSegmented
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSheet
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.account.Badge
import com.chesstutor.app.domain.account.GameFilter
import com.chesstutor.app.domain.account.GameLibrary
import com.chesstutor.app.domain.account.GameOutcome
import com.chesstutor.app.domain.account.TimeClass
import com.chesstutor.app.domain.account.TrainingCalculator
import com.chesstutor.app.domain.account.TrainingReport
import com.chesstutor.app.domain.account.label
import com.chesstutor.app.domain.learn.Course
import com.chesstutor.app.domain.learn.OpeningCourses
import com.chesstutor.app.domain.puzzle.PuzzleStats
import com.chesstutor.app.viewmodel.AppUiState
import java.util.Locale

/** The Account tab's pages (plan section 6.4; "Performance" became "Stats" in D44). */
enum class AccountTab(val label: String) {
    OVERVIEW("Overview"),
    GAMES("Games"),
    STATS("Stats"),
    TRAINING("Training"),
}

/** What the Account tab can do; the nav host wires each one. */
data class AccountActions(
    val onOpenGame: (gameId: String) -> Unit = {},
    /** Opens the spaced review of positions from the player's games (Puzzles > Mistakes review). */
    val onReviewMistakes: () -> Unit = {},
    /** Stats: an opening row opens the Learn course that teaches it (D44). */
    val onOpenCourse: (courseId: String) -> Unit = {},
    /** Training: a theme row plays puzzles of that theme. */
    val onPracticeTheme: (theme: String) -> Unit = {},
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
 * Account tab (plan section 6.4, D44): the player, their two ratings and level, then
 * Overview (recent games, badges), Games (every saved game, one filter sheet), Stats (score,
 * openings, colours) and Training (themes to work on, mistakes to review).
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
    training: TrainingReport = TrainingCalculator.report(emptyList(), { null }, 0, 0),
    overview: OverviewState = OverviewState(),
    /** The Learn courses that teach openings, for Stats (D44). */
    openingCourses: List<Course> = emptyList(),
) {
    var tabName by rememberSaveable { mutableStateOf(initialTab.name) }
    val tab = AccountTab.entries.firstOrNull { it.name == tabName } ?: AccountTab.OVERVIEW
    // The games filter lives here so it survives opening a game and coming back.
    var filter by rememberSaveable(stateSaver = GameFilterSaver) { mutableStateOf(GameFilter()) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var badgeId by rememberSaveable { mutableStateOf<String?>(null) }
    val courseIdFor = remember(openingCourses) { { family: String -> OpeningCourses.courseFor(family, openingCourses)?.id } }
    LaunchedEffect(Unit) { actions.onShown() }

    CtScreenBackground(modifier) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = CtSpacing.xl),
            ) {
                item(key = "top") {
                    CtScreenTitle(title = "Account") { SettingsButton(onClick = actions.onOpenSettings) }
                }
                item(key = "profile") { ProfileRow(state, stats, Modifier.padding(horizontal = CtSpacing.gutter, vertical = CtSpacing.md)) }
                item(key = "numbers") { Numbers(state, stats, Modifier.padding(horizontal = CtSpacing.gutter)) }
                if (showsLinkCard(state)) {
                    item(key = "linked") { LinkAccountCard(state = state, actions = actions, modifier = Modifier.padding(start = CtSpacing.gutter, end = CtSpacing.gutter, top = CtSpacing.lg)) }
                }
                item(key = "tabs") {
                    CtSegmented(
                        options = AccountTab.entries,
                        selected = tab,
                        label = { it.label },
                        onSelect = { tabName = it.name },
                        modifier = Modifier.padding(start = CtSpacing.gutter, end = CtSpacing.gutter, top = CtSpacing.lg),
                    )
                }
                val page = Modifier.padding(horizontal = CtSpacing.gutter)
                when (tab) {
                    AccountTab.OVERVIEW -> overview(games, overview.badges, actions.onOpenGame, onSeeAll = { tabName = AccountTab.GAMES.name }, onOpenBadge = { badgeId = it.id })
                    AccountTab.GAMES -> gamesList(games, filter, onOpenFilters = { filtersOpen = true }, onOpenGame = actions.onOpenGame)
                    AccountTab.STATS -> item(key = "stats") {
                        StatsPage(
                            games = games,
                            courseIdFor = courseIdFor,
                            onOpenCourse = actions.onOpenCourse,
                            onShowGames = { shown ->
                                filter = shown
                                tabName = AccountTab.GAMES.name
                            },
                            modifier = page,
                        )
                    }
                    AccountTab.TRAINING -> item(key = "training") {
                        TrainingPage(
                            training = training,
                            dueReviewCount = dueReviewCount,
                            onReviewMistakes = actions.onReviewMistakes,
                            onPracticeTheme = actions.onPracticeTheme,
                            modifier = page,
                        )
                    }
                }
            }
            GameFilterSheet(
                visible = filtersOpen,
                games = games,
                filter = filter,
                onFilter = { filter = it },
                onDismiss = { filtersOpen = false },
            )
            BadgeSheet(badge = overview.badges.firstOrNull { it.id == badgeId }, onDismiss = { badgeId = null })
        }
    }
}

// ------------------------------------------------------------------ header

/** Initial (or a person), "@name" with where the rating comes from, and the day streak. */
@Composable
private fun ProfileRow(state: AppUiState, stats: PuzzleStats, modifier: Modifier = Modifier) {
    val linked = state.linkedProfile
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier.size(52.dp).clip(CtShapes.Pill).background(CtColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            if (linked != null) {
                Text(linked.username.take(1).uppercase(Locale.UK), style = CtType.Headline)
            } else {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = CtColors.Text, modifier = Modifier.size(26.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CtSpacing.xxs)) {
            Text(linked?.let { "@${it.username}" } ?: "You", style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (linked != null) {
                Text("${linked.platform.displayName}, ${linked.selectedTimeControl.displayName.lowercase(Locale.UK)}", style = CtType.Caption)
            }
        }
        if (stats.dayStreak > 0) StreakChip(stats.dayStreak)
    }
}

@Composable
private fun StreakChip(days: Int) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(CtShapes.Pill)
            .background(CtColors.Surface)
            .padding(horizontal = CtSpacing.md)
            .clearAndSetSemantics { contentDescription = "$days-day streak" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = CtColors.Accent, modifier = Modifier.size(16.dp))
        Text("$days", style = CtType.Label)
    }
}

/** Rating and puzzle rating as big numbers, then the level with its XP bar. */
@Composable
private fun Numbers(state: AppUiState, stats: PuzzleStats, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
            NumberTile("${state.effectiveUserRating}", "Rating", Modifier.weight(1f))
            NumberTile("${stats.rating}", "Puzzle rating", Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
        ) {
            Text("Level ${stats.playerLevel.level}", style = CtType.Label)
            CtProgressBar(progress = stats.playerLevel.progress, height = 3.dp, modifier = Modifier.weight(1f))
            Text("${String.format(Locale.UK, "%,d", stats.totalXp)} XP", style = CtType.Caption)
        }
    }
}

@Composable
private fun NumberTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(CtShapes.Card)
            .background(CtColors.Surface)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = CtSpacing.lg, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(CtSpacing.xxs),
    ) {
        Text(value, style = CtType.Display, maxLines = 1)
        Text(label, style = CtType.Caption, maxLines = 1)
    }
}

// ------------------------------------------------------------------ overview

private fun LazyListScope.overview(
    games: GamesState,
    badges: List<Badge>,
    onOpenGame: (String) -> Unit,
    onSeeAll: () -> Unit,
    onOpenBadge: (Badge) -> Unit,
) {
    item(key = "games-header") {
        CtSectionHeader(
            title = "Recent games",
            actionLabel = if (games.games.isNotEmpty()) "See all" else null,
            onAction = onSeeAll,
            modifier = Modifier.padding(start = CtSpacing.gutter, end = CtSpacing.gutter, top = CtSpacing.sm),
        )
    }
    if (games.games.isEmpty()) {
        item(key = "games-empty") { NoGames(loading = games.loading) }
    } else {
        val recent = games.games.take(RECENT_GAMES)
        itemsIndexed(recent, key = { _, game -> "recent-${game.id}" }) { i, game ->
            GameListRow(
                game = game,
                onClick = { onOpenGame(game.id) },
                accuracy = games.analysed[game.id]?.accuracyFor(game.userColor),
                divider = i < recent.lastIndex,
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
            )
        }
    }
    if (badges.isNotEmpty()) {
        item(key = "badges") { BadgesRow(badges, onOpen = onOpenBadge, modifier = Modifier.padding(top = CtSpacing.sm)) }
    }
}

private const val RECENT_GAMES = 3

@Composable
private fun NoGames(loading: Boolean) {
    CtEmptyState(
        icon = Icons.Rounded.SportsEsports,
        title = if (loading) "Loading your games" else "No games yet",
        body = if (loading) "Naming the openings…" else "Play a bot from Home, or link your account.",
    )
}

// ------------------------------------------------------------------ games

private fun LazyListScope.gamesList(
    games: GamesState,
    filter: GameFilter,
    onOpenFilters: () -> Unit,
    onOpenGame: (String) -> Unit,
) {
    if (games.games.isEmpty()) {
        item(key = "games-empty") { NoGames(loading = games.loading) }
        return
    }
    val shown = GameLibrary.filter(games.games, filter)
    item(key = "count") {
        Row(
            modifier = Modifier.padding(start = CtSpacing.gutter, end = CtSpacing.gutter, top = CtSpacing.lg, bottom = CtSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (filter.isEmpty) gamesCount(shown.size) else "${shown.size} of ${gamesCount(games.games.size)}",
                style = CtType.Caption,
                modifier = Modifier.weight(1f),
            )
            FilterButton(active = activeFilters(filter), onClick = onOpenFilters)
        }
    }
    if (shown.isEmpty()) {
        item(key = "no-match") {
            CtEmptyState(icon = Icons.Rounded.History, title = "Nothing matches", body = "Change or clear the filters.")
        }
    }
    itemsIndexed(shown, key = { _, game -> game.id }) { i, game ->
        GameListRow(
            game = game,
            onClick = { onOpenGame(game.id) },
            accuracy = games.analysed[game.id]?.accuracyFor(game.userColor),
            divider = i < shown.lastIndex,
            modifier = Modifier.padding(horizontal = CtSpacing.gutter),
        )
    }
}

private fun gamesCount(n: Int): String = "$n game${if (n == 1) "" else "s"}"

/** How many filters are set; the search counts as one. */
internal fun activeFilters(filter: GameFilter): Int =
    listOf(filter.source, filter.outcome, filter.color, filter.timeClass, filter.openingFamily).count { it != null } +
        if (filter.query.isBlank()) 0 else 1

/** "Filter", or "Filters (2)" on the primary colour while any are set. */
@Composable
private fun FilterButton(active: Int, onClick: () -> Unit) {
    val on = active > 0
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(CtShapes.Pill)
            .background(if (on) CtColors.Primary else CtColors.Surface)
            .ctPressable(onClickLabel = "Filter games", onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
    ) {
        val ink = if (on) CtColors.OnPrimary else CtColors.Text
        Icon(Icons.Rounded.Tune, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        Text(if (on) "Filters ($active)" else "Filter", style = CtType.Label.copy(color = ink))
    }
}

/** Every games filter in one sheet: search, then one row of choices per kind. */
@Composable
private fun GameFilterSheet(visible: Boolean, games: GamesState, filter: GameFilter, onFilter: (GameFilter) -> Unit, onDismiss: () -> Unit) {
    val sources = remember(games.games) { GameLibrary.sources(games.games) }
    val timeClasses = remember(games.games) { GameLibrary.timeClasses(games.games) }
    val openings = remember(games.games) { GameLibrary.topOpenings(games.games) }
    val shown = remember(games.games, filter) { GameLibrary.filter(games.games, filter).size }
    CtSheet(visible = visible, onDismiss = onDismiss) {
        CtSectionHeader(
            title = "Filter games",
            actionLabel = if (filter.isEmpty) null else "Clear",
            onAction = { onFilter(GameFilter()) },
        )
        SearchField(value = filter.query, onValueChange = { onFilter(filter.copy(query = it)) })
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CtSpacing.lg),
        ) {
            if (sources.size > 1) {
                FilterGroup("Source", listOf(null) + sources, filter.source, { it?.label ?: "Any" }) { onFilter(filter.copy(source = it)) }
            }
            FilterGroup("Result", listOf(null, GameOutcome.WIN, GameOutcome.DRAW, GameOutcome.LOSS), filter.outcome, ::outcomeLabel) { onFilter(filter.copy(outcome = it)) }
            FilterGroup("Colour", listOf(null, PieceColor.WHITE, PieceColor.BLACK), filter.color, ::colourLabel) { onFilter(filter.copy(color = it)) }
            if (timeClasses.size > 1) {
                FilterGroup("Time", listOf(null) + timeClasses, filter.timeClass, { it?.label ?: "Any" }) { onFilter(filter.copy(timeClass = it)) }
            }
            if (openings.isNotEmpty()) {
                FilterGroup("Opening", listOf(null) + openings, filter.openingFamily, { it ?: "Any" }) { onFilter(filter.copy(openingFamily = it)) }
            }
        }
        CtButton(text = "Show ${gamesCount(shown)}", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

private fun outcomeLabel(outcome: GameOutcome?): String = when (outcome) {
    null -> "Any"
    GameOutcome.WIN -> "Wins"
    GameOutcome.DRAW -> "Draws"
    GameOutcome.LOSS -> "Losses"
    GameOutcome.UNKNOWN -> "Unfinished"
}

private fun colourLabel(color: PieceColor?): String = when (color) {
    null -> "Both"
    PieceColor.WHITE -> "White"
    PieceColor.BLACK -> "Black"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> FilterGroup(title: String, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        Text(title, style = CtType.Label.copy(color = CtColors.Text2))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
            options.forEach { option ->
                CtChip(text = label(option), selected = option == selected, onClick = { onPick(option) })
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CtSpacing.touchTarget)
            .clip(CtShapes.Control)
            .background(CtColors.Surface)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = CtColors.Text2, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Opponent or opening", style = CtType.Body.copy(color = CtColors.Text2))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = CtType.Body.copy(color = CtColors.Text),
                cursorBrush = SolidColor(CtColors.Text),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search games" },
            )
        }
    }
}

/** Saves the games filter across navigation and process death as plain strings. */
private val GameFilterSaver = listSaver<GameFilter, String>(
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
        Icon(Icons.Rounded.Settings, contentDescription = null, tint = CtColors.Text, modifier = Modifier.size(20.dp))
    }
}
