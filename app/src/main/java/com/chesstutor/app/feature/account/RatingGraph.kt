package com.chesstutor.app.feature.account

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtMotion
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.LocalReduceMotion
import com.chesstutor.app.domain.account.RatingTrend
import kotlin.math.abs

private const val MOST_POINTS = 150

/** A flat run still gets a line in the middle, not one at full height. */
private const val SMALLEST_RANGE = 20

/** "+24", or "−12" with a true minus sign. */
internal fun changeText(change: Int): String = if (change > 0) "+$change" else "−${abs(change)}"

/**
 * A rating over time (D55): the line draws itself in, with the rating and its rise or fall above.
 * A finger on the graph reads any day. [trend] needs at least two points.
 */
@Composable
fun RatingGraph(trend: RatingTrend, now: Long, modifier: Modifier = Modifier) {
    val points = remember(trend) { trend.thinned(MOST_POINTS) }
    var touched by remember(trend) { mutableStateOf<Int?>(null) }
    val reduceMotion = LocalReduceMotion.current
    val drawn = remember(trend) { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(trend) {
        if (!reduceMotion) drawn.animateTo(1f, tween(CtMotion.DRAW_MS, easing = CtMotion.EaseOut))
    }
    val first = points.first().dateMillis
    val span = (points.last().dateMillis - first).coerceAtLeast(1L)
    val lowest = points.minOf { it.rating }
    val range = (points.maxOf { it.rating } - lowest).coerceAtLeast(1)
    val spread = range.coerceAtLeast(SMALLEST_RANGE)
    fun nearest(share: Float): Int {
        val day = first + (share * span).toLong()
        return points.indices.minBy { abs(points[it].dateMillis - day) }
    }

    val shown = points[touched ?: points.lastIndex]
    val change = trend.changeSince(now)?.takeIf { it != 0 }
    val ink = CtColors.Accent
    val rule = CtColors.Line
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${shown.rating}", style = CtType.Display, modifier = Modifier.weight(1f))
            when {
                touched != null -> Text(formatDate(shown.dateMillis), style = CtType.Caption)
                change != null -> Text(changeText(change), style = CtType.Label.copy(color = if (change > 0) CtColors.Success else CtColors.Error))
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .semantics { contentDescription = "Rating graph, now ${points.last().rating}. Hold to read a day." }
                .pointerInput(points) {
                    detectTapGestures(
                        onPress = { at ->
                            touched = nearest(at.x / size.width)
                            tryAwaitRelease()
                            touched = null
                        },
                    )
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures(
                        onDragStart = { at -> touched = nearest(at.x / size.width) },
                        onDragEnd = { touched = null },
                        onDragCancel = { touched = null },
                    ) { drag, _ -> touched = nearest((drag.position.x / size.width).coerceIn(0f, 1f)) }
                },
        ) {
            val margin = 6.dp.toPx()
            val tall = size.height - margin * 2
            fun x(i: Int) = (points[i].dateMillis - first) / span.toFloat() * size.width
            fun y(i: Int) = margin + tall * (1f - (points[i].rating - lowest + (spread - range) / 2f) / spread)

            val line = Path().apply {
                moveTo(x(0), y(0))
                for (i in 1..points.lastIndex) lineTo(x(i), y(i))
            }
            val area = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            clipRect(right = size.width * drawn.value) {
                drawPath(area, Brush.verticalGradient(listOf(ink.copy(alpha = 0.2f), Color.Transparent)))
                drawPath(line, ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            val spot = touched
            if (spot != null) drawLine(rule, Offset(x(spot), 0f), Offset(x(spot), size.height), strokeWidth = 1.dp.toPx())
            if (spot != null || drawn.value == 1f) {
                val at = spot ?: points.lastIndex
                drawCircle(ink, radius = 4.dp.toPx(), center = Offset(x(at), y(at)))
            }
        }
        Row {
            Text(formatDate(first), style = CtType.Caption, modifier = Modifier.weight(1f))
            Text(formatDate(points.last().dateMillis), style = CtType.Caption)
        }
    }
}
