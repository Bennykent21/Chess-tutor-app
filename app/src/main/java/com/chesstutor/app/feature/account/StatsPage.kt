package com.chesstutor.app.feature.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardPicture
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.board.PieceArt
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtRing
import com.chesstutor.app.design.CtRingPart
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSegmented
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctEnter
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.account.GameFilter
import com.chesstutor.app.domain.account.GameOutcome
import com.chesstutor.app.domain.account.PerformanceCalculator
import com.chesstutor.app.domain.account.RatingTrend
import com.chesstutor.app.domain.account.TimeClass
import com.chesstutor.app.domain.account.Wdl
import com.chesstutor.app.domain.account.WdlRow
import kotlin.math.roundToInt

/**
 * Account > Stats (D44, D55, D59): the rating graph with a tab for each clock, the results, then the player's openings by
 * score (the weakest in the error colour; a tap opens the course that teaches one, or its games
 * when there is no course), and the results with each colour. A result opens its games.
 */
@Composable
fun StatsPage(
    games: GamesState,
    gameTrends: List<RatingTrend>,
    puzzleTrend: RatingTrend,
    /** The clock the app follows, whose tab opens first. */
    followed: TimeClass?,
    now: Long,
    boardTheme: BoardTheme,
    courseIdFor: (openingFamily: String) -> String?,
    onOpenCourse: (courseId: String) -> Unit,
    onShowGames: (GameFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val report = remember(games.games) { PerformanceCalculator.report(games.games, emptyMap()) }
    val accuracy = remember(games) { averageAccuracy(games) }
    val graphs = (gameTrends + puzzleTrend).filter { it.points.size >= 2 }
    Column(modifier) {
        if (report.isEmpty && graphs.isEmpty()) {
            CtEmptyState(icon = Icons.Rounded.Insights, title = "No finished games yet", body = "Play a bot from Home or link your account.")
            return@Column
        }
        if (graphs.isNotEmpty()) RatingCard(graphs, followed, now, Modifier.padding(top = CtSpacing.sm).ctEnter(0))
        if (!report.isEmpty) {
            ResultsCard(report.overall, accuracy, onShow = { onShowGames(GameFilter(outcome = it)) }, Modifier.padding(top = CtSpacing.md).ctEnter(1))
        }
        if (report.byOpening.isNotEmpty()) {
            CtSectionHeader(title = "Your openings")
            val weak = remember(report) { PerformanceCalculator.weakest(report.byOpening) }
            val pictures = remember(report) { report.byOpening.associate { it.label to PerformanceCalculator.openingFen(games.games, it.label) } }
            report.byOpening.forEach { row ->
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
        if (report.byColor.isNotEmpty()) {
            CtSectionHeader(title = "By colour")
            report.byColor.forEach { row ->
                ColourRow(row, boardTheme, onClick = { onShowGames(row.filter) })
            }
        }
    }
}

/** The graph, with a tab for each rating that has a history. */
@Composable
private fun RatingCard(graphs: List<RatingTrend>, followed: TimeClass?, now: Long, modifier: Modifier = Modifier) {
    var chosen by rememberSaveable { mutableStateOf(followed?.name) }
    val trend = graphs.firstOrNull { (it.timeClass?.name ?: PUZZLES) == chosen } ?: graphs.first()
    Column(
        modifier = modifier.fillMaxWidth().clip(CtShapes.Card).background(CtColors.Surface).padding(CtSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
    ) {
        if (graphs.size > 1) {
            CtSegmented(
                options = graphs,
                selected = trend,
                label = { it.timeClass?.label ?: "Puzzles" },
                onSelect = { chosen = it.timeClass?.name ?: PUZZLES },
            )
        }
        RatingGraph(trend, now)
    }
}

private const val PUZZLES = "PUZZLES"

/** Every game at a glance: the split as a ring, a line for each result, the score and accuracy. */
@Composable
private fun ResultsCard(wdl: Wdl, accuracy: Double?, onShow: (GameOutcome) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().clip(CtShapes.Card).background(CtColors.Surface).padding(horizontal = CtSpacing.lg, vertical = CtSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.lg),
    ) {
        val total = wdl.games.coerceAtLeast(1).toFloat()
        CtRing(
            parts = counts(wdl).map { (outcome, n) -> CtRingPart(n / total, outcomeColor(outcome)) },
            diameter = 96.dp,
            stroke = 10.dp,
        ) {
            Column(Modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${wdl.games}", style = CtType.Number, maxLines = 1)
                Text("Games", style = CtType.Caption, maxLines = 1)
            }
        }
        Column(Modifier.weight(1f)) {
            counts(wdl).forEach { (outcome, n) -> ResultLine(outcome, n, onClick = { onShow(outcome) }) }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
            Figure(percent(wdl.score), "Score")
            if (accuracy != null) Figure(accuracyText(accuracy), "Accuracy")
        }
    }
}

private fun counts(wdl: Wdl): List<Pair<GameOutcome, Int>> =
    listOf(GameOutcome.WIN to wdl.wins, GameOutcome.DRAW to wdl.draws, GameOutcome.LOSS to wdl.losses)

@Composable
private fun ResultLine(outcome: GameOutcome, count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .ctPressable(onClickLabel = "Show the games", onClick = onClick)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ResultSquare(outcome, size = 28.dp)
        Text("$count", style = CtType.TitleSmall, maxLines = 1)
    }
}

@Composable
private fun Figure(value: String, label: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.End) {
        Text(value, style = CtType.Number, maxLines = 1)
        Text(label, style = CtType.Caption, maxLines = 1)
    }
}

/** Wins, draws and losses side by side, each as wide as its share. */
@Composable
private fun WdlBar(wdl: Wdl, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(4.dp).clip(CtShapes.Pill), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
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

/** The king of that colour, in the player's own set, says which side the row is. */
@Composable
private fun ColourRow(row: WdlRow, boardTheme: BoardTheme, onClick: () -> Unit) {
    val color = row.filter.color ?: PieceColor.WHITE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .ctPressable(onClickLabel = "Show the games") { onClick() }
            .semantics(mergeDescendants = true) { contentDescription = row.label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
    ) {
        Image(painterResource(PieceArt.drawable(boardTheme.pieceSet, Piece(PieceType.KING, color))), contentDescription = null, modifier = Modifier.size(32.dp))
        WdlBar(row.wdl, Modifier.weight(1f))
        Text(percent(row.wdl.score), style = CtType.Label, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
    }
}

/** The player's average accuracy over their reviewed games, or null before any review. */
internal fun averageAccuracy(games: GamesState): Double? =
    games.games.mapNotNull { game -> games.analysed[game.id]?.accuracyFor(game.userColor) }
        .takeIf { it.isNotEmpty() }
        ?.average()

internal fun percent(value: Float): String = "${(value * 100).roundToInt()}%"
