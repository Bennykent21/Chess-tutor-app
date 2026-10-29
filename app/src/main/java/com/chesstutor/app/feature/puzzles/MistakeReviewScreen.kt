package com.chesstutor.app.feature.puzzles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.chesstutor.app.board.BoardArrow
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardHost
import com.chesstutor.app.board.BoardSession
import com.chesstutor.app.board.MarkKind
import com.chesstutor.app.board.SquareRing
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.Square
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtBoardScreen
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtCoachStrip
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtIconButton
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.MascotMood

/** What the mistakes review can do. */
data class MistakeReviewActions(
    val onHint: () -> Unit = {},
    val onShowAnswer: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onFeedback: (BoardFeedback) -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * Puzzles > Mistakes review (plan 6.3, mock-up round 17): positions from the player's games
 * that are due, on the fixed board like the puzzle player, with the coach below it, a hint
 * (the piece, then the move) and the solution.
 */
@Composable
fun MistakeReviewScreen(
    ui: MistakeReviewState,
    session: BoardSession,
    settings: AppSettings,
    actions: MistakeReviewActions,
    modifier: Modifier = Modifier,
) {
    if (ui.loading || ui.finished) {
        CtScreenBackground(modifier) {
            Column(Modifier.fillMaxSize()) {
                CtTopBar(title = "Mistakes review", onBack = actions.onBack)
                if (ui.finished) {
                    CtEmptyState(
                        icon = Icons.Rounded.CheckCircle,
                        title = if (ui.total == 0) "Nothing due" else "All caught up",
                        body = if (ui.total == 0) {
                            "Positions from your games come back here when they are due. Add more from a game review with \"Practise\"."
                        } else {
                            "${ui.cleanCount} of ${ui.total} found first time without a hint. The rest come back soon."
                        },
                        actionLabel = "Done",
                        onAction = actions.onBack,
                    )
                }
            }
        }
        return
    }
    val whiteAtBottom = remember(ui.item?.id) {
        ui.item?.fen?.let { Position.tryFromFen(it).getOrNull()?.sideToMove } != PieceColor.BLACK
    }
    val hint = ui.hint
    val from = hint?.from?.let { runCatching { Square.fromAlgebraic(it) }.getOrNull() }
    val to = hint?.to?.let { runCatching { Square.fromAlgebraic(it) }.getOrNull() }
    CtBoardScreen(
        title = "Mistakes review",
        onBack = actions.onBack,
        modifier = modifier,
        status = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (ui.done) "Found it" else "Find the better move", style = CtType.TitleSmall)
                    Text("From your games", style = CtType.Caption)
                }
                Text("Position ${ui.number} of ${ui.total}", style = CtType.Label.copy(color = com.chesstutor.app.design.CtColors.Text2))
            }
        },
        board = {
            BoardHost(
                session = session,
                theme = settings.boardTheme,
                whiteAtBottom = whiteAtBottom,
                showCoordinates = settings.showCoordinates,
                showLegalDots = settings.showLegalDots,
                autoQueen = settings.autoQueen,
                arrows = if (from != null && to != null) listOf(BoardArrow(from, to, MarkKind.HINT)) else emptyList(),
                rings = if (from != null && to == null) listOf(SquareRing(from, MarkKind.HINT)) else emptyList(),
                onFeedback = actions.onFeedback,
            )
        },
        controls = {
            if (ui.done) {
                CtButton(text = if (ui.number < ui.total) "Next position" else "Finish", onClick = actions.onNext, modifier = Modifier.weight(1f))
            } else {
                CtIconButton(icon = Icons.Rounded.Lightbulb, contentDescription = "Hint", onClick = actions.onHint)
                CtButton(text = "Solution", style = CtButtonStyle.Secondary, onClick = actions.onShowAnswer, modifier = Modifier.weight(1f))
            }
        },
    ) {
        CtCoachStrip(
            mascot = settings.mascot,
            text = ui.message,
            mood = when {
                ui.done -> MascotMood.PROUD
                ui.wrong -> MascotMood.KIND
                hint != null -> MascotMood.THINKING
                else -> MascotMood.READY
            },
            modifier = Modifier.fillMaxWidth().padding(top = CtSpacing.xs),
        )
    }
}

