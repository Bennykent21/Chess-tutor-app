package com.chesstutor.app.feature.account

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtDivider
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.account.ThemeScore
import com.chesstutor.app.domain.account.TrainingReport

/**
 * Account > Training (D44): the puzzle themes that trip the player up (a tap plays puzzles
 * of that theme), and the positions from their games due for review.
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
        val (title, themes) = when {
            training.weaknesses.isNotEmpty() -> "Themes to work on" to training.weaknesses
            else -> "Your best themes" to training.strengths
        }
        if (themes.isEmpty()) {
            CtSectionHeader(title = "Themes")
            Text("Solve a few puzzles of each kind to see them here.", style = CtType.BodySmall)
        } else {
            CtSectionHeader(title = title)
            themes.forEachIndexed { i, theme ->
                ThemeRow(theme, divider = i < themes.lastIndex, onClick = { onPracticeTheme(theme.theme) })
            }
        }
        MistakesRow(dueReviewCount, onReviewMistakes, Modifier.padding(top = CtSpacing.lg))
    }
}

@Composable
private fun ThemeRow(theme: ThemeScore, divider: Boolean, onClick: () -> Unit) {
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .ctPressable(onClickLabel = "Practise ${theme.name}") { onClick() }
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(theme.name, style = CtType.TitleSmall, modifier = Modifier.weight(1f))
                Text("${percent(theme.rate)} clean", style = CtType.Caption)
            }
            CtProgressBar(progress = theme.rate, height = 3.dp, modifier = Modifier.fillMaxWidth())
        }
        if (divider) CtDivider()
    }
}

@Composable
private fun MistakesRow(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(CtShapes.CardSmall)
            .background(CtColors.Surface)
            .ctPressable(onClickLabel = "Review your mistakes") { onClick() }
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = CtSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
    ) {
        Text("$count", style = CtType.Number)
        Text("Mistakes to review", style = CtType.TitleSmall, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = CtColors.Text2, modifier = Modifier.size(20.dp))
    }
}
