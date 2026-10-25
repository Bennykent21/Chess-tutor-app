package com.chesstutor.app.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtStatTile
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.account.ThemeScore
import com.chesstutor.app.domain.account.TrainingReport
import com.chesstutor.app.domain.puzzle.PuzzleStats
import kotlin.math.roundToInt

/**
 * The Training page (plan 6.4): puzzle rating history, level and XP, streaks, the themes
 * the player solves cleanly and the ones that trip them up, lessons done, and the
 * positions from their games that are due for review.
 */
@Composable
fun TrainingPage(
    stats: PuzzleStats,
    training: TrainingReport,
    dueReviewCount: Int,
    onReviewMistakes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
            CtStatTile(value = "${stats.rating}", label = "Puzzle rating", icon = Icons.Rounded.Extension, accent = CtColors.Teal, modifier = Modifier.weight(1f))
            CtStatTile(value = "${stats.solved}", label = "Puzzles solved", icon = Icons.Rounded.CheckCircle, accent = CtColors.Lime, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
            CtStatTile(value = "${stats.dayStreak}", label = "Day streak", icon = Icons.Rounded.LocalFireDepartment, accent = CtColors.Coral, modifier = Modifier.weight(1f))
            CtStatTile(value = "${stats.bestStreak}", label = "Best clean run", icon = Icons.Rounded.Star, accent = CtColors.Gold, modifier = Modifier.weight(1f))
        }

        CtCard(modifier = Modifier.fillMaxWidth()) {
            Text("Level ${stats.playerLevel.level}", style = CtType.Title)
            Text("${stats.totalXp} XP from puzzles and lessons · ${stats.playerLevel.xpForNext - stats.playerLevel.xpIntoLevel} XP to the next level", style = CtType.BodySmall)
            Spacer(Modifier.height(CtSpacing.sm))
            CtProgressBar(progress = stats.playerLevel.progress, color = CtColors.Violet, height = 8.dp)
        }

        CtCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mistakes review", style = CtType.TitleSmall)
                    Text(
                        if (dueReviewCount == 0) "Nothing due. Positions from your games come back here when it is time." else "$dueReviewCount position${if (dueReviewCount == 1) "" else "s"} from your games due now",
                        style = CtType.BodySmall,
                    )
                }
            }
            if (dueReviewCount > 0) {
                Spacer(Modifier.height(CtSpacing.sm))
                CtButton(text = "Review now", icon = Icons.Rounded.History, onClick = onReviewMistakes, modifier = Modifier.fillMaxWidth())
            }
        }

        CtSectionHeader(title = "Puzzle rating")
        if (training.ratingHistory.size < 2) {
            Text("Solve a few puzzles to see your rating over time.", style = CtType.BodySmall)
        } else {
            CtCard(modifier = Modifier.fillMaxWidth()) {
                val first = training.ratingHistory.first().rating
                val last = training.ratingHistory.last().rating
                Text("${if (last >= first) "+" else ""}${last - first} over ${training.puzzlesPlayed} puzzles", style = CtType.Label)
                Text("${percent(training.cleanRate)} solved cleanly (no mistakes or hints)", style = CtType.Caption)
                Spacer(Modifier.height(CtSpacing.sm))
                RatingChart(training.ratingHistory, if (last >= first) CtColors.Lime else CtColors.Coral)
            }
        }

        CtSectionHeader(title = "Themes")
        if (training.strengths.isEmpty() && training.weaknesses.isEmpty()) {
            Text("After a few puzzles of each kind, your strongest and weakest patterns show here.", style = CtType.BodySmall)
        } else {
            ThemeList("Your strengths", training.strengths, CtColors.Lime)
            ThemeList("Worth practising", training.weaknesses, CtColors.Coral)
        }

        CtSectionHeader(title = "Lessons")
        CtCard(modifier = Modifier.fillMaxWidth()) {
            Text("${training.lessonsDone} of ${training.lessonsTotal} lessons done", style = CtType.Label)
            Spacer(Modifier.height(CtSpacing.sm))
            CtProgressBar(
                progress = if (training.lessonsTotal == 0) 0f else training.lessonsDone.toFloat() / training.lessonsTotal,
                color = CtColors.Gold,
                height = 8.dp,
            )
        }
    }
}

@Composable
private fun ThemeList(title: String, themes: List<ThemeScore>, color: Color) {
    if (themes.isEmpty()) return
    CtCard(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = CtType.Label)
        themes.forEach { theme ->
            Spacer(Modifier.height(CtSpacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(theme.name, style = CtType.BodySmall.copy(color = CtColors.Text), modifier = Modifier.weight(1f))
                Text("${percent(theme.rate)} of ${theme.attempts}", style = CtType.Caption)
            }
            Spacer(Modifier.width(CtSpacing.xs))
            CtProgressBar(progress = theme.rate, color = color, height = 6.dp)
        }
    }
}

private fun percent(value: Float): String = "${(value * 100).roundToInt()}%"
