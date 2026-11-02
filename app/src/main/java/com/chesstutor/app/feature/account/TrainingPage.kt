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
import com.chesstutor.app.domain.account.ThemeScore
import com.chesstutor.app.domain.account.TrainingReport

/**
 * Account > Training (D44): the positions from the player's games due for review, and the
 * puzzle themes that trip them up, each with its share of clean solves (a tap plays that theme).
 */
@Composable
fun TrainingPage(
    training: TrainingReport,
    dueReviewCount: Int,
    onReviewMistakes: () -> Unit,
    onPracticeTheme: (theme: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        MistakesTile(dueReviewCount, onReviewMistakes, Modifier.padding(top = CtSpacing.md))
        val (title, themes) = when {
            training.weaknesses.isNotEmpty() -> "Themes to work on" to training.weaknesses
            else -> "Your best themes" to training.strengths
        }
        if (themes.isEmpty()) {
            CtSectionHeader(title = "Themes")
            Text("Solve a few puzzles of each kind to see them here.", style = CtType.BodySmall)
        } else {
            CtSectionHeader(title = title)
            themes.forEach { theme ->
                ThemeRow(theme, onClick = { onPracticeTheme(theme.theme) })
            }
        }
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
