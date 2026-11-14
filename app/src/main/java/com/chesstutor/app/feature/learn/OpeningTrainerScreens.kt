package com.chesstutor.app.feature.learn

import androidx.compose.runtime.remember
import androidx.compose.material.icons.rounded.Visibility
import com.chesstutor.app.board.moveListOf
import com.chesstutor.app.board.BoardMoveList
import com.chesstutor.app.design.CtControl
import com.chesstutor.app.design.CtTutor
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
fun OpeningTrainerScreen(
    state: TrainerState,
    onOpen: (openingId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** The taught openings (Phase 10): each replaces the old single lines of the same opening. */
    repertoires: List<RepertoireBookUi> = emptyList(),
    onOpenRepertoire: (id: String) -> Unit = {},
    /** Reviews every taught opening's due cards, mixed. */
    onReviewRepertoires: () -> Unit = {},
    /** One side only, with the side as the title (Train, Phase 12); null shows both behind a switch. */
    fixedSide: PieceColor? = null,
) {
    val taught = repertoires.map { it.book.id }.toSet()
    val legacy = state.openings.filter { it.opening.id !in taught && it.lines.isNotEmpty() }
    val firstDue = legacy.firstOrNull { it.due > 0 }
    val dueRepertoire = repertoires.firstOrNull { it.due > 0 }
    // Open on the side that has work due, so the list starts where the work is.
    var chosen by rememberSaveable { mutableStateOf(dueRepertoire?.book?.side ?: firstDue?.opening?.side ?: PieceColor.WHITE) }
    val side = fixedSide ?: chosen
    val repertoireDue = repertoires.filter { it.book.side == side || fixedSide == null }.sumOf { it.due }
    val due = legacy.filter { it.opening.side == side || fixedSide == null }.sumOf { it.due }
    CtActionPage(
        title = when (fixedSide) {
            PieceColor.WHITE -> "As White"
            PieceColor.BLACK -> "As Black"
            null -> "Opening trainer"
        },
        onBack = onBack,
        modifier = modifier,
        bottom = {
            if (repertoireDue > 0) {
                CtButton(text = "Review $repertoireDue", onClick = onReviewRepertoires, modifier = Modifier.weight(1f))
            } else if (firstDue != null) {
                CtButton(text = "Review $due due line${if (due == 1) "" else "s"}", onClick = { onOpen(firstDue.opening.id) }, modifier = Modifier.weight(1f))
            }
        },
    ) {
        // Read when the app starts, so this is at most a blink: nothing to announce.
        if (state.loading) return@CtActionPage
        if (fixedSide == null) {
            CtSegmented(
                options = listOf(PieceColor.WHITE, PieceColor.BLACK),
                selected = side,
                label = { if (it == PieceColor.WHITE) "As White" else "As Black" },
                onSelect = { chosen = it },
            )
        }
        val shown = legacy.filter { it.opening.side == side }
        Column(Modifier.padding(top = CtSpacing.sm)) {
            repertoires.filter { it.book.side == side }.forEach { item -> RepertoireRow(item, onOpen = { onOpenRepertoire(item.book.id) }) }
            shown.forEach { item -> TrainerRow(item, onOpen = { onOpen(item.opening.id) }) }
        }
    }
}

@Composable
private fun RepertoireRow(item: RepertoireBookUi, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .ctPressable(onClickLabel = "Open ${item.book.title}", onClick = onOpen)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = CtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.book.title, style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${item.introduced} of ${item.total}", style = CtType.Caption)
            }
            CtProgressBar(progress = if (item.total == 0) 0f else item.introduced.toFloat() / item.total, height = 3.dp, modifier = Modifier.fillMaxWidth())
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
}

@Composable
private fun TrainerRow(item: TrainerOpeningState, onOpen: () -> Unit) {
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
    /** Opens the Learn course that explains this opening, when there is one. */
    onLearn: (() -> Unit)? = null,
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
        zone = {
            CtTutor(settings.mascot, mood = mood, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(message, style = CtType.Chat)
            }
            CtProgressBar(progress = if (total == 0) 0f else made.toFloat() / total, height = 3.dp, modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
            ) {
                drill.lines.forEach { line ->
                    CtChip(text = line.variation, selected = line.id == drill.line.id, onClick = { onPickLine(line.id) })
                }
            }
        },
        under = {
            BoardMoveList(entries = remember(board.version) { moveListOf(board.start, board.moves) }, currentPly = board.moves.size)
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
                soundsOtherMoves = true,
            )
        },
        controls = {
            if (result == null) {
                CtControl(Icons.Rounded.Visibility, "Show me", enabled = !session.replyDue && answer == null && movable != null, onClick = onShowAnswer)
                onLearn?.let { CtControl(Icons.Rounded.Lightbulb, "Learn the ideas", onClick = it) }
            } else {
                CtButton(text = "Again", style = CtButtonStyle.Secondary, onClick = onAgain, modifier = Modifier.weight(1f))
                CtButton(text = "Next line", onClick = onNext, modifier = Modifier.weight(1f))
            }
        },
    )
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
