package com.chesstutor.app.feature.account

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardView
import com.chesstutor.app.board.moveListOf
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtConfirmDialog
import com.chesstutor.app.design.CtIconBubble
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.PgnFormatter
import com.chesstutor.app.domain.account.AnalysisSummary
import com.chesstutor.app.domain.account.GameOutcome
import com.chesstutor.app.domain.account.GameSummary
import kotlinx.coroutines.launch

/** What the game page can do. */
data class GameDetailActions(
    /** Opens the game review (analysing the game first if it has not been). */
    val onReview: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * One saved game (plan section 6.4): result, players, opening and where each side left the
 * book, accuracy once analysed, the final position and the moves, with review, copy PGN
 * and delete.
 */
@Composable
fun GameDetailScreen(
    game: GameSummary?,
    settings: AppSettings,
    actions: GameDetailActions,
    modifier: Modifier = Modifier,
    /** The finished analysis, if the game has one. */
    analysis: AnalysisSummary? = null,
) {
    CtScreenBackground(modifier) {
        if (game == null) {
            Column(Modifier.fillMaxSize()) { CtTopBar(title = "Game", subtitle = "This game is no longer saved", onBack = actions.onBack) }
            return@CtScreenBackground
        }
        var confirmDelete by rememberSaveable { mutableStateOf(false) }
        var copied by rememberSaveable { mutableStateOf(false) }
        val clipboard = LocalClipboard.current
        val copyScope = rememberCoroutineScope()
        val start = remember(game.id) { game.startFen ?: Position.STARTING_FEN }
        val finalPosition = remember(game.id) { runCatching { Position.fromFen(game.record.finalFen) }.getOrElse { Position.fromFen(start) } }
        val moveText = remember(game.id) { movesText(start, game.moves) }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtTopBar(title = "vs ${game.opponent}", subtitle = detailLine(game), onBack = actions.onBack)
            Column(
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
            ) {
                CtCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutcomeBadge(game.outcome)
                        Spacer(Modifier.width(CtSpacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(resultTitle(game), style = CtType.Title)
                            Text(resultDetail(game), style = CtType.BodySmall)
                        }
                    }
                }

                BoardView(
                    position = finalPosition,
                    theme = settings.boardTheme,
                    whiteAtBottom = game.userColor == PieceColor.WHITE,
                    showCoordinates = settings.showCoordinates,
                    showLegalDots = false,
                )

                analysis?.let { a ->
                    CtCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Accuracy", style = CtType.Label)
                        Text(
                            "You ${accuracyText(a.accuracyFor(game.userColor))} · ${game.opponent} ${accuracyText(a.accuracyFor(game.userColor.opposite()))}",
                            style = CtType.Title,
                        )
                    }
                }
                CtButton(
                    text = if (analysis == null) "Review with Stockfish" else "Open the review",
                    icon = Icons.Rounded.Insights,
                    onClick = actions.onReview,
                    enabled = game.moves.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )

                CtSectionHeader(title = "Opening")
                CtCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CtIconBubble(icon = Icons.AutoMirrored.Rounded.MenuBook, tint = CtColors.Teal, size = 40.dp)
                        Spacer(Modifier.width(CtSpacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(game.openingName ?: "Not named", style = CtType.TitleSmall)
                            game.opening?.eco?.let { Text("ECO $it", style = CtType.Caption) }
                            Text(bookLine(game), style = CtType.BodySmall)
                        }
                    }
                }

                if (moveText.isNotBlank()) {
                    CtSectionHeader(title = "Moves")
                    CtCard(modifier = Modifier.fillMaxWidth()) {
                        Text(moveText, style = CtType.BodySmall)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    CtButton(
                        text = if (copied) "Copied" else "Copy PGN",
                        icon = Icons.Rounded.ContentCopy,
                        style = CtButtonStyle.Secondary,
                        onClick = {
                            val pgn = PgnFormatter.reconstructPgnFromGameRecord(game.record)
                            copyScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("PGN", pgn))) }
                            copied = true
                        },
                        modifier = Modifier.weight(1f),
                    )
                    CtButton(
                        text = "Delete",
                        icon = Icons.Rounded.Delete,
                        style = CtButtonStyle.Secondary,
                        onClick = { confirmDelete = true },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (game.record.incompleteParse) {
                    CtChip(text = "Some moves could not be read from this game", color = CtColors.Coral)
                }
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }

        if (confirmDelete) {
            CtConfirmDialog(
                title = "Delete this game?",
                text = "It disappears from your games list. A game from Chess.com or Lichess comes back on the next sync.",
                confirmLabel = "Delete",
                onConfirm = {
                    confirmDelete = false
                    actions.onDelete()
                },
                onDismiss = { confirmDelete = false },
            )
        }
    }
}

internal fun resultTitle(game: GameSummary): String = when (game.outcome) {
    GameOutcome.WIN -> "You won"
    GameOutcome.LOSS -> "You lost"
    GameOutcome.DRAW -> "Draw"
    GameOutcome.UNKNOWN -> "No result"
}

/** "By checkmate · You played White · 34 moves". */
internal fun resultDetail(game: GameSummary): String = buildList {
    game.record.termination.trim().takeIf { it.isNotEmpty() }?.let { add("By ${it.lowercase()}") }
    add("You played ${if (game.userColor == PieceColor.WHITE) "White" else "Black"}")
    add("${game.moveNumber} move${if (game.moveNumber == 1) "" else "s"}")
}.joinToString(" · ")

/** Who left the opening book first, and on which move. */
internal fun bookLine(game: GameSummary): String {
    if (game.startFen != null) return "This game started from a set-up position, so it has no opening name."
    val ply = game.leftBookAt ?: return if (game.opening != null) "Every move was in the opening book." else "No book moves were found."
    val mover = if (ply % 2 == 0) PieceColor.WHITE else PieceColor.BLACK
    val who = if (mover == game.userColor) "You" else "Your opponent"
    return "$who left the book on move ${game.leftBookAtMove}."
}

/** "1. e4 e5 2. Nf3 Nc6 …" in SAN. */
internal fun movesText(startFen: String, moves: List<String>): String {
    val start = runCatching { Position.fromFen(startFen) }.getOrNull() ?: return ""
    val parsed = moves.mapNotNull { runCatching { Move.fromUci(it) }.getOrNull() }
    return moveListOf(start, parsed).joinToString(" ") { entry -> listOfNotNull(entry.numberLabel, entry.san).joinToString(" ") }
}

