package com.chesstutor.app.feature.train

import com.chesstutor.app.feature.play.rememberTalk
import com.chesstutor.app.feature.play.TalkLine
import com.chesstutor.app.feature.play.Talk
import com.chesstutor.app.design.CtMascotArt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardArrow
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardHost
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.board.MarkKind
import com.chesstutor.app.board.SquareRing
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Square
import com.chesstutor.app.design.CtBackBar
import com.chesstutor.app.design.CtBoardScreen
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtControl
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.train.PositionGoal
import com.chesstutor.app.domain.train.PositionKind
import com.chesstutor.app.domain.train.PositionOutcome
import com.chesstutor.app.feature.learn.BoardThumbnail
import com.chesstutor.app.feature.learn.ProgressRing
import com.chesstutor.app.data.settings.AppSettings

/** Endgames or Middlegames in Train: the sets, each with a small board and how many are won. */
@Composable
fun PositionSetsScreen(
    kind: PositionKind,
    sets: List<PositionSetUi>,
    theme: BoardTheme,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtBackBar(onBack = onBack)
            Column(Modifier.padding(horizontal = CtSpacing.gutter), verticalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                Text(if (kind == PositionKind.ENDGAME) "Endgames" else "Middlegames", style = CtType.Display)
                Column {
                    sets.forEach { ui -> SetRow(ui, theme, onClick = { onOpen(ui.set.id) }) }
                }
                Spacer(Modifier.height(CtSpacing.xl))
            }
        }
    }
}

@Composable
private fun SetRow(ui: PositionSetUi, theme: BoardTheme, onClick: () -> Unit) {
    val first = ui.set.positions.first()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 68.dp)
            .ctPressable(onClickLabel = "Play ${ui.set.title}", onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BoardThumbnail(first.fen, theme, flipped = first.side == PieceColor.BLACK)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(ui.set.title, style = CtType.TitleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${ui.total} position${if (ui.total == 1) "" else "s"}", style = CtType.Caption, maxLines = 1)
        }
        ProgressRing(ui.won, ui.total)
    }
}

/**
 * One position against Stockfish (mock-up round 4): where you are in the set, the goal in one
 * word, the board, the aim under it, and Hint, Restart and Give up. At the end, how it went and
 * the move that let it slip, with Again and Next position.
 */
@Composable
fun PositionRunScreen(
    run: PositionRunUi?,
    settings: AppSettings,
    onFeedback: (BoardFeedback) -> Unit,
    onHint: () -> Unit,
    onRestart: () -> Unit,
    onGiveUp: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (run == null) return
    CtBoardScreen(
        title = run.set.title,
        onBack = onBack,
        modifier = modifier,
        // The talking coach, as in games, puzzles and Learn drills (D97); the run's dots go under the board.
        zone = {
            val line = coachLine(run)
            val talk = rememberTalk(listOf(TalkLine("${run.index}:${run.end}:$line".hashCode(), line)), key = run.set.title)
            Talk(avatar = { CtMascotArt(settings.mascot, height = 76.dp) }, lines = talk)
        },
        board = {
            BoardHost(
                session = run.board,
                theme = settings.boardTheme,
                whiteAtBottom = run.side == PieceColor.WHITE,
                showCoordinates = settings.showCoordinates,
                showLegalDots = settings.showLegalDots,
                autoQueen = settings.autoQueen,
                arrows = listOfNotNull(
                    run.hintTo?.let { to -> run.hintFrom?.let { from -> BoardArrow(Square.fromAlgebraic(from), Square.fromAlgebraic(to), MarkKind.HINT) } },
                ),
                rings = listOfNotNull(run.hintFrom?.takeIf { run.hintTo == null }?.let { SquareRing(Square.fromAlgebraic(it), MarkKind.HINT) }),
                onFeedback = onFeedback,
                soundsOtherMoves = true,
            )
        },
        under = {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                QueueDots(run)
                if (!run.ended) {
                    Text(
                        (if (run.side == PieceColor.WHITE) "White to move" else "Black to move") + if (run.thinking && run.learnerMoves > 0) " · Stockfish is thinking" else "",
                        style = CtType.Caption.copy(color = CtColors.Text2),
                        maxLines = 1,
                    )
                }
            }
        },
        controls = {
            if (run.ended) {
                CtButton(text = "Again", onClick = onRestart, style = CtButtonStyle.Ghost)
                if (run.next != null) CtButton(text = "Next position", onClick = onNext, modifier = Modifier.weight(1f))
                else CtButton(text = "Done", onClick = onBack, modifier = Modifier.weight(1f))
            } else {
                CtControl(Icons.Rounded.Lightbulb, "Hint", enabled = !run.thinking, onClick = onHint)
                CtControl(Icons.Rounded.Replay, "Restart", onClick = onRestart)
                CtControl(Icons.Rounded.Flag, "Give up", onClick = onGiveUp)
            }
        },
    )
}

/** A dot per position of this run, and "3 of 12". */
@Composable
private fun QueueDots(run: PositionRunUi) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            run.queue.indices.take(MAX_DOTS).forEach { i ->
                val color = when {
                    i < run.index -> CtColors.Success
                    i == run.index -> CtColors.Text2
                    else -> CtColors.Track
                }
                Box(Modifier.size(8.dp).clip(CtShapes.Pill).background(color))
            }
        }
        Text("${run.index + 1} of ${run.queue.size}", style = CtType.Caption.copy(color = CtColors.Text2))
    }
}

/** What the coach says (D97): the goal and the aim while playing; how it went once over. */
private fun coachLine(run: PositionRunUi): String {
    val (word, chip, _) = wordsOf(run)
    val rest = if (run.ended) listOfNotNull(chip, underOf(run)) else listOf(run.position.aim)
    return (listOf(word) + rest).filter { it.isNotBlank() }.joinToString(". ") { it.trimEnd('.') } + "."
}

private fun wordsOf(run: PositionRunUi): Triple<String, String?, Boolean> {
    val moves = "${run.learnerMoves} move${if (run.learnerMoves == 1) "" else "s"}"
    return when (run.end) {
        null -> Triple(
            when (run.position.goal) {
                PositionGoal.WIN -> "Win"
                PositionGoal.HOLD -> "Hold"
                PositionGoal.CONVERT -> "Convert"
            },
            null, true,
        )
        PositionEnd.MATED -> Triple("Won", "Mate in $moves", true)
        PositionEnd.PROMOTED -> Triple("Won", "Promoted in $moves", true)
        PositionEnd.CONVERTED -> Triple("Won", "Converted in $moves", true)
        PositionEnd.HELD -> Triple("Held", "Held for $moves", true)
        PositionEnd.DRAWN -> Triple("Only a draw", run.slip?.let { "$it let it slip" }, false)
        PositionEnd.LOST -> Triple("Lost", run.slip?.let { "$it let it slip" }, false)
        PositionEnd.GAVE_UP -> Triple("Given up", null, false)
    }
}

private fun underOf(run: PositionRunUi): String = when {
    !run.ended -> run.position.aim
    run.outcome == PositionOutcome.SLIPPED ->
        "Back tomorrow." + (run.better?.let { " $it was the move." } ?: "")
    run.next != null -> "${run.queue.size - run.index - 1} more to go"
    else -> "Set done"
}

private const val MAX_DOTS = 16
