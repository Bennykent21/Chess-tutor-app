package com.chesstutor.app.feature.puzzles

import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import com.chesstutor.app.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyRow
import com.chesstutor.app.board.BoardScenes
import com.chesstutor.app.board.BoardPicture
import com.chesstutor.app.design.ctEnter
import com.chesstutor.app.design.CtTickMark
import com.chesstutor.app.design.CtPictureTile
import com.chesstutor.app.board.BoardTheme
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtHeroCard
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtScreenTitle
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.LocalReduceMotion
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.puzzle.PuzzleLevels
import com.chesstutor.app.domain.puzzle.PuzzleWorld

/**
 * The Puzzles tab (plan 6.3.1, D43, D95): your rating, the level you are on as one card with its
 * world's levels in a strip under it (passed ones are metal medals, tap to replay), then the daily
 * puzzle, the mistakes from your games and the puzzle modes as tiles.
 */
@Composable
fun PuzzlesHomeScreen(
    state: PuzzlesHomeState,
    dueReviewCount: Int,
    onPlayLevel: (Int) -> Unit,
    onOpenModes: () -> Unit,
    onReviewMistakes: () -> Unit,
    modifier: Modifier = Modifier,
    onDailyPuzzle: () -> Unit = {},
    /** The player's board colours, for the level card's picture. */
    boardTheme: BoardTheme = BoardTheme(),
) {
    val current = state.currentLevel
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = CtSpacing.xl)) {
            CtScreenTitle(title = "Puzzles") {
                Text("${state.stats.rating}", style = CtType.Headline, modifier = Modifier.semantics { contentDescription = "Puzzle rating ${state.stats.rating}" })
            }
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
                        ) { BoardPicture(BoardScenes.OPENING, boardTheme) }
                        LevelStrip(state, onPlayLevel)
                        Row(Modifier.height(150.dp).ctEnter(1), horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                            CtPictureTile(
                                title = "Daily puzzle",
                                subtitle = if (state.dailySolved) "Solved today" else "Waiting for you",
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = onDailyPuzzle,
                                onClickLabel = "Open the daily puzzle",
                                trailing = if (state.dailySolved) { { CtTickMark() } } else null,
                            ) { BoardPicture(state.daily?.fen, boardTheme, whiteAtBottom = state.daily?.whiteAtBottom ?: true) }
                            CtPictureTile(
                                title = "Mistakes to fix",
                                subtitle = if (dueReviewCount > 0) "$dueReviewCount due today" else "From your games",
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = onReviewMistakes,
                                onClickLabel = "Review the mistakes from your games",
                            ) { BoardPicture(BoardScenes.ATTACK, boardTheme) }
                        }
                        CtPictureTile(
                            title = "Puzzle modes",
                            subtitle = "Rush, themes, your own runs",
                            modifier = Modifier.fillMaxWidth().height(120.dp).ctEnter(2),
                            onClick = onOpenModes,
                            onClickLabel = "Open the puzzle modes",
                        ) { ModesPicture() }
                    }
                }
            }
        }
    }
}

/**
 * Every level in one sideways strip under the level card (D95, replacing the All levels page):
 * each world starts with its name, passed levels are metal medals (gold for a perfect run,
 * silver for a pass) that replay on a tap, the next level is ringed, and later ones wait.
 */
@Composable
private fun LevelStrip(state: PuzzlesHomeState, onPlayLevel: (Int) -> Unit) {
    val items = remember(state.levels) { PuzzlesHomeModel.pathItems(state.levels) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.currentLevel) {
        val index = items.indexOfFirst { it is PathItem.Level && it.level == state.currentLevel }
        if (index >= 0) listState.scrollToItem((index - 2).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().ctEnter(1),
    ) {
        items(items, key = { item -> if (item is PathItem.Level) "L${item.level}" else "W${(item as PathItem.WorldBanner).world.number}" }) { item ->
            when (item) {
                is PathItem.WorldBanner -> Column(Modifier.padding(start = CtSpacing.sm, end = CtSpacing.xs).width(72.dp).semantics(mergeDescendants = true) {}) {
                    Text("World ${item.world.number}", style = CtType.Caption.copy(color = CtColors.Text2))
                    Text(item.world.title, style = CtType.Label, maxLines = 2)
                }
                is PathItem.Level -> LevelNode(item, onPlayLevel)
            }
        }
    }
}

@Composable
private fun LevelNode(item: PathItem.Level, onPlayLevel: (Int) -> Unit) {
    val playable = item.state != LevelNodeState.LOCKED
    val status = when (item.state) {
        LevelNodeState.PASSED -> "passed" + (item.bestScore?.let { ", best $it of ${PuzzleLevels.PUZZLES_PER_LEVEL}" } ?: "")
        LevelNodeState.CURRENT -> "next level"
        LevelNodeState.OPEN -> "open"
        LevelNodeState.LOCKED -> "locked: pass level ${item.level - 1} first"
    }
    Box(
        modifier = Modifier
            .size(NODE_SIZE)
            .clip(CtShapes.Pill)
            .ctPressable(enabled = playable, role = Role.Button, onClickLabel = "Play level ${item.level}") { onPlayLevel(item.level) }
            .semantics {
                contentDescription = "Level ${item.level}"
                stateDescription = status
            },
        contentAlignment = Alignment.Center,
    ) {
        when (item.state) {
            LevelNodeState.PASSED -> Image(
                painterResource(if (item.bestScore == PuzzleLevels.PUZZLES_PER_LEVEL) R.drawable.medal_puzzle_gold else R.drawable.medal_puzzle_silver),
                contentDescription = null,
                modifier = Modifier.size(NODE_SIZE),
            )
            LevelNodeState.CURRENT -> Box(
                Modifier.size(NODE_SIZE - 6.dp).clip(CtShapes.Pill).background(CtColors.Primary).border(3.dp, CtColors.Accent, CtShapes.Pill),
                contentAlignment = Alignment.Center,
            ) { Text("${item.level}", style = CtType.TitleSmall.copy(color = CtColors.OnPrimary)) }
            LevelNodeState.OPEN -> Box(Modifier.size(NODE_SIZE - 12.dp).clip(CtShapes.Pill).background(CtColors.Surface), contentAlignment = Alignment.Center) {
                Text("${item.level}", style = CtType.Label)
            }
            LevelNodeState.LOCKED -> Box(Modifier.size(NODE_SIZE - 16.dp).clip(CtShapes.Pill).background(CtColors.Track), contentAlignment = Alignment.Center) {
                Text("${item.level}", style = CtType.Caption.copy(color = CtColors.Text2))
            }
        }
    }
}

/** Puzzle modes' tile picture: the rush bolt and the survival heart in metal over a warm glow. */
@Composable
private fun ModesPicture() {
    Box(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(CtColors.Accent.copy(alpha = 0.35f), CtColors.Surface))),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md), modifier = Modifier.padding(bottom = CtSpacing.lg)) {
            Image(painterResource(R.drawable.icon_bolt), contentDescription = null, modifier = Modifier.size(56.dp))
            Image(painterResource(R.drawable.icon_favorite), contentDescription = null, modifier = Modifier.size(48.dp))
            Image(painterResource(R.drawable.icon_tune), contentDescription = null, modifier = Modifier.size(48.dp))
        }
    }
}

private val NODE_SIZE = 52.dp
