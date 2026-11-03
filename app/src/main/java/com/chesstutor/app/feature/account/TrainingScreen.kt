package com.chesstutor.app.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.ctEnter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtArtTile
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtRing
import com.chesstutor.app.design.CtRingPart
import com.chesstutor.app.design.CtScene
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.account.PuzzleRecords
import com.chesstutor.app.domain.account.RatingTrend
import com.chesstutor.app.domain.account.ThemeScore
import com.chesstutor.app.domain.puzzle.PuzzleStats
import com.chesstutor.app.domain.account.TrainingReport

/**
 * Training (D60), opened from the puzzle rating on Account: the puzzle rating graph, the
 * puzzle records, the positions from the player's games due for review, and the puzzle themes
 * that trip them up and that they solve best, each with its share of clean solves (a tap plays it).
 */
@Composable
fun TrainingScreen(
    training: TrainingReport,
    stats: PuzzleStats,
    dueReviewCount: Int,
    now: Long,
    onReviewMistakes: () -> Unit,
    onPracticeTheme: (theme: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trend = remember(training) { RatingTrend(timeClass = null, points = training.ratingHistory) }
    val records = remember(stats, training) { PuzzleRecords.of(stats, training) }
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtTopBar(title = "Training", onBack = onBack)
            Column(
                modifier = Modifier.padding(start = CtSpacing.gutter, end = CtSpacing.gutter, top = CtSpacing.sm, bottom = CtSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
            ) {
                if (trend.points.size >= 2) GraphCard(trend, now, Modifier.ctEnter(0))
                RecordGrid(
                    listOf(
                        Record("${records.current}", "Puzzle rating"),
                        Record("${records.peak}", "Peak"),
                        Record("${records.solved}", "Solved"),
                        Record(records.cleanRate?.let(::percent) ?: "–", "Solved cleanly"),
                        Record("${records.bestCleanRun}", "Longest clean run"),
                        Record("${records.dayStreak}", "Day streak"),
                    ),
                    Modifier.ctEnter(1),
                )
                MistakesTile(dueReviewCount, onReviewMistakes)
                Themes("Themes to work on", training.weaknesses, onPracticeTheme)
                Themes("Your best themes", training.strengths, onPracticeTheme)
                if (training.weaknesses.isEmpty() && training.strengths.isEmpty()) {
                    Text("Solve a few puzzles of each kind to see your themes here.", style = CtType.BodySmall)
                }
            }
        }
    }
}

@Composable
private fun Themes(title: String, themes: List<ThemeScore>, onPracticeTheme: (String) -> Unit) {
    if (themes.isEmpty()) return
    Column {
        CtSectionHeader(title = title)
        themes.forEach { theme -> ThemeRow(theme, onClick = { onPracticeTheme(theme.theme) }) }
    }
}

@Composable
private fun ThemeRow(theme: ThemeScore, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .ctPressable(onClickLabel = "Practise ${theme.name}") { onClick() }
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
    ) {
        CtRing(listOf(CtRingPart(theme.rate, CtColors.Accent)), diameter = 48.dp, stroke = 4.dp) {
            Text(percent(theme.rate), style = CtType.Caption, maxLines = 1)
        }
        Text(theme.name, style = CtType.TitleSmall, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = CtColors.Text2, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun MistakesTile(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    CtArtTile(
        title = "Mistakes to review",
        scene = CtScene.BLUNDERS,
        modifier = modifier.fillMaxWidth().height(132.dp),
        onClick = onClick,
        onClickLabel = "Review your mistakes",
        trailing = { Text("$count", style = CtType.Number.copy(color = CtColors.Accent), maxLines = 1) },
    )
}
