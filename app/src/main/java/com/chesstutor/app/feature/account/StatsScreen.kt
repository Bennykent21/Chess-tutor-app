package com.chesstutor.app.feature.account

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import com.chesstutor.app.board.SideTones
import com.chesstutor.app.design.CtGroupLabel
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.domain.account.GamePhase
import com.chesstutor.app.domain.account.ClockInsight
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardPicture
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.board.PieceArt
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctEnter
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.account.ClockReport
import com.chesstutor.app.domain.account.ClockStats
import com.chesstutor.app.domain.account.GameEnding
import com.chesstutor.app.domain.account.GameFilter
import com.chesstutor.app.domain.account.GameOutcome
import com.chesstutor.app.domain.account.PerformanceCalculator
import com.chesstutor.app.domain.account.TimeClass
import com.chesstutor.app.domain.account.Wdl
import com.chesstutor.app.domain.account.WdlRow
import com.chesstutor.app.viewmodel.AppUiState
import kotlin.math.roundToInt

/**
 * Stats for one clock (D60, D64, D104), opened from its rating on Account: the clock chips at the
 * top drive everything. The clock's rating over its graph, the results in a card, records as boxes
 * that open their games (best win opens that game), how games ended, insights from the reviewed
 * games (where losses turned, why they were lost, accuracy), and openings as White and as Black.
 */
@Composable
fun StatsScreen(
    games: GamesState,
    state: AppUiState,
    initialClock: TimeClass,
    now: Long,
    courseIdFor: (openingFamily: String) -> String?,
    onOpenCourse: (courseId: String) -> Unit,
    onShowGames: (GameFilter) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    insights: Map<TimeClass, ClockInsight> = emptyMap(),
    onOpenGame: (gameId: String) -> Unit = {},
) {
    val played = remember(games.games) { ClockStats.clocksPlayed(games.games) }
    val clocks = played.ifEmpty { listOf(initialClock) }
    var clockName by rememberSaveable { mutableStateOf(initialClock.name) }
    val clock = TimeClass.valueOf(clockName).takeIf { it in clocks } ?: clocks.first()
    val report = remember(games.games, clock, state.linkedProfile) { ClockStats.report(games.games, clock, linkedRating(state, clock)) }
    val accuracy = remember(games, clock) { averageAccuracy(games, clock) }
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtTopBar(title = "Stats", onBack = onBack)
            if (clocks.size > 1) ClockChips(clocks, clock, onSelect = { it?.let { picked -> clockName = picked.name } })
            if (report.games > 0) {
                // Keyed on the clock, so each clock's graph draws itself in and starts at three months.
                androidx.compose.runtime.key(clock) {
                    RatingHero(report.trend, report.records.current, now, clockIcon(clock), CtColors.metal(clockHue(clock)), Modifier.padding(top = CtSpacing.md).ctEnter(0))
                }
            }
            Column(
                modifier = Modifier.padding(start = CtSpacing.gutter, end = CtSpacing.gutter, top = CtSpacing.md, bottom = CtSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.xl),
            ) {
                if (report.games == 0) {
                    CtEmptyState(icon = Icons.Rounded.Insights, title = "No ${clock.label.lowercase()} games yet", body = "Import your games or play a bot.")
                } else {
                    CtCard(Modifier.fillMaxWidth().ctEnter(1)) {
                        ResultsBar(report.results, onShow = { onShowGames(GameFilter(timeClass = clock, outcome = it)) })
                    }
                    RecordBoxes(
                        gameRecords(report, accuracy, clock, onOpenGame, onShowGames),
                        Modifier.ctEnter(2),
                    )
                    Endings(report, onShow = { outcome, ending -> onShowGames(GameFilter(timeClass = clock, outcome = outcome, ending = ending)) })
                    insights[clock]?.takeIf { it.reviewed > 0 }?.let { insight ->
                        Insights(insight, clock, report, onShow = onShowGames)
                    }
                    OpeningsAs(PieceColor.WHITE, report, games, state.appSettings.boardTheme, courseIdFor, onOpenCourse, onShowGames)
                    OpeningsAs(PieceColor.BLACK, report, games, state.appSettings.boardTheme, courseIdFor, onOpenCourse, onShowGames)
                }
            }
        }
    }
}

