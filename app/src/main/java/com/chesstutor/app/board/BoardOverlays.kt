package com.chesstutor.app.board

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.SanFormatter
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.LocalReduceMotion
import com.chesstutor.app.design.ctPressable
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

// ------------------------------------------------------------------ eval bar

/** Scores are from White's side, like every engine result in the app. */
object EvalMath {
    /** White's share of the bar: 0.5 when level, near 1 or 0 when winning, exactly at mate. */
    fun whiteShare(centipawns: Int?, mateIn: Int?): Float {
        if (mateIn != null) return if (mateIn > 0) 1f else if (mateIn < 0) 0f else 0.5f
        val cp = centipawns ?: return 0.5f
        // A logistic curve: +-1 pawn is about 60:40, +-4 pawns about 83:17.
        val share = 1.0 / (1.0 + exp(-0.004 * cp))
        return share.toFloat().coerceIn(0.03f, 0.97f)
    }

    /** "+0.3", "-1.2", "0.0", "M3" (White mates) or "-M2" (Black mates). */
    fun label(centipawns: Int?, mateIn: Int?): String {
        if (mateIn != null) return if (mateIn >= 0) "M${abs(mateIn)}" else "-M${abs(mateIn)}"
        val cp = centipawns ?: return "…"
        val pawns = (cp / 10.0).roundToInt() / 10.0
        return when {
            pawns > 0 -> "+$pawns"
            pawns < 0 -> "$pawns"
            else -> "0.0"
        }
    }

    fun spoken(centipawns: Int?, mateIn: Int?): String = when {
        mateIn != null && mateIn > 0 -> "White mates in $mateIn"
        mateIn != null && mateIn < 0 -> "Black mates in ${abs(mateIn)}"
        centipawns == null -> "No evaluation yet"
        else -> "Evaluation ${label(centipawns, null)} pawns for White"
    }
}

/**
 * Vertical eval bar beside the board (plan section 4.2). White's share grows from White's
 * side of the board. It glides to new values, or snaps with "Reduce motion" on.
 */
