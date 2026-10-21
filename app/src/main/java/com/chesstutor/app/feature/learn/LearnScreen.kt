package com.chesstutor.app.feature.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtComingSoon
import com.chesstutor.app.design.CtIconBubble
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctEnter
import com.chesstutor.app.domain.LearnCategory
import com.chesstutor.app.domain.LearnCurriculumRepository
import com.chesstutor.app.viewmodel.AppUiState

/**
 * Learn hub (plan section 6.2): big buttons, no accordions. Until the new lesson pages
 * arrive in Phase 4, each button opens the existing lesson list on that category.
 */
@Composable
fun LearnScreen(
    state: AppUiState,
    onOpenCategory: (LearnCategory) -> Unit,
    onOpenDrills: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tiles = remember(state.masteredModules, state.practicedModules) {
        LearnHubModel.tiles(LearnCurriculumRepository.topics, state.masteredModules, state.practicedModules)
    }
    val overall = LearnHubModel.overallProgress(tiles)
    val done = tiles.sumOf { it.completedLessons }
    val total = tiles.sumOf { it.totalLessons }

    CtScreenBackground(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            CtTopBar(title = "Learn", subtitle = "Lessons from the first move to the endgame")
            Column(
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
            ) {
                Column(modifier = Modifier.ctEnter(0)) {
                    Text(text = "$done of $total lessons complete", style = CtType.Label)
                    Spacer(Modifier.height(CtSpacing.sm))
                    CtProgressBar(progress = overall, color = CtColors.Teal)
                }

                tiles.chunked(2).forEachIndexed { rowIndex, rowTiles ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
                        modifier = Modifier.ctEnter(rowIndex + 1),
                    ) {
                        rowTiles.forEach { tile ->
                            HubTile(
                                tile = tile,
                                onClick = tile.category?.let { category -> { onOpenCategory(category) } },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                CtSectionHeader(title = "Practice", modifier = Modifier.ctEnter(5))
                CtListRow(
                    title = "Tactics drills",
                    subtitle = "The current drill player, until puzzles replace it",
                    icon = Icons.Rounded.Extension,
                    iconTint = CtColors.Gold,
                    onClick = onOpenDrills,
                    modifier = Modifier.ctEnter(6),
                )
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}

private fun tileStyle(key: String): Pair<ImageVector, Color> = when (key) {
    "basics" -> Icons.Rounded.School to CtColors.Lime
    "openings" -> Icons.Rounded.Flag to CtColors.Gold
    "tactics" -> Icons.Rounded.Bolt to CtColors.Coral
    "middlegame" -> Icons.Rounded.Psychology to CtColors.Violet
    "endgames" -> Icons.Rounded.AutoStories to CtColors.Teal
    else -> Icons.Rounded.Shield to CtColors.Coral
}

@Composable
private fun HubTile(tile: LearnHubTile, onClick: (() -> Unit)?, modifier: Modifier) {
    val (icon, accent) = tileStyle(tile.key)
    CtCard(
        modifier = modifier,
        onClick = if (tile.isAvailable) onClick else null,
        onClickLabel = "Open ${tile.title}",
    ) {
        CtIconBubble(icon = icon, tint = accent, size = 48.dp)
        Spacer(Modifier.height(CtSpacing.md))
        Text(text = tile.title, style = CtType.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = tile.subtitle,
            style = CtType.Caption,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(32.dp),
        )
        Spacer(Modifier.height(CtSpacing.sm))
        val phase = tile.comingInPhase
        if (phase != null) {
            CtComingSoon(phase)
        } else {
            CtProgressBar(progress = tile.progress, color = accent, height = 8.dp)
            Spacer(Modifier.height(CtSpacing.xs))
            Text(
                text = "${tile.completedLessons}/${tile.totalLessons} done",
                style = CtType.Caption,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