/** The linked site's own rating for [clock], which is fresher than the last imported game. */
private fun linkedRating(state: AppUiState, clock: TimeClass): Int? {
    val linked = state.linkedProfile ?: return null
    return when (clock) {
        TimeClass.RAPID -> linked.rapidRating
        TimeClass.BLITZ -> linked.blitzRating
        TimeClass.BULLET -> linked.bulletRating
        else -> null
    }
}

/** One record: a big number and what it is, and what a tap opens. */
internal data class Record(val value: String, val label: String, val onOpen: (() -> Unit)? = null)

/** The records the hero and graph do not already show (rating and peak are there); each opens its games (D104). */
private fun gameRecords(report: ClockReport, accuracy: Double?, clock: TimeClass, onOpenGame: (String) -> Unit, onShow: (GameFilter) -> Unit): List<Record> {
    val r = report.records
    val all = { onShow(GameFilter(timeClass = clock)) }
    return listOf(
        Record(r.bestWin?.opponentRating?.toString() ?: "–", "Best win", r.bestWin?.let { win -> { onOpenGame(win.id) } }),
        Record("${r.longestWinStreak}", "Win streak", { onShow(GameFilter(timeClass = clock, outcome = GameOutcome.WIN)) }),
        Record(accuracy?.let(::accuracyText) ?: "–", "Accuracy", all),
        Record(r.averageOpponent?.toString() ?: "–", "Avg opponent", all),
        Record(report.averageMoves?.toString() ?: "–", "Moves a game", all),
        Record("${report.games}", "Games", all),
    )
}

/** Records three to a row in boxes of one size, each a number over its name; a tap opens its games (D104). */
@Composable
private fun RecordBoxes(records: List<Record>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        records.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                row.forEach { record ->
                    Column(
                        Modifier
                            .weight(1f)
                            .height(76.dp)
                            .clip(CtShapes.CardSmall)
                            .background(CtColors.Raised)
                            .then(record.onOpen?.let { Modifier.ctPressable(onClickLabel = "Show the games", onClick = it) } ?: Modifier)
                            .semantics(mergeDescendants = true) {}
                            .padding(CtSpacing.sm),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(record.value, style = CtType.Number, maxLines = 1)
                        Text(record.label, style = CtType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** Records three to a row, each a number over its name, straight on the page. */
@Composable
internal fun Figures(records: List<Record>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CtSpacing.lg)) {
        records.chunked(3).forEach { row ->
            Row {
                row.forEach { record ->
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                        Text(record.value, style = CtType.Number, maxLines = 1)
                        Text(record.label, style = CtType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** Wins and losses by how they ended, in one card; a row opens those games (D104). */
@Composable
private fun Endings(report: ClockReport, onShow: (GameOutcome, GameEnding) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        CtGroupLabel("How games end")
        CtCard(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.lg)) {
                Endings("Won by", report.wonBy, Modifier.weight(1f), onShow = { onShow(GameOutcome.WIN, it) })
                Endings("Lost by", report.lostBy, Modifier.weight(1f), onShow = { onShow(GameOutcome.LOSS, it) })
            }
        }
    }
}

@Composable
private fun Endings(title: String, counts: Map<GameEnding, Int>, modifier: Modifier, onShow: (GameEnding) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = CtType.Label)
        GameEnding.entries.filter { it != GameEnding.OTHER || (counts[it] ?: 0) > 0 }.forEach { ending ->
            val n = counts[ending] ?: 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .then(if (n > 0) Modifier.ctPressable(onClickLabel = "Show the games") { onShow(ending) } else Modifier)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(ending.label, style = CtType.BodySmall, modifier = Modifier.weight(1f))
                Text("$n", style = CtType.Label)
            }
        }
    }
}

/**
 * Insights from the clock's reviewed games (D104): where the losses turned (the phase of the
 * player's costliest move), why they were lost, and the accuracy over the reviewed games.
 */
@Composable
private fun Insights(insight: ClockInsight, clock: TimeClass, report: ClockReport, onShow: (GameFilter) -> Unit) {
    val losses = GameFilter(timeClass = clock, outcome = GameOutcome.LOSS)
    Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        CtGroupLabel("Insights · ${insight.reviewed} reviewed game${if (insight.reviewed == 1) "" else "s"}")
        if (insight.losses > 0) {
            CtCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    Text("Where your losses turned", style = CtType.TitleSmall)
                    PhaseBars(insight.turned)
                }
            }
            val reasons = listOf(
                "A blunder decided it" to insight.blunders,
                "A winning game let slip" to insight.slipped,
                "Lost on time" to (report.lostBy[GameEnding.TIME] ?: 0),
            ).filter { it.second > 0 }
            if (reasons.isNotEmpty()) {
                CtCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text("Why you lose", style = CtType.TitleSmall, modifier = Modifier.padding(bottom = CtSpacing.xs))
                        reasons.forEach { (label, n) ->
                            val ending = if (label == "Lost on time") GameEnding.TIME else null
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .ctPressable(onClickLabel = "Show the games") { onShow(losses.copy(ending = ending)) }
                                    .semantics(mergeDescendants = true) {},
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, style = CtType.BodySmall, modifier = Modifier.weight(1f))
                                Text("$n", style = CtType.Label)
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = CtColors.Text2, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
        if (insight.accuracy.size >= 2) {
            CtCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Accuracy", style = CtType.TitleSmall, modifier = Modifier.weight(1f))
                        Text("last ${minOf(10, insight.accuracy.size)}: ${accuracyText(insight.accuracy.takeLast(10).average())}", style = CtType.Caption)
                    }
                    AccuracyLine(insight.accuracy.takeLast(30))
                }
            }
        }
    }
}

