package com.chesstutor.app.feature.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.data.model.GameSource
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.account.GameFilter
import com.chesstutor.app.domain.account.GameLibrary
import com.chesstutor.app.domain.account.GameReview
import com.chesstutor.app.domain.account.MistakeKind
import com.chesstutor.app.domain.account.PerformanceCalculator
import com.chesstutor.app.domain.account.PerformanceReport
import com.chesstutor.app.domain.account.RatingPoint
import com.chesstutor.app.domain.account.Wdl
import com.chesstutor.app.domain.account.WdlRow
import com.chesstutor.app.domain.account.label
import kotlin.math.roundToInt

/**
 * The Performance page (plan 6.4): results by colour, time control and opening (tap a bar to
 * see its games), accuracy by phase and common mistakes from analysed games, and the rating
 * trend from imported games. A source chip narrows everything to one place.
 */
@Composable
fun PerformancePage(
    games: GamesState,
    reviews: Map<String, GameReview>,
    onShowGames: (GameFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sourceName by rememberSaveable { mutableStateOf<String?>(null) }
    val source = sourceName?.let(GameSource::valueOf)
    val sources = remember(games.games) { GameLibrary.sources(games.games) }
    val base = GameFilter(source = source)
    val report = remember(games.games, reviews, source) {
        PerformanceCalculator.report(GameLibrary.filter(games.games, base), reviews, base)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
        if (sources.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                CtChip(text = "All games", selected = source == null, onClick = { sourceName = null })
                sources.forEach { s -> CtChip(text = s.label, selected = s == source, onClick = { sourceName = s.name }) }
            }
        }
        if (report.isEmpty) {
            CtEmptyState(icon = Icons.Rounded.Insights, title = "No finished games yet", body = "Play a bot from Home or import your games, and your results show here.")
            return@Column
        }
        CtCard(onClick = { onShowGames(base) }, onClickLabel = "Show these games", modifier = Modifier.fillMaxWidth()) {
            Text("Score ${percent(report.overall.score)}", style = CtType.Title)
            Text("${report.overall.games} games · ${report.overall.wins} won, ${report.overall.draws} drawn, ${report.overall.losses} lost", style = CtType.BodySmall)
            Spacer(Modifier.height(CtSpacing.sm))
            WdlBar(report.overall)
        }
        WdlSection("By colour", report.byColor, onShowGames)
        WdlSection("By time control", report.byTimeClass, onShowGames)
        WdlSection("By opening", report.byOpening, onShowGames)
        Analysed(report)
        RatingSection(report)
    }
}

@Composable
private fun WdlSection(title: String, rows: List<WdlRow>, onShowGames: (GameFilter) -> Unit) {
    if (rows.isEmpty()) return
    CtSectionHeader(title = title)
    rows.forEach { row ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CtShapes.CardSmall)
                .background(CtColors.Surface)
                .ctPressable(onClickLabel = "Show the games: ${row.label}") { onShowGames(row.filter) }
                .padding(CtSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CtSpacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.label, style = CtType.Label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${row.wdl.games} game${if (row.wdl.games == 1) "" else "s"} · ${percent(row.wdl.score)}", style = CtType.Caption)
            }
            WdlBar(row.wdl)
        }
    }
}

/** Wins, draws and losses as one bar: lime, grey and coral. */
@Composable
internal fun WdlBar(wdl: Wdl, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CtShapes.Pill)
            .background(CtColors.SurfaceHigh)
            .semantics { contentDescription = "${wdl.wins} won, ${wdl.draws} drawn, ${wdl.losses} lost" },
    ) {
        listOf(wdl.wins to CtColors.Lime, wdl.draws to CtColors.Text3, wdl.losses to CtColors.Coral).forEach { (count, color) ->
            if (count > 0) Box(Modifier.weight(count.toFloat()).height(10.dp).background(color))
        }
    }
}

