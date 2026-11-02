package com.chesstutor.app.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtDivider
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.account.GameFilter
import com.chesstutor.app.domain.account.PerformanceCalculator
import com.chesstutor.app.domain.account.WdlRow
import kotlin.math.roundToInt

/**
 * Account > Stats (D44): the score, games and accuracy, then the player's openings by score
 * (the weakest in the error colour; a tap opens the course that teaches one, or its games
 * when there is no course), and the score with each colour.
 */
@Composable
fun StatsPage(
    games: GamesState,
    courseIdFor: (openingFamily: String) -> String?,
    onOpenCourse: (courseId: String) -> Unit,
    onShowGames: (GameFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val report = remember(games.games) { PerformanceCalculator.report(games.games, emptyMap()) }
    val accuracy = remember(games) { averageAccuracy(games) }
    Column(modifier) {
        if (report.isEmpty) {
            CtEmptyState(icon = Icons.Rounded.Insights, title = "No finished games yet", body = "Play a bot from Home or link your account.")
            return@Column
        }
        Row(Modifier.padding(top = CtSpacing.sm), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TotalTile(percent(report.overall.score), "Score", Modifier.weight(1f))
            TotalTile("${report.overall.games}", "Games", Modifier.weight(1f))
            if (accuracy != null) TotalTile(accuracyText(accuracy), "Accuracy", Modifier.weight(1f))
        }
        if (report.byOpening.isNotEmpty()) {
            CtSectionHeader(title = "Your openings")
            val weak = remember(report) { PerformanceCalculator.weakest(report.byOpening) }
            report.byOpening.forEachIndexed { i, row ->
                val courseId = courseIdFor(row.label)
                OpeningRow(
                    row = row,
                    weak = row.label in weak,
                    learn = courseId != null,
                    divider = i < report.byOpening.lastIndex,
                    onClick = { if (courseId != null) onOpenCourse(courseId) else onShowGames(row.filter) },
                )
            }
        }
        if (report.byColor.isNotEmpty()) {
            CtSectionHeader(title = "By colour")
            report.byColor.forEachIndexed { i, row ->
                ColourRow(row, divider = i < report.byColor.lastIndex, onClick = { onShowGames(row.filter) })
            }
        }
    }
}

@Composable
private fun TotalTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(CtShapes.CardSmall)
            .background(CtColors.Surface)
            .semantics(mergeDescendants = true) {}
            .padding(CtSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CtSpacing.xxs),
    ) {
        Text(value, style = CtType.Number, maxLines = 1)
        Text(label, style = CtType.Caption, maxLines = 1)
    }
}

/** Name and score, a thin bar, the number of games; the weakest are coloured, not labelled. */
@Composable
private fun OpeningRow(row: WdlRow, weak: Boolean, learn: Boolean, divider: Boolean, onClick: () -> Unit) {
    val tint = if (weak) CtColors.Error else CtColors.Accent
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .ctPressable(onClickLabel = if (learn) "Learn the ${row.label}" else "Show the games") { onClick() }
                .semantics(mergeDescendants = true) {}
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    Text(row.label, style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(percent(row.wdl.score), style = CtType.Label.copy(color = if (weak) CtColors.Error else CtColors.Text))
                }
                CtProgressBar(progress = row.wdl.score, color = tint, modifier = Modifier.fillMaxWidth())
                Text("${row.wdl.games} game${if (row.wdl.games == 1) "" else "s"}", style = CtType.Caption)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = CtColors.Text2, modifier = Modifier.size(20.dp))
        }
        if (divider) CtDivider()
    }
}

@Composable
private fun ColourRow(row: WdlRow, divider: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .ctPressable(onClickLabel = "Show the games") { onClick() }
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
        ) {
            Text(row.label, style = CtType.Body.copy(color = CtColors.Text), modifier = Modifier.weight(1f))
            CtProgressBar(progress = row.wdl.score, modifier = Modifier.width(120.dp))
            Text(percent(row.wdl.score), style = CtType.Label, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
        }
        if (divider) CtDivider()
    }
}

/** The player's average accuracy over their reviewed games, or null before any review. */
internal fun averageAccuracy(games: GamesState): Double? =
    games.games.mapNotNull { game -> games.analysed[game.id]?.accuracyFor(game.userColor) }
        .takeIf { it.isNotEmpty() }
        ?.average()

internal fun percent(value: Float): String = "${(value * 100).roundToInt()}%"
