package com.chesstutor.app.feature.puzzles

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardColors
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtHeroCard
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtScreenTitle
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTile
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.LocalReduceMotion
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.puzzle.PuzzleLevels
import com.chesstutor.app.domain.puzzle.PuzzleWorld

/**
 * The Puzzles tab (plan 6.3.1, Phase 7D, canvas `Puzzles.dc.html`, D43): your rating, the level
 * you are on as one card, the daily puzzle and the mistakes from your games, then the modes
 * and every level. The world map has its own page ([PuzzleLevelsScreen]).
 */
@Composable
fun PuzzlesHomeScreen(
    state: PuzzlesHomeState,
    dueReviewCount: Int,
    onPlayLevel: (Int) -> Unit,
    onOpenModes: () -> Unit,
    onReviewMistakes: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLevels: () -> Unit = {},
    onDailyPuzzle: () -> Unit = {},
    /** The player's board colours, for the level card's picture. */
    boardColors: BoardColors = BoardColors.DEFAULT,
) {
    val current = state.currentLevel
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = CtSpacing.xl)) {
            CtScreenTitle(title = "Puzzles") {
                CtChip(text = "Level $current", icon = Icons.Rounded.Map, onClick = onOpenLevels)
            }
            Text("Rating ${state.stats.rating}", style = CtType.BodySmall, modifier = Modifier.padding(horizontal = CtSpacing.gutter))
            Column(
                modifier = Modifier.padding(horizontal = CtSpacing.gutter, vertical = CtSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
            ) {
                when {
                    state.loading -> Unit
                    !state.packAvailable -> CtEmptyState(
                        icon = Icons.Rounded.Extension,
                        title = "Puzzles are missing",
                        body = "This build has no puzzle pack. Install a build from GitHub Actions.",
                    )
                    else -> {
                        val world = PuzzleWorld.of(current)
                        CtHeroCard(
                            kicker = "${world.title}, world ${world.number}",
                            title = "Level $current",
                            detail = state.levels[current]?.bestScore?.let { "Best $it of ${PuzzleLevels.PUZZLES_PER_LEVEL}" }
                                ?: "Solve ${PuzzleLevels.TO_PASS} of ${PuzzleLevels.PUZZLES_PER_LEVEL} to pass",
                            onClick = { onPlayLevel(current) },
                            onClickLabel = "Play level $current",
                        ) { BoardPattern(boardColors) }
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                            CtTile(modifier = Modifier.weight(1f).fillMaxHeight(), onClick = onDailyPuzzle, onClickLabel = "Open the daily puzzle") {
                                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = CtColors.Accent, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.weight(1f))
                                TileText("Daily puzzle", if (state.stats.solvedToday) "Solved today" else "Waiting for you")
                            }
                            CtTile(modifier = Modifier.weight(1f).fillMaxHeight(), onClick = onReviewMistakes, onClickLabel = "Review the mistakes from your games") {
                                Text("$dueReviewCount", style = CtType.BigNumber)
                                Spacer(Modifier.weight(1f))
                                TileText("Mistakes to fix", if (dueReviewCount > 0) "Due today" else "From your games")
                            }
                        }
                    }
                }
                CtSectionHeader(title = "More")
                Column {
                    CtListRow(title = "Puzzle modes", subtitle = "Rush, by theme, custom", icon = Icons.Rounded.Tune, onClick = onOpenModes)
                    CtListRow(
                        title = "All levels",
                        subtitle = "${state.passed.size} of ${PuzzleLevels.COUNT} passed",
                        icon = Icons.Rounded.Map,
                        onClick = onOpenLevels,
                        divider = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun TileText(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = CtType.TitleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(detail, style = CtType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** An empty board in the player's colours: the level card's picture, without inventing a position. */
@Composable
private fun BoardPattern(colors: BoardColors) {
    val light = Color(colors.light)
    val dark = Color(colors.dark)
    Canvas(Modifier.fillMaxSize()) {
        val cell = size.minDimension / 8f
        for (row in 0 until 8) for (col in 0 until 8) {
            drawRect(if ((row + col) % 2 == 0) light else dark, Offset(col * cell, row * cell), Size(cell, cell))
        }
    }
}

/** Every level of the five worlds, scrolling upward from the first (D43: its own page). */
@Composable
fun PuzzleLevelsScreen(
    state: PuzzlesHomeState,
    onPlayLevel: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize()) {
            CtTopBar(title = "Levels", subtitle = "${state.passed.size} of ${PuzzleLevels.COUNT} passed", onBack = onBack)
            Box(Modifier.weight(1f)) {
                if (!state.loading && state.packAvailable) LevelPath(state, onPlayLevel)
            }
        }
    }
}

@Composable
private fun LevelPath(state: PuzzlesHomeState, onPlayLevel: (Int) -> Unit) {
    val items = PuzzlesHomeModel.pathItems(state.levels)
    val listState = rememberLazyListState()
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(state.currentLevel) {
        // Bring the current level into view (the list is drawn bottom-up).
        val index = items.indexOfFirst { it is PathItem.Level && it.level == state.currentLevel }
        if (index >= 0) {
            val target = (index - 2).coerceAtLeast(0)
            if (reduceMotion) listState.scrollToItem(target) else listState.animateScrollToItem(target)
        }
    }
    LazyColumn(
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(vertical = CtSpacing.lg),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { item -> if (item is PathItem.Level) "L${item.level}" else "W${(item as PathItem.WorldBanner).world.number}" }) { item ->
            when (item) {
                is PathItem.WorldBanner -> WorldBanner(item)
                is PathItem.Level -> LevelNode(item, onPlayLevel)
            }
        }
    }
}

@Composable
private fun WorldBanner(item: PathItem.WorldBanner) {
    val complete = item.passedLevels == PuzzleLevels.PER_WORLD
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CtSpacing.gutter, vertical = CtSpacing.md)
            .clip(CtShapes.Card)
            .background(CtColors.Surface)
            .padding(CtSpacing.lg)
            .semantics(mergeDescendants = true) {},
    ) {
        Text("World ${item.world.number}", style = CtType.Caption)
        Text(item.world.title, style = CtType.Title)
        Text(item.world.tagline, style = CtType.BodySmall)
        Spacer(Modifier.height(CtSpacing.sm))
        Text(
            text = if (complete) "Complete! Badge earned: ${item.world.badge}" else "${item.passedLevels} of ${PuzzleLevels.PER_WORLD} levels passed",
            style = CtType.Label.copy(color = if (complete) CtColors.Accent else CtColors.Text),
        )
    }
}

