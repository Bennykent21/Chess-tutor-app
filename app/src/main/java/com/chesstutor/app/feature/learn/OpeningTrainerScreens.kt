package com.chesstutor.app.feature.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardArrow
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardHost
import com.chesstutor.app.board.MarkKind
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtCoachBubble
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType

import java.util.concurrent.TimeUnit

/** The opening trainer's list (plan 6C): the openings for White and for Black, with lines learned and due. */
@Composable
fun OpeningTrainerScreen(state: TrainerState, onOpen: (openingId: String) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtTopBar(title = "Opening trainer", subtitle = "Learn the main lines by heart", onBack = onBack)
            Column(Modifier.padding(horizontal = CtSpacing.gutter), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                if (state.loading) {
                    CtEmptyState(icon = Icons.Rounded.Lightbulb, title = "Opening the book", body = "Reading the opening database…")
                    return@Column
                }
                Text(
                    "Play your side's moves from memory; the other side answers from the book. A clean run brings the line back later, a slip brings it back today.",
                    style = CtType.Body,
                )
                listOf(PieceColor.WHITE to "As White", PieceColor.BLACK to "As Black").forEach { (side, title) ->
                    CtSectionHeader(title = title)
                    state.openings.filter { it.opening.side == side && it.lines.isNotEmpty() }.forEach { item ->
                        CtCard(onClick = { onOpen(item.opening.id) }, onClickLabel = "Train ${item.opening.title}", modifier = Modifier.fillMaxWidth()) {
                            Row {
                                Column(Modifier.weight(1f)) {
                                    Text(item.opening.title, style = CtType.TitleSmall)
                                    Text("${item.learned} of ${item.lines.size} lines learned", style = CtType.Caption)
                                }
                                if (item.due > 0) CtChip(text = "${item.due} due", color = CtColors.Coral)
                                else if (item.started < item.lines.size) CtChip(text = "New lines", color = CtColors.Teal)
                            }
                            Spacer(Modifier.height(CtSpacing.sm))
                            CtProgressBar(progress = item.learned.toFloat() / item.lines.size, color = CtColors.Teal, height = 6.dp)
                        }
                    }
                }
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}

/** One line on the board: the player's moves from memory, the book's replies by themselves. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpeningDrillScreen(
    drill: DrillUi?,
    settings: AppSettings,
    onFeedback: (BoardFeedback) -> Unit,
    onShowAnswer: () -> Unit,
    onAgain: () -> Unit,
    onNext: () -> Unit,
    onPickLine: (lineId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (drill == null) {
                CtTopBar(title = "Opening trainer", onBack = onBack)
                return@Column
            }
            val session = drill.session
            val board by session.state.collectAsState()
            val (made, total) = session.progress
            CtTopBar(title = drill.opening.title, subtitle = drill.line.variation, onBack = onBack)
            Column(Modifier.padding(horizontal = CtSpacing.gutter), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                val answer = session.shownAnswer
                CtCoachBubble(
                    mascot = settings.mascot,
                    text = when {
                        drill.result != null && session.isClean -> "Clean! ${comesBack(drill.result.dueAt - drill.result.lastPlayedAt)}"
                        drill.result != null -> "Line done with ${session.slips} slip${if (session.slips == 1) "" else "s"}. It comes back today."
                        answer != null -> "The book move is shown. Play it to carry on."
                        session.replyDue -> "Book reply coming…"
                        else -> "Your move ($made of $total). You play ${if (drill.line.side == PieceColor.WHITE) "White" else "Black"}."
                    },
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                )
                BoardHost(
                    session = session,
                    theme = settings.boardTheme,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    whiteAtBottom = drill.line.side == PieceColor.WHITE,
                    showCoordinates = settings.showCoordinates,
                    showLegalDots = settings.showLegalDots,
                    autoQueen = settings.autoQueen,
                    arrows = listOfNotNull(answer?.let { BoardArrow(it.from, it.to, MarkKind.HINT) }),
                    onFeedback = onFeedback,
                )
                CtProgressBar(progress = if (total == 0) 0f else made.toFloat() / total, color = CtColors.Teal, height = 6.dp)
                if (drill.result == null) {
                    CtButton(text = "Show me", icon = Icons.Rounded.Lightbulb, style = CtButtonStyle.Secondary, enabled = !session.replyDue && answer == null && board.movable != null, onClick = onShowAnswer, modifier = Modifier.fillMaxWidth())
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                        CtButton(text = "Again", icon = Icons.Rounded.Refresh, style = CtButtonStyle.Secondary, onClick = onAgain, modifier = Modifier.weight(1f))
                        CtButton(text = "Next line", icon = Icons.AutoMirrored.Rounded.ArrowForward, onClick = onNext, modifier = Modifier.weight(1f))
                    }
                }
                CtSectionHeader(title = "Lines")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.xs)) {
                    drill.lines.forEach { line ->
                        CtChip(text = line.variation, color = CtColors.Violet, selected = line.id == drill.line.id, onClick = { onPickLine(line.id) })
                    }
                }
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}

/** "It comes back tomorrow", "in 3 days". */
internal fun comesBack(gapMs: Long): String {
    val days = TimeUnit.MILLISECONDS.toDays(gapMs + TimeUnit.HOURS.toMillis(1)).toInt()
    return when {
        days <= 0 -> "It comes back later today."
        days == 1 -> "It comes back tomorrow."
        else -> "It comes back in $days days."
    }
}

