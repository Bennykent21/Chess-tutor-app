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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtScreenTitle
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtGradients
import com.chesstutor.app.design.CtIconBubble
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.learn.LessonCategory

/** Everything the hub can open. */
data class LearnHubActions(
    val onOpenCategory: (LessonCategory) -> Unit,
    val onOpenLesson: (lessonId: String) -> Unit,
    /** The opening trainer (plan 6C). */
    val onOpenTrainer: () -> Unit = {},
)

/**
 * Learn hub (plan section 6.2): a "Continue" card, then big category buttons with progress,
 * no lists or accordions. Each button opens its category page.
 */
@Composable
fun LearnScreen(
    learn: LearnState,
    actions: LearnHubActions,
    modifier: Modifier = Modifier,
    /** Opening trainer lines due again, for its row. */
    trainerDue: Int = 0,
) {
    val tiles = remember(learn) { LearnHubModel.tiles(learn.library, learn.progress) }
    val next = remember(learn) { LearnHubModel.continueLesson(learn.library, learn.progress) }
    val done = tiles.sumOf { it.completedLessons }
    val total = tiles.sumOf { it.totalLessons }

    CtScreenBackground(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            CtScreenTitle(title = "Learn")
            Column(
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
            ) {
                Column(modifier = Modifier) {
                    Text(text = "$done of $total lessons complete", style = CtType.Label)
                    Spacer(Modifier.height(CtSpacing.sm))
                    CtProgressBar(progress = LearnHubModel.overallProgress(tiles), color = CtColors.Teal)
                }

                next?.let { (course, lesson) ->
                    val started = LearnHubModel.status(lesson, learn.progress) == LessonStatus.STARTED
                    CtCard(
                        background = CtGradients.Teal,
                        onClick = { actions.onOpenLesson(lesson.id) },
                        onClickLabel = "Open ${lesson.title}",
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                            Column(Modifier.weight(1f)) {
                                Text(if (started) "Continue" else "Up next", style = CtType.Label.copy(color = CtColors.Gold))
                                Text(lesson.title, style = CtType.Title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(course.title, style = CtType.BodySmall.copy(color = CtColors.Text))
                            }
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = CtColors.Gold)
                        }
                    }
                }

                tiles.chunked(2).forEachIndexed { rowIndex, rowTiles ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
                        modifier = Modifier,
                    ) {
                        rowTiles.forEach { tile ->
                            HubTile(
                                tile = tile,
                                onClick = { actions.onOpenCategory(tile.category) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                CtListRow(
                    title = "Opening trainer",
                    subtitle = if (trainerDue > 0) "$trainerDue line${if (trainerDue == 1) "" else "s"} due for review" else "Learn 15 openings' main lines by heart",
                    icon = Icons.Rounded.Flag,
                    iconTint = CtColors.Gold,
                    onClick = actions.onOpenTrainer,
                    modifier = Modifier,
                )
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}

/** Icon and accent colour of a category, shared by the hub and the category page. */
@Composable
@ReadOnlyComposable
internal fun categoryStyle(category: LessonCategory): Pair<ImageVector, Color> = when (category) {
    LessonCategory.BASICS -> Icons.Rounded.School to CtColors.Lime
    LessonCategory.OPENINGS -> Icons.Rounded.Flag to CtColors.Gold
    LessonCategory.TACTICS -> Icons.Rounded.Bolt to CtColors.Coral
    LessonCategory.MIDDLEGAME -> Icons.Rounded.Psychology to CtColors.Violet
    LessonCategory.ENDGAMES -> Icons.Rounded.AutoStories to CtColors.Teal
    LessonCategory.BLUNDERS -> Icons.Rounded.Shield to CtColors.Coral
}

@Composable
private fun HubTile(tile: LearnHubTile, onClick: () -> Unit, modifier: Modifier) {
    val (icon, accent) = categoryStyle(tile.category)
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
        if (!tile.isAvailable) {
            Text(text = "Lessons on the way", style = CtType.Caption, modifier = Modifier.fillMaxWidth())
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
