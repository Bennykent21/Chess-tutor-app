package com.chesstutor.app.feature.puzzles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtCoachStrip
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar

/** What the mistakes review can do. */
data class MistakeReviewActions(
    val onHint: () -> Unit = {},
    val onShowAnswer: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onFeedback: (BoardFeedback) -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * Puzzles > Mistakes review (plan 6.3): positions from the player's games that are due,
 * on the new board with the coach strip, a hint (the piece, then the move) and the solution.
 */
@Composable
fun MistakeReviewScreen(
    ui: MistakeReviewState,
    session: BoardSession,
    settings: AppSettings,
    actions: MistakeReviewActions,
    modifier: Modifier = Modifier,
) {
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtTopBar(
                title = "Mistakes review",
                subtitle = if (ui.total > 0 && !ui.finished) "Position ${ui.number} of ${ui.total}" else "Positions from your games",
                onBack = actions.onBack,
            )
            Column(Modifier.padding(horizontal = CtSpacing.gutter), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                when {
                    ui.loading -> Unit
                    ui.finished -> CtEmptyState(
                        icon = Icons.Rounded.CheckCircle,
                        title = if (ui.total == 0) "Nothing due" else "All caught up",
                        body = if (ui.total == 0) {
                            "Positions from your games come back here when they are due. Add more from a game review with \"Practise this mistake\"."
                        } else {
                            "${ui.cleanCount} of ${ui.total} found first time without a hint. The rest come back soon."
                        },
                        actionLabel = "Done",
                        onAction = actions.onBack,
                    )
                    else -> Position(ui, session, settings, actions)
                }
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}

@Composable
private fun Position(ui: MistakeReviewState, session: BoardSession, settings: AppSettings, actions: MistakeReviewActions) {
    val whiteAtBottom = remember(ui.item?.id) {
        ui.item?.fen?.let { Position.tryFromFen(it).getOrNull()?.sideToMove } != PieceColor.BLACK
    }
    val hint = ui.hint
    val from = hint?.from?.let { runCatching { Square.fromAlgebraic(it) }.getOrNull() }
    val to = hint?.to?.let { runCatching { Square.fromAlgebraic(it) }.getOrNull() }
    CtCoachStrip(mascot = settings.mascot, text = ui.message, modifier = Modifier.fillMaxWidth())
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
    if (ui.done) {
        CtButton(text = if (ui.number < ui.total) "Next position" else "Finish", icon = Icons.AutoMirrored.Rounded.ArrowForward, onClick = actions.onNext, modifier = Modifier.fillMaxWidth())
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
            CtButton(text = "Hint", icon = Icons.Rounded.Lightbulb, style = CtButtonStyle.Secondary, onClick = actions.onHint, modifier = Modifier.weight(1f))
            CtButton(text = "Solution", icon = Icons.Rounded.Visibility, style = CtButtonStyle.Secondary, onClick = actions.onShowAnswer, modifier = Modifier.weight(1f))
        }
    }
}