@Composable
private fun Analysed(report: PerformanceReport) {
    CtSectionHeader(title = "From analysed games")
    if (report.analysedGames == 0) {
        Text("Open a game and tap Review to analyse it. Accuracy by phase and your common mistakes then show here.", style = CtType.BodySmall)
        return
    }
    CtCard(modifier = Modifier.fillMaxWidth()) {
        Text("Accuracy by phase", style = CtType.Label)
        Text("${report.analysedGames} analysed game${if (report.analysedGames == 1) "" else "s"}", style = CtType.Caption)
        Spacer(Modifier.height(CtSpacing.sm))
        report.phases.forEach { phase ->
            Row(Modifier.fillMaxWidth().padding(vertical = CtSpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                Text(phase.phase.label, style = CtType.BodySmall, modifier = Modifier.weight(1f))
                Text(if (phase.moves == 0) "–" else accuracyText(phase.accuracy), style = CtType.Label.copy(color = CtColors.Teal), modifier = Modifier.width(72.dp))
                Text("${phase.errors} slip${if (phase.errors == 1) "" else "s"}", style = CtType.Caption, modifier = Modifier.width(64.dp))
            }
        }
    }
    CtCard(modifier = Modifier.fillMaxWidth()) {
        Text("Common mistakes", style = CtType.Label)
        val total = report.mistakes.values.sum()
        if (total == 0) {
            Text("No mistakes or blunders found yet. Well played!", style = CtType.BodySmall)
        }
        MistakeKind.entries.filter { (report.mistakes[it] ?: 0) > 0 }.sortedByDescending { report.mistakes[it] }.forEach { kind ->
            Spacer(Modifier.height(CtSpacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(kind.label, style = CtType.BodySmall.copy(color = CtColors.Text))
                    Text(kind.description, style = CtType.Caption)
                }
                Text("${report.mistakes[kind]}", style = CtType.Number.copy(color = CtColors.Coral))
            }
        }
    }
}

@Composable
private fun RatingSection(report: PerformanceReport) {
    CtSectionHeader(title = "Rating trend")
    val points = report.ratingTrend
    if (points.size < 2) {
        Text("Import rated games from Chess.com or Lichess to see your rating over time.", style = CtType.BodySmall)
        return
    }
    CtCard(modifier = Modifier.fillMaxWidth()) {
        val first = points.first().rating
        val last = points.last().rating
        val change = last - first
        Text("${report.ratingTimeClass?.label ?: "Rated"} · $last", style = CtType.Title)
        Text(
            "${if (change >= 0) "+" else ""}$change over ${points.size} games (${formatDate(points.first().dateMillis)} to ${formatDate(points.last().dateMillis)})",
            style = CtType.BodySmall,
        )
        Spacer(Modifier.height(CtSpacing.sm))
        RatingChart(points, if (change >= 0) CtColors.Lime else CtColors.Coral)
        Row {
            Text("Low ${points.minOf { it.rating }}", style = CtType.Caption, modifier = Modifier.weight(1f))
            Text("High ${points.maxOf { it.rating }}", style = CtType.Caption)
        }
    }
}

@Composable
private fun RatingChart(points: List<RatingPoint>, color: Color) {
    val low = points.minOf { it.rating }
    val high = points.maxOf { it.rating }
    val span = (high - low).coerceAtLeast(1)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .semantics { contentDescription = "Rating from ${points.first().rating} to ${points.last().rating}" },
    ) {
        val w = size.width
        val h = size.height
        fun x(i: Int) = if (points.size == 1) 0f else i / (points.size - 1f) * w
        fun y(r: Int) = h - (r - low) / span.toFloat() * (h - 8.dp.toPx()) - 4.dp.toPx()
        val path = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.rating)) else lineTo(x(i), y(p.rating)) }
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        drawCircle(color, radius = 4.dp.toPx(), center = Offset(x(points.lastIndex), y(points.last().rating)))
    }
}

private fun percent(value: Float): String = "${(value * 100).roundToInt()}%"