/** Three bars, opening, middlegame and endgame, as tall as their share; the tallest in red. */
@Composable
private fun PhaseBars(turned: Map<GamePhase, Int>) {
    val most = (turned.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val top = turned.maxByOrNull { it.value }?.key
    Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(CtSpacing.md), verticalAlignment = Alignment.Bottom) {
        GamePhase.entries.forEach { phase ->
            val n = turned[phase] ?: 0
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "${phase.label}: $n" }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$n", style = CtType.Label)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((52f * n / most).dp.coerceAtLeast(3.dp))
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (phase == top && n > 0) CtColors.Error else CtColors.Track),
                )
                Text(phase.label, style = CtType.Caption, maxLines = 1)
            }
        }
    }
}

/** The player's accuracy over the reviewed games, oldest on the left. */
@Composable
private fun AccuracyLine(values: List<Double>) {
    val color = CtColors.Accent
    Canvas(Modifier.fillMaxWidth().height(52.dp).semantics { contentDescription = "Accuracy over your last ${values.size} reviewed games" }) {
        val low = (values.minOrNull() ?: 0.0) - 5
        val high = (values.maxOrNull() ?: 100.0) + 5
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1).coerceAtLeast(1)
            val y = size.height * (1f - ((v - low) / (high - low)).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** One colour's openings, under the king of that colour with its score; the weakest score is red. */
@Composable
private fun OpeningsAs(
    color: PieceColor,
    report: ClockReport,
    games: GamesState,
    boardTheme: BoardTheme,
    courseIdFor: (String) -> String?,
    onOpenCourse: (String) -> Unit,
    onShowGames: (GameFilter) -> Unit,
) {
    val rows = if (color == PieceColor.WHITE) report.asWhite else report.asBlack
    if (rows.isEmpty()) return
    val total = rows.fold(Wdl()) { acc, row -> Wdl(acc.wins + row.wdl.wins, acc.draws + row.wdl.draws, acc.losses + row.wdl.losses) }
    val weak = remember(rows) { PerformanceCalculator.weakest(rows) }
    val pictures = remember(rows, games.games) { rows.associate { it.label to PerformanceCalculator.openingFen(games.games, it.label) } }
    val side = if (color == PieceColor.WHITE) "As White" else "As Black"
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .ctPressable(onClickLabel = "Show the games") { onShowGames(GameFilter(timeClass = report.clock, color = color)) }
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
        ) {
            // The side as a square in its own colour, like the clocks (D104).
            Box(
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (color == PieceColor.WHITE) SideTones.White else SideTones.Black)
                    .then(if (color == PieceColor.WHITE) Modifier.border(1.dp, CtColors.Line, RoundedCornerShape(6.dp)) else Modifier),
            )
            Text(side, style = CtType.Title, modifier = Modifier.weight(1f))
            Text(percent(total.score), style = CtType.Label)
        }
        rows.forEach { row ->
            val courseId = courseIdFor(row.label)
            OpeningRow(
                row = row,
                fen = pictures[row.label],
                boardTheme = boardTheme,
                weak = row.label in weak,
                learn = courseId != null,
                onClick = { if (courseId != null) onOpenCourse(courseId) else onShowGames(row.filter) },
            )
        }
    }
}

