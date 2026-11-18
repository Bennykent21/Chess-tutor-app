package com.chesstutor.app.board

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.graphics.Color
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
    // Square and flush with the board (D92); White's share in white, Black's in black, in every theme.
    Box(
        modifier = modifier
            .width(EVAL_BAR_WIDTH)
            .fillMaxHeight()
            .background(SideTones.Black)
            .semantics { contentDescription = EvalMath.spoken(centipawns, mateIn) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(share)
                .align(if (whiteAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
                .background(SideTones.White),
        )
        // The score sits on the leading side's colour, at that side's end of the bar.
        val atBottom = whiteLeads == whiteAtBottom
        Text(
            text = label.removePrefix("+").removePrefix("-"),
            style = CtType.Caption.copy(color = if (whiteLeads) SideTones.OnWhite else SideTones.OnBlack),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(if (atBottom) Alignment.BottomCenter else Alignment.TopCenter)
                .padding(vertical = CtSpacing.xs),
        )
    }
}

/** Eval bar width; [BoardWithEvalBar] leaves room for it. */
val EVAL_BAR_WIDTH = 22.dp

/**
 * White's and Black's own colours, the same in every theme (D92): the eval bar and the clocks
 * show who is who by them, as the pieces do.
 */
object SideTones {
    val White = Color(0xFFF7F4EE)
    val OnWhite = Color(0xFF1E1B17)
    val Black = Color(0xFF1E1B17)
    val OnBlack = Color(0xFFF2EDE3)
}

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
        val boardSize = if (evalBar != null) maxWidth - EVAL_BAR_WIDTH else maxWidth
        Row(modifier = Modifier.height(boardSize)) {
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

/** A player's clock (canvas `Play.dc.html`): the running clock takes the primary colour; low time turns it red. */
@Composable
fun BoardClock(millis: Long, running: Boolean, modifier: Modifier = Modifier) {
    val low = millis < 20_000
    val background = when {
        running && low -> CtColors.Error
        running -> CtColors.Primary
        else -> CtColors.Surface
    }
    val color = when {
        running && low -> CtColors.OnError
        running -> CtColors.OnPrimary
        else -> CtColors.Text2
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
 * Horizontal move list (plan section 4.2, D104): a strip that runs out to the screen's edges past
 * the slot's [bleed], with no box around it, hairlines above and below and soft faded ends. The
 * current move is held in the centre, earlier moves to its left; with [onSelect] set, each move can
 * be tapped to jump to it.
 */
@Composable
fun BoardMoveList(
    entries: List<MoveListEntry>,
    currentPly: Int,
    modifier: Modifier = Modifier,
    onSelect: ((Int) -> Unit)? = null,
    bleed: Dp = CtSpacing.lg,
    /** Something after a move, such as its grade in a review ("??"); told whether the move is the current one. */
    after: (@Composable (ply: Int, current: Boolean) -> Unit)? = null,
) {
    val scroll = rememberScrollState()
    val reduceMotion = LocalReduceMotion.current
    val placed = remember { HashMap<Int, LayoutCoordinates>() }
    var viewport by remember { mutableStateOf<LayoutCoordinates?>(null) }
    LaunchedEffect(currentPly, entries.size) {
        // Wait for the moves to be laid out, then bring the current one to the middle.
        withFrameNanos { }
        val port = viewport ?: return@LaunchedEffect
        val move = placed[currentPly]?.takeIf { it.isAttached && port.isAttached } ?: return@LaunchedEffect
        val centre = port.localPositionOf(move, Offset.Zero).x + move.size.width / 2f
        val target = (scroll.value + centre - port.size.width / 2f).roundToInt().coerceIn(0, scroll.maxValue)
        if (reduceMotion) scroll.scrollTo(target) else scroll.animateScrollTo(target)
    }
    val line = CtColors.Line
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                // Past the slot's side padding, out to the screen's edges.
                val extra = (bleed * 2).roundToPx()
                val width = constraints.maxWidth + extra
                val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
            }
            .drawBehind {
                drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, 0.14f to Color.Black, 0.86f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        val half = maxWidth / 2
        Row(
            modifier = Modifier
                .onPlaced { viewport = it }
                .horizontalScroll(scroll)
                // Room either side so the first and the last move can sit in the middle too.
                .padding(horizontal = half, vertical = CtSpacing.xs),
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
                Row(
                    modifier = Modifier
                        .onPlaced { placed[entry.ply] = it }
                        .clip(CtShapes.Chip)
                        .background(if (current) CtColors.Gold else Color.Transparent)
                        .then(
                            if (onSelect != null) Modifier.ctPressable(role = Role.Button, onClickLabel = "Go to this move") { onSelect(entry.ply) }
                            else Modifier
                        )
                        .semantics(mergeDescendants = true) { contentDescription = "$side, ${entry.san}" + if (current) ", current move" else "" }
                        .padding(horizontal = CtSpacing.sm, vertical = CtSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = entry.san, style = CtType.Label.copy(color = if (current) CtColors.OnGold else CtColors.Text))
                    after?.invoke(entry.ply, current)
                }
            }
        }
    }
}
