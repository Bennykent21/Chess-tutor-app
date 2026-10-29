package com.chesstutor.app.feature.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardArrow
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardHost
import com.chesstutor.app.board.MarkKind
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtActionPage
import com.chesstutor.app.design.CtBoardScreen
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtDivider
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtMascotArt
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSegmented
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.MascotMood
import java.util.concurrent.TimeUnit

/**
 * The opening trainer's list (plan 6C, mock-up round 17): White's or Black's openings, each
 * with lines learned and how many are due; a button reviews the due lines first.
 */
@Composable
fun OpeningTrainerScreen(state: TrainerState, onOpen: (openingId: String) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val firstDue = state.openings.firstOrNull { it.due > 0 && it.lines.isNotEmpty() }
    // Open on the side that has lines due, so the list starts where the work is.
    var side by rememberSaveable { mutableStateOf(firstDue?.opening?.side ?: PieceColor.WHITE) }
    val due = state.openings.sumOf { it.due }
    CtActionPage(
        title = "Opening trainer",
        onBack = onBack,
        modifier = modifier,
        bottom = {
            if (firstDue != null) {
                CtButton(text = "Review $due due line${if (due == 1) "" else "s"}", onClick = { onOpen(firstDue.opening.id) }, modifier = Modifier.weight(1f))
            }
        },
    ) {
        if (state.loading) {
            CtEmptyState(icon = Icons.Rounded.Lightbulb, title = "Opening the book", body = "Reading the opening database…")
            return@CtActionPage
        }
        CtSegmented(
            options = listOf(PieceColor.WHITE, PieceColor.BLACK),
            selected = side,
            label = { if (it == PieceColor.WHITE) "As White" else "As Black" },
            onSelect = { side = it },
        )
        val shown = state.openings.filter { it.opening.side == side && it.lines.isNotEmpty() }
        Column(Modifier.padding(top = CtSpacing.sm)) {
            shown.forEachIndexed { i, item -> TrainerRow(item, divider = i < shown.lastIndex, onOpen = { onOpen(item.opening.id) }) }
        }
    }
}

@Composable
private fun TrainerRow(item: TrainerOpeningState, divider: Boolean, onOpen: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .ctPressable(onClickLabel = "Train ${item.opening.title}", onClick = onOpen)
                .semantics(mergeDescendants = true) {}
                .padding(vertical = CtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.opening.title, style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${item.learned} of ${item.lines.size}", style = CtType.Caption)
                }
                CtProgressBar(progress = item.learned.toFloat() / item.lines.size, height = 3.dp, modifier = Modifier.fillMaxWidth())
            }
            if (item.due > 0) {
                Text(
                    "${item.due} due",
                    style = CtType.Label.copy(color = CtColors.OnError),
                    modifier = Modifier.clip(CtShapes.Pill).background(CtColors.Error).padding(horizontal = 10.dp, vertical = CtSpacing.xxs),
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = CtColors.Text2, modifier = Modifier.size(20.dp))
        }
        if (divider) CtDivider()
    }
}

/**
 * One line on the fixed board (mock-up round 17): the player's moves from memory, the book's
 * replies by themselves. The coach's word sits in the row above the board, the lines below it.
 */
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
    if (drill == null) {
        CtScreenBackground(modifier) {
            Column(Modifier.fillMaxSize()) { CtTopBar(title = "Opening trainer", onBack = onBack) }
        }
        return
    }
    val session = drill.session
    val board by session.state.collectAsState()
    val (made, total) = session.progress
    // Read here, so a move on the board refreshes the coach's word above it too.
    val movable = board.movable
    val answer = session.shownAnswer
    val result = drill.result
    val message = when {
        result != null && session.isClean -> "Clean! ${comesBack(result.dueAt - result.lastPlayedAt)}"
        result != null -> "Line done with ${session.slips} slip${if (session.slips == 1) "" else "s"}. It comes back today."
        answer != null -> "The book move is shown. Play it to carry on."
        session.replyDue -> "Book reply coming…"
        else -> "Your move ($made of $total). You play ${if (drill.line.side == PieceColor.WHITE) "White" else "Black"}."
    }
    val mood = when {
        result != null && session.isClean -> MascotMood.PROUD
        result != null || answer != null -> MascotMood.KIND
        else -> MascotMood.THINKING
    }
    CtBoardScreen(
        title = drill.opening.title,
        onBack = onBack,
        modifier = modifier,
        status = {
            Row(
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
            ) {
                CtMascotArt(mascot = settings.mascot, mood = mood, height = 44.dp, modifier = Modifier.size(44.dp))
                Text(message, style = CtType.BodySmall.copy(color = CtColors.Text), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        },
        board = {
            BoardHost(
                session = session,
                theme = settings.boardTheme,
                whiteAtBottom = drill.line.side == PieceColor.WHITE,
                showCoordinates = settings.showCoordinates,
                showLegalDots = settings.showLegalDots,
                autoQueen = settings.autoQueen,
                arrows = listOfNotNull(answer?.let { BoardArrow(it.from, it.to, MarkKind.HINT) }),
                onFeedback = onFeedback,
            )
        },
        controls = {
            if (result == null) {
                CtButton(text = "Show me", style = CtButtonStyle.Secondary, enabled = !session.replyDue && answer == null && movable != null, onClick = onShowAnswer, modifier = Modifier.weight(1f))
            } else {
                CtButton(text = "Again", style = CtButtonStyle.Secondary, onClick = onAgain, modifier = Modifier.weight(1f))
                CtButton(text = "Next line", onClick = onNext, modifier = Modifier.weight(1f))
            }
        },
    ) {
        CtProgressBar(progress = if (total == 0) 0f else made.toFloat() / total, height = 3.dp, modifier = Modifier.fillMaxWidth())
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = CtSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
        ) {
            drill.lines.forEach { line ->
                CtChip(text = line.variation, selected = line.id == drill.line.id, onClick = { onPickLine(line.id) })
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