/** Every game at a glance: one bar split by result with the score beside it, and each result's count (a tap shows those games). */
@Composable
internal fun ResultsBar(wdl: Wdl, onShow: (GameOutcome) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CtSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.lg)) {
            WdlBar(wdl, Modifier.weight(1f), thickness = 10.dp)
            ResultFigure(percent(wdl.score), "Score")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.lg)) {
            counts(wdl).forEach { (outcome, n) -> ResultLine(outcome, n, onClick = { onShow(outcome) }) }
        }
    }
}

private fun counts(wdl: Wdl): List<Pair<GameOutcome, Int>> =
    listOf(GameOutcome.WIN to wdl.wins, GameOutcome.DRAW to wdl.draws, GameOutcome.LOSS to wdl.losses)

@Composable
private fun ResultLine(outcome: GameOutcome, count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(40.dp)
            .ctPressable(onClickLabel = "Show the games", onClick = onClick)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ResultSquare(outcome, size = 24.dp)
        Text("$count", style = CtType.TitleSmall, maxLines = 1)
    }
}

@Composable
private fun ResultFigure(value: String, label: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.End) {
        Text(value, style = CtType.Number, maxLines = 1)
        Text(label, style = CtType.Caption, maxLines = 1)
    }
}

/** Wins, draws and losses side by side, each as wide as its share. */
@Composable
private fun WdlBar(wdl: Wdl, modifier: Modifier = Modifier, thickness: Dp = 4.dp) {
    Row(modifier.fillMaxWidth().height(thickness).clip(CtShapes.Pill), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        counts(wdl).filter { (_, n) -> n > 0 }.forEach { (outcome, n) ->
            Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(outcomeColor(outcome)))
        }
    }
}

/** A picture of the opening, its name and score, its results, the number of games; the weakest score is red. */
@Composable
private fun OpeningRow(row: WdlRow, fen: String?, boardTheme: BoardTheme, weak: Boolean, learn: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .ctPressable(onClickLabel = if (learn) "Learn the ${row.label}" else "Show the games") { onClick() }
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
    ) {
        Box(Modifier.size(52.dp).clip(CtShapes.Chip)) { BoardPicture(fen, boardTheme, zoom = 1.8f) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                Text(row.label, style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(percent(row.wdl.score), style = CtType.Label.copy(color = if (weak) CtColors.Error else CtColors.Text))
            }
            WdlBar(row.wdl)
            Text("${row.wdl.games} game${if (row.wdl.games == 1) "" else "s"}", style = CtType.Caption)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = CtColors.Text2, modifier = Modifier.size(20.dp))
    }
}

/** The player's average accuracy over their reviewed games, in [clock] when given, or null before any review. */
internal fun averageAccuracy(games: GamesState, clock: TimeClass? = null): Double? =
    games.games.filter { clock == null || it.timeClass == clock }.mapNotNull { game -> games.analysed[game.id]?.accuracyFor(game.userColor) }
        .takeIf { it.isNotEmpty() }
        ?.average()

internal fun percent(value: Float): String = "${(value * 100).roundToInt()}%"
