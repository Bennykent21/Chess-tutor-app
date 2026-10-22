package com.chesstutor.app.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.rounded.LastPage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.SanFormatter
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.engine.EngineLine

// ------------------------------------------------------------------ pure helpers

/**
 * An engine line in SAN with move numbers, for example "12. Nf3 Nc6 13. Bb5" or
 * "12... Nc6 13. Bb5". Stops at [maxPlies] or at the first move that is not legal.
 */
fun lineSan(position: Position, uciMoves: List<String>, maxPlies: Int = 10): String {
    val parts = mutableListOf<String>()
    var current = position
    uciMoves.take(maxPlies).forEachIndexed { index, uci ->
        val move = runCatching { Move.fromUci(uci) }.getOrNull() ?: return parts.joinToString(" ")
        val legal = LegalMoveGenerator.generateLegalMoves(current).firstOrNull { it.uci == move.uci } ?: return parts.joinToString(" ")
        val white = current.sideToMove == PieceColor.WHITE
        when {
            white -> parts += "${current.fullmoveNumber}."
            index == 0 -> parts += "${current.fullmoveNumber}..."
        }
        parts += SanFormatter.format(current, legal)
        current = LegalMoveGenerator.makeMove(current, legal)
    }
    return parts.joinToString(" ")
}

/** Adds the arrow, or removes it if the same arrow is already drawn (drawing twice erases). */
fun toggleArrow(arrows: List<BoardArrow>, arrow: BoardArrow): List<BoardArrow> =
    if (arrows.any { it.from == arrow.from && it.to == arrow.to }) arrows.filterNot { it.from == arrow.from && it.to == arrow.to }
    else arrows + arrow

fun toggleRing(rings: List<SquareRing>, ring: SquareRing): List<SquareRing> =
    if (rings.any { it.square == ring.square }) rings.filterNot { it.square == ring.square } else rings + ring

// ------------------------------------------------------------------ move list with variations

/**
 * The analysis move list (plan section 4.2): the main line, with variations in brackets in
 * a lighter style. Tap any move to jump there; the current move is gold.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalysisMoveList(view: AnalysisView, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .clip(CtShapes.CardSmall)
            .background(CtColors.Surface)
            .padding(CtSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.xxs),
        verticalArrangement = Arrangement.spacedBy(CtSpacing.xxs),
    ) {
        if (view.tokens.isEmpty()) {
            Text("No moves yet. Play a move on the board.", style = CtType.BodySmall, modifier = Modifier.padding(CtSpacing.xs))
        }
        view.tokens.forEach { token ->
            when (token) {
                is NotationToken.VariationStart -> Text("(", style = CtType.BodySmall, modifier = Modifier.align(Alignment.CenterVertically))
                is NotationToken.VariationEnd -> Text(")", style = CtType.BodySmall, modifier = Modifier.align(Alignment.CenterVertically))
                is NotationToken.MoveToken -> {
                    val current = token.nodeId == view.currentId
                    val mainLine = token.depth == 0
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .clip(CtShapes.Chip)
                            .background(if (current) CtColors.Gold else CtColors.Surface)
                            .ctPressable(role = Role.Button, onClickLabel = "Go to this move") { onSelect(token.nodeId) }
                            .semantics(mergeDescendants = true) {
                                contentDescription = listOfNotNull(token.numberLabel, token.san, if (mainLine) null else "side line").joinToString(" ")
                                if (current) stateDescription = "Current move"
                            }
                            .padding(horizontal = CtSpacing.xs, vertical = CtSpacing.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val numberColor = if (current) CtColors.OnGold else CtColors.Text3
                        val moveColor = when {
                            current -> CtColors.OnGold
                            mainLine -> CtColors.Text
                            else -> CtColors.Text2
                        }
                        token.numberLabel?.let { Text("$it ", style = CtType.Caption.copy(color = numberColor)) }
                        Text(token.san, style = (if (mainLine) CtType.Label else CtType.BodySmall).copy(color = moveColor))
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ engine lines

/**
 * Engine lines (MultiPV, plan section 4.4): each line's score and its moves in SAN. Tapping
 * a line plays its first move.
 */
@Composable
fun EngineLinesPanel(
    position: Position,
    lines: List<EngineLine>,
    onPlayLine: (EngineLine) -> Unit,
    modifier: Modifier = Modifier,
    engineName: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CtShapes.CardSmall)
            .background(CtColors.Surface)
            .padding(CtSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CtSpacing.xs),
    ) {
        val depth = lines.firstOrNull()?.depth
        val heading = listOfNotNull(engineName ?: "Engine", depth?.let { "depth $it" }).joinToString(" · ")
        Text(heading, style = CtType.Caption)
        if (lines.isEmpty()) {
            Text("Thinking…", style = CtType.BodySmall)
        }
        lines.forEach { line ->
            val score = EvalMath.label(line.centipawns, line.mateInMoves)
            val san = lineSan(position, line.moves)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CtShapes.Chip)
                    .ctPressable(role = Role.Button, onClickLabel = "Play the first move of this line") { onPlayLine(line) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Line ${line.rank}: ${EvalMath.spoken(line.centipawns, line.mateInMoves)}. $san"
                    }
                    .padding(vertical = CtSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
            ) {
                val whiteBetter = (line.mateInMoves ?: 0) > 0 || (line.mateInMoves == null && (line.centipawns ?: 0) >= 0)
                Box(
                    modifier = Modifier
                        .widthIn(min = CtSpacing.xxl + CtSpacing.lg)
                        .clip(CtShapes.Chip)
                        .background(if (whiteBetter) CtColors.Text else CtColors.BgDeep)
                        .padding(horizontal = CtSpacing.sm, vertical = CtSpacing.xxs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(score, style = CtType.Label.copy(color = if (whiteBetter) CtColors.BgDeep else CtColors.Text), maxLines = 1)
                }
                Text(san, style = CtType.BodySmall.copy(color = CtColors.Text), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ------------------------------------------------------------------ toolbar

/** Back and forward through an analysis, and the arrow-drawing switch. */
@Composable
fun AnalysisToolbar(
    view: AnalysisView,
    drawing: Boolean,
    onStart: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onEnd: () -> Unit,
    onToggleDrawing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton(Icons.Rounded.FirstPage, "Go to the start", view.canGoBack, onStart)
        ToolButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Back one move", view.canGoBack, onBack)
        ToolButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Forward one move", view.canGoForward, onForward)
        ToolButton(Icons.Rounded.LastPage, "Go to the end", view.canGoForward, onEnd)
        ToolButton(Icons.Rounded.Draw, if (drawing) "Stop drawing arrows" else "Draw arrows", true, onToggleDrawing, active = drawing)
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, active: Boolean = false) {
    Box(
        modifier = Modifier
            .size(CtSpacing.touchTarget + CtSpacing.sm)
            .clip(CtShapes.Pill)
            .background(if (active) CtColors.Lime else CtColors.Surface)
            .ctPressable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val tint = when {
            active -> CtColors.OnLime
            enabled -> CtColors.Text
            else -> CtColors.Text3
        }
        Icon(icon, contentDescription = null, tint = tint)
    }
}