@Composable
private fun LevelNode(item: PathItem.Level, onPlayLevel: (Int) -> Unit) {
    val playable = item.state != LevelNodeState.LOCKED
    val reduceMotion = LocalReduceMotion.current
    val pulse = if (item.state == LevelNodeState.CURRENT && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "currentLevel")
        transition.animateFloat(1f, 1.06f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse").value
    } else 1f
    val fill = when (item.state) {
        LevelNodeState.PASSED -> CtColors.Accent
        LevelNodeState.CURRENT -> CtColors.Primary
        LevelNodeState.OPEN -> CtColors.Surface
        LevelNodeState.LOCKED -> CtColors.Track
    }
    val (icon, tint) = when (item.state) {
        LevelNodeState.PASSED -> Icons.Rounded.Check to CtColors.OnAccent
        LevelNodeState.CURRENT -> Icons.Rounded.PlayArrow to CtColors.OnPrimary
        LevelNodeState.OPEN -> Icons.Rounded.PlayArrow to CtColors.Accent
        LevelNodeState.LOCKED -> Icons.Rounded.Lock to CtColors.Text2
    }
    val status = when (item.state) {
        LevelNodeState.PASSED -> "passed" + (item.bestScore?.let { ", best $it of ${PuzzleLevels.PUZZLES_PER_LEVEL}" } ?: "")
        LevelNodeState.CURRENT -> "next level"
        LevelNodeState.OPEN -> "open"
        LevelNodeState.LOCKED -> "locked: pass level ${item.level - 1} first"
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = CtSpacing.sm)) {
        val swing = (maxWidth - NODE_SIZE) / 2 * 0.8f
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = swing * PuzzlesHomeModel.sway(item.level)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(NODE_SIZE)
                    .scale(pulse)
                    .clip(CtShapes.Pill)
                    .background(fill)
                    .ctPressable(enabled = playable, role = Role.Button, onClickLabel = "Play level ${item.level}") { onPlayLevel(item.level) }
                    .semantics {
                        contentDescription = "Level ${item.level}"
                        stateDescription = status
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            }
            Column {
                Text("${item.level}", style = CtType.Label)
                item.bestScore?.let { Text("$it/${PuzzleLevels.PUZZLES_PER_LEVEL}", style = CtType.Caption) }
            }
        }
    }
}

private val NODE_SIZE = 60.dp
