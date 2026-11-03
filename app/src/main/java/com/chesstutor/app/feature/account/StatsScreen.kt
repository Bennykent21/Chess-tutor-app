package com.chesstutor.app.feature.account

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
 * Stats for one clock (D60, D64), opened from its rating on Account: the clock's metal and rating
 * over its graph in the metal's colour, its records as plain figures, its results, how its games
 * ended, and its openings as White and as Black. Every result and opening opens those games; an
 * opening with a course opens the course instead.
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
                    Figures(gameRecords(report, accuracy), Modifier.ctEnter(1))
                    ResultsBar(report.results, onShow = { onShowGames(GameFilter(timeClass = clock, outcome = it)) }, Modifier.ctEnter(2))
                    Endings(report)
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

/** One record: a big number and what it is. */
internal data class Record(val value: String, val label: String)

/** The records the hero and graph do not already show (rating and peak are there). */
private fun gameRecords(report: ClockReport, accuracy: Double?): List<Record> {
    val r = report.records
    return listOf(
        Record(r.bestWin?.opponentRating?.toString() ?: "–", "Best win"),
        Record("${r.longestWinStreak}", "Win streak"),
        Record(accuracy?.let(::accuracyText) ?: "–", "Accuracy"),
        Record(r.averageOpponent?.toString() ?: "–", "Avg opponent"),
        Record(report.averageMoves?.toString() ?: "–", "Moves a game"),
        Record("${report.games}", "Games"),
    )
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

/** Wins and losses by how they ended. */
@Composable
private fun Endings(report: ClockReport) {
    Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.xl)) {
        Endings("Won by", report.wonBy, Modifier.weight(1f))
        Endings("Lost by", report.lostBy, Modifier.weight(1f))
    }
}

@Composable
private fun Endings(title: String, counts: Map<GameEnding, Int>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = CtType.Label)
        GameEnding.entries.filter { it != GameEnding.OTHER || (counts[it] ?: 0) > 0 }.forEach { ending ->
            Row(Modifier.semantics(mergeDescendants = true) {}) {
                Text(ending.label, style = CtType.BodySmall, modifier = Modifier.weight(1f))
                Text("${counts[ending] ?: 0}", style = CtType.Label)
            }
        }
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
            Image(painterResource(PieceArt.drawable(boardTheme.pieceSet, Piece(PieceType.KING, color))), contentDescription = null, modifier = Modifier.size(28.dp))
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
