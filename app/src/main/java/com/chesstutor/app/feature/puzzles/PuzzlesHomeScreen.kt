package com.chesstutor.app.feature.puzzles

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtCoachBubble
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.LocalReduceMotion
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.Mascot
import com.chesstutor.app.domain.puzzle.PuzzleLevels
import com.chesstutor.app.domain.puzzle.WorldAccent
import java.util.Locale

/**
 * The Puzzles tab (plan section 6.3.1): stats, the coach, the level path through five
 * worlds (scrolling upward), the player's level and the big "Solve puzzles" button.
 */
@Composable
fun PuzzlesHomeScreen(
    state: PuzzlesHomeState,
    mascot: Mascot,
    dueReviewCount: Int,
    onPlayLevel: (Int) -> Unit,
    onOpenModes: () -> Unit,
    onReviewMistakes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = state.currentLevel
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(horizontal = CtSpacing.gutter, vertical = CtSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.sm),
            ) {
                StatsStrip(state)
                CtCoachBubble(
                    mascot = mascot,
                    text = PuzzlesHomeModel.coachLine(state.stats, current, state.stats.solvedToday),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(Modifier.weight(1f)) {
                when {
                    state.loading -> Unit
                    !state.packAvailable -> CtEmptyState(
                        icon = Icons.Rounded.Extension,
                        title = "Puzzles are missing",
                        body = "This build has no puzzle pack. Install a build from GitHub Actions.",
                    )
                    else -> LevelPath(state, onPlayLevel)
                }
            }
            BottomPanel(state, current, dueReviewCount, onPlayLevel, onOpenModes, onReviewMistakes)
        }
    }
}

@Composable
private fun StatsStrip(state: PuzzlesHomeState) {
    Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        StatPill(String.format(Locale.getDefault(), "%,d", state.stats.totalXp), "XP", Icons.Rounded.Star, CtColors.Gold, Modifier.weight(1f))
        StatPill("${state.stats.dayStreak}", "Day streak", Icons.Rounded.LocalFireDepartment, CtColors.Coral, Modifier.weight(1f))
        StatPill("${state.stats.rating}", "Rating", Icons.AutoMirrored.Rounded.TrendingUp, CtColors.Teal, Modifier.weight(1f))
    }
}

/** A compact stat: three must fit across a phone without cutting a number off. */
@Composable
private fun StatPill(value: String, label: String, icon: ImageVector, accent: Color, modifier: Modifier) {
    Row(
        modifier = modifier
            .clip(CtShapes.CardSmall)
            .background(CtColors.Surface)
            .padding(horizontal = CtSpacing.sm, vertical = CtSpacing.sm)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(CtSpacing.xs))
        Column {
            Text(value, style = CtType.TitleSmall, maxLines = 1)
            Text(label, style = CtType.Caption, maxLines = 1)
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

private fun WorldAccent.color(): Color = when (this) {
    WorldAccent.LIME -> CtColors.Lime
    WorldAccent.TEAL -> CtColors.Teal
    WorldAccent.VIOLET -> CtColors.Violet
    WorldAccent.CORAL -> CtColors.Coral
    WorldAccent.GOLD -> CtColors.Gold
}

@Composable
private fun WorldBanner(item: PathItem.WorldBanner) {
    val accent = item.world.accent.color()
    val complete = item.passedLevels == PuzzleLevels.PER_WORLD
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CtSpacing.gutter, vertical = CtSpacing.md)
            .clip(CtShapes.Card)
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.22f), CtColors.Surface)))
            .border(1.dp, accent.copy(alpha = 0.5f), CtShapes.Card)
            .padding(CtSpacing.lg)
            .semantics(mergeDescendants = true) {},
    ) {
        Text("World ${item.world.number}", style = CtType.Caption.copy(color = CtColors.Text2))
        Text(item.world.title, style = CtType.Title)
        Text(item.world.tagline, style = CtType.BodySmall)
        Spacer(Modifier.height(CtSpacing.sm))
        Text(
            text = if (complete) "Complete! Badge earned: ${item.world.badge}" else "${item.passedLevels} of ${PuzzleLevels.PER_WORLD} levels passed",
            style = CtType.Label,
        )
    }
}

@Composable
private fun LevelNode(item: PathItem.Level, onPlayLevel: (Int) -> Unit) {
    val accent = item.world.accent.color()
    val playable = item.state != LevelNodeState.LOCKED
    val reduceMotion = LocalReduceMotion.current
    val pulse = if (item.state == LevelNodeState.CURRENT && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "currentLevel")
        transition.animateFloat(1f, 1.08f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse").value
    } else 1f
    val (fill, icon) = when (item.state) {
        LevelNodeState.PASSED -> accent to Icons.Rounded.Check
        LevelNodeState.CURRENT -> CtColors.Gold to Icons.Rounded.PlayArrow
        LevelNodeState.OPEN -> accent.copy(alpha = 0.6f) to Icons.Rounded.PlayArrow
        LevelNodeState.LOCKED -> CtColors.SurfaceHigh to Icons.Rounded.Lock
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
                    .border(3.dp, if (item.state == LevelNodeState.CURRENT) CtColors.Text else Color.Transparent, CtShapes.Pill)
                    .ctPressable(enabled = playable, role = Role.Button, onClickLabel = "Play level ${item.level}") { onPlayLevel(item.level) }
                    .semantics {
                        contentDescription = "Level ${item.level}"
                        stateDescription = status
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (item.state == LevelNodeState.LOCKED) CtColors.Text3 else CtColors.BgDeep, modifier = Modifier.size(30.dp))
            }
            Column {
                Text("${item.level}", style = CtType.Label)
                item.bestScore?.let { Text("$it/${PuzzleLevels.PUZZLES_PER_LEVEL}", style = CtType.Caption) }
            }
        }
    }
}

private val NODE_SIZE = 64.dp

@Composable
private fun BottomPanel(
    state: PuzzlesHomeState,
    current: Int,
    dueReviewCount: Int,
    onPlayLevel: (Int) -> Unit,
    onOpenModes: () -> Unit,
    onReviewMistakes: () -> Unit,
) {
    val level = state.stats.playerLevel
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CtColors.BgDeep.copy(alpha = 0.85f))
            .padding(horizontal = CtSpacing.gutter, vertical = CtSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CtSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Level ${level.level}", style = CtType.Label, modifier = Modifier.width(72.dp))
            CtProgressBar(progress = level.progress, color = CtColors.Gold, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(CtSpacing.sm))
            Text("${level.xpIntoLevel}/${level.xpForNext} XP", style = CtType.Caption)
        }
        if (dueReviewCount > 0) {
            CtListRow(
                title = "Mistakes review",
                subtitle = "$dueReviewCount position${if (dueReviewCount == 1) "" else "s"} from your games are due",
                icon = Icons.Rounded.History,
                iconTint = CtColors.Coral,
                onClick = onReviewMistakes,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CtShapes.Pill)
                    .background(CtColors.SurfaceRaised)
                    .ctPressable(onClickLabel = "Open puzzle modes", onClick = onOpenModes)
                    .semantics { contentDescription = "Puzzle modes" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Menu, contentDescription = null, tint = CtColors.Text)
            }
            CtButton(
                text = "Solve puzzles",
                onClick = { onPlayLevel(current) },
                enabled = state.packAvailable && !state.loading,
                icon = Icons.Rounded.PlayArrow,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