@Composable
fun BoardEvalBar(
    centipawns: Int?,
    mateIn: Int?,
    whiteAtBottom: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val target = EvalMath.whiteShare(centipawns, mateIn)
    val share by animateFloatAsState(target, tween(if (reduceMotion) 0 else 450), label = "evalShare")
    val whiteLeads = target >= 0.5f
    val label = EvalMath.label(centipawns, mateIn)
    Box(
        modifier = modifier
            .width(EVAL_BAR_WIDTH)
            .fillMaxHeight()
            .clip(CtShapes.Chip)
            .background(CtColors.BgDeep)
            .border(1.dp, CtColors.Line, CtShapes.Chip)
            .semantics { contentDescription = EvalMath.spoken(centipawns, mateIn) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(share)
                .align(if (whiteAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
                .background(CtColors.Text),
        )
        // The score sits on the leading side's colour, at that side's end of the bar.
        val atBottom = whiteLeads == whiteAtBottom
        Text(
            text = label.removePrefix("+").removePrefix("-"),
            style = CtType.Caption.copy(color = if (whiteLeads) CtColors.BgDeep else CtColors.Text),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(if (atBottom) Alignment.BottomCenter else Alignment.TopCenter)
                .padding(vertical = CtSpacing.xs),
        )
    }
}

/** Eval bar width; [BoardWithEvalBar] leaves room for it. */
val EVAL_BAR_WIDTH = 26.dp

/**
 * The board with an optional eval bar to its left, sized from the available width (the
 * board stays square and the bar matches its height). Use this instead of intrinsic sizes:
 * both the board and the bar measure with constraints.
 */
@Composable
fun BoardWithEvalBar(
    modifier: Modifier = Modifier,
    evalBar: (@Composable (Modifier) -> Unit)? = null,
    board: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gap = CtSpacing.sm
        val boardSize = if (evalBar != null) maxWidth - EVAL_BAR_WIDTH - gap else maxWidth
        Row(
            modifier = Modifier.height(boardSize),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            evalBar?.invoke(Modifier.fillMaxHeight())
            board(Modifier.size(boardSize))
        }
    }
}

// ------------------------------------------------------------------ clocks

/** "5:00", "1:02:03", and tenths below 20 seconds ("9.4") when every tenth counts. */
fun formatClock(millis: Long): String {
    val clamped = millis.coerceAtLeast(0)
    if (clamped < 20_000) {
        val tenths = clamped / 100
        return "${tenths / 10}.${tenths % 10}"
    }
    val totalSeconds = clamped / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}

/** A player's clock. The running clock is gold; low time turns it coral. */
@Composable
fun BoardClock(millis: Long, running: Boolean, modifier: Modifier = Modifier) {
    val low = millis < 20_000
    val background = when {
        running && low -> CtColors.Coral
        running -> CtColors.Gold
        else -> CtColors.Surface
    }
    val color = when {
        running && low -> CtColors.OnCoral
        running -> CtColors.OnGold
        else -> CtColors.Text
    }
    Text(
        text = formatClock(millis),
        style = CtType.Number.copy(color = color),
        modifier = modifier
            .clip(CtShapes.Chip)
            .background(background)
            .padding(horizontal = CtSpacing.md, vertical = CtSpacing.xs),
    )
}

// ------------------------------------------------------------------ move list

/** One move in a move list: its ply (1 = White's first move), SAN, and a "12." number for White. */
data class MoveListEntry(val ply: Int, val san: String, val numberLabel: String?)

/** SAN for each move from [start], stopping at the first move that is not legal. */
fun moveListOf(start: Position, moves: List<Move>): List<MoveListEntry> {
    val entries = mutableListOf<MoveListEntry>()
    var position = start
    // A game starting with Black to move begins at an even ply ("1... e5").
    val firstPly = if (start.sideToMove == PieceColor.WHITE) 1 else 2
    moves.forEachIndexed { index, move ->
        val legal = LegalMoveGenerator.generateLegalMoves(position).firstOrNull { it.uci == move.uci } ?: return entries
        val ply = firstPly + index
        val number = (ply + 1) / 2 + start.fullmoveNumber - 1
        val label = when {
            ply % 2 == 1 -> "$number."
            index == 0 -> "$number..."
            else -> null
        }
        entries += MoveListEntry(ply, SanFormatter.format(position, legal), label)
        position = LegalMoveGenerator.makeMove(position, legal)
    }
    return entries
}

/**
 * Horizontal move list (plan section 4.2). The current move is highlighted and kept in view.
 * With [onSelect] set, each move can be tapped to jump to it.
 */
@Composable
fun BoardMoveList(
    entries: List<MoveListEntry>,
    currentPly: Int,
    modifier: Modifier = Modifier,
    onSelect: ((Int) -> Unit)? = null,
) {
    val scroll = rememberScrollState()
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(currentPly, entries.size) {
        // Keep the newest move in view; stepping back through history leaves the scroll alone.
        if (entries.isNotEmpty() && currentPly == entries.last().ply) {
            if (reduceMotion) scroll.scrollTo(scroll.maxValue) else scroll.animateScrollTo(scroll.maxValue)
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CtShapes.CardSmall)
            .background(CtColors.Surface)
            .horizontalScroll(scroll)
            .padding(horizontal = CtSpacing.sm, vertical = CtSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entries.isEmpty()) {
            Text("No moves yet", style = CtType.BodySmall, modifier = Modifier.padding(CtSpacing.sm))
        }
        entries.forEach { entry ->
            entry.numberLabel?.let {
                Text(it, style = CtType.Caption, modifier = Modifier.padding(start = CtSpacing.xs))
            }
            val current = entry.ply == currentPly
            val side = if (entry.ply % 2 == 1) "White" else "Black"
            Text(
                text = entry.san,
                style = CtType.Label.copy(color = if (current) CtColors.OnGold else CtColors.Text),
                modifier = Modifier
                    .clip(CtShapes.Chip)
                    .background(if (current) CtColors.Gold else CtColors.Surface)
                    .then(
                        if (onSelect != null) Modifier.ctPressable(role = Role.Button, onClickLabel = "Go to this move") { onSelect(entry.ply) }
                        else Modifier
                    )
                    .semantics { contentDescription = "$side, ${entry.san}" + if (current) ", current move" else "" }
                    .padding(horizontal = CtSpacing.sm, vertical = CtSpacing.xs),
            )
        }
    }
}
