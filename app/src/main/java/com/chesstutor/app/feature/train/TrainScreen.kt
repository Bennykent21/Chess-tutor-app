package com.chesstutor.app.feature.train

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.board.BoardView
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.design.CtArtTile
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtMetalIcon
import com.chesstutor.app.design.CtScene
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtScreenTitle
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.feature.learn.RepertoireState
import com.chesstutor.app.feature.learn.TrainerState

/** What the Train tab can open. */
data class TrainActions(
    /** Today's mixed review of every opening. */
    val onToday: () -> Unit,
    /** The openings of one side. */
    val onSide: (PieceColor) -> Unit,
    /** An opening played out against Stockfish (Opening Battle). */
    val onVsStockfish: () -> Unit,
)

/**
 * The Train tab (Phase 12, mock-up round 3): every kind of practice in one place. Today mixes
 * what is due; then the openings as White and as Black, position sets for endgames and
 * middlegames (step 3 of the plan, so marked "Soon"), and openings played out against Stockfish.
 */
@Composable
fun TrainScreen(
    repertoires: RepertoireState,
    trainer: TrainerState,
    theme: BoardTheme,
    actions: TrainActions,
    modifier: Modifier = Modifier,
) {
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = CtSpacing.xl)) {
            CtScreenTitle(title = "Train")
            Column(Modifier.padding(horizontal = CtSpacing.gutter), verticalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                Today(repertoires, theme, actions.onToday)
                Text("Openings", style = CtType.Label.copy(color = CtColors.Text2), modifier = Modifier.padding(top = CtSpacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                    PieceColor.entries.forEach { side ->
                        val books = repertoires.books.filter { it.book.side == side }
                        val others = trainer.openings.count { it.opening.side == side && it.opening.id !in books.map { b -> b.book.id } && it.lines.isNotEmpty() }
                        CtArtTile(
                            title = if (side == PieceColor.WHITE) "As White" else "As Black",
                            scene = if (side == PieceColor.WHITE) CtScene.OPENINGS else CtScene.POSITION,
                            modifier = Modifier.weight(1f).height(112.dp),
                            subtitle = books.joinToString { it.book.title }.ifEmpty { "$others openings" },
                            progress = books.takeIf { it.isNotEmpty() }?.map { it.readiness }?.average()?.toFloat(),
                            onClick = { actions.onSide(side) },
                            onClickLabel = "Openings as ${if (side == PieceColor.WHITE) "White" else "Black"}",
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                    CtArtTile(title = "Endgames", scene = CtScene.ENDGAMES, modifier = Modifier.weight(1f).height(112.dp), subtitle = "Win this · soon")
                    CtArtTile(title = "Middlegames", scene = CtScene.MIDDLEGAME, modifier = Modifier.weight(1f).height(112.dp), subtitle = "Convert this · soon")
                }
                CtListRow(
                    title = "Openings vs Stockfish",
                    icon = null,
                    onClick = actions.onVsStockfish,
                    leading = { CtMetalIcon(Icons.Rounded.SportsEsports, size = 36.dp) },
                )
            }
        }
    }
}

/** What is due today in every opening: how many, three of the positions, and Start. */
@Composable
private fun Today(repertoires: RepertoireState, theme: BoardTheme, onStart: () -> Unit) {
    val due = repertoires.dueTotal
    Column(
        modifier = Modifier.fillMaxWidth().clip(CtShapes.Card).background(CtColors.Surface).padding(CtSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = if (due > 0) "$due positions due today" else "Nothing due today" }) {
                Text("Today", style = CtType.Label.copy(color = CtColors.Text2))
                if (due > 0) {
                    Text("$due", style = CtType.BigNumber)
                    Text("About ${minutesFor(due)} minute${if (minutesFor(due) == 1) "" else "s"}", style = CtType.Caption.copy(color = CtColors.Text2))
                } else {
                    Text("Nothing due", style = CtType.Title, modifier = Modifier.padding(top = CtSpacing.xs))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repertoires.dueSamples.forEach { (fen, side) ->
                    val position = remember(fen) { Position.tryFromFen(fen).getOrNull() }
                    if (position != null) {
                        Box(Modifier.size(54.dp).clip(CtShapes.Chip)) {
                            BoardView(position, theme, whiteAtBottom = side == PieceColor.WHITE, showCoordinates = false, showLegalDots = false)
                        }
                    }
                }
            }
        }
        if (due > 0) CtButton(text = "Start", onClick = onStart, modifier = Modifier.fillMaxWidth())
    }
}

/** About thirty seconds a position, at least a minute. */
internal fun minutesFor(positions: Int): Int = ((positions + 1) / 2).coerceAtLeast(1)
