package com.chesstutor.app.feature.account

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtMotion
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.LocalReduceMotion
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.account.GraphPeriod
import com.chesstutor.app.domain.account.RatingTrend
import kotlin.math.abs

/** Enough to show the shape of a year without the zigzag of every game (D65). */
private const val MOST_POINTS = 90

/** A flat run still gets a line in the middle, not one at full height. */
private const val SMALLEST_RANGE = 20

/** "+24", or "−12" with a true minus sign. */
internal fun changeText(change: Int): String = if (change > 0) "+$change" else "−${abs(change)}"

/**
 * The top of a stats page (D64): the page's metal [icon] with a soft glow of its own [hue], the
 * rating and its rise or fall over the chosen period, then the graph in that hue from edge to
 * edge, with the peak as a dashed line, and the periods under it. A finger on the graph reads any
 * day. [current] is shown when the trend has no points (or is fresher than its last one).
 */
@Composable
fun RatingHero(
    trend: RatingTrend,
    current: Int?,
    now: Long,
    @DrawableRes icon: Int,
    hue: Color,
    modifier: Modifier = Modifier,
) {
    var periodName by rememberSaveable { mutableStateOf(GraphPeriod.QUARTER.name) }
    val period = GraphPeriod.valueOf(periodName)
    // A period with too little in it shows the whole trend rather than an empty graph.
    val shown = remember(trend, period, now) { trend.within(now, period.days).takeIf { it.points.size >= 2 } ?: trend }
    val points = remember(shown) { shown.forGraph(MOST_POINTS) }
    var touched by remember(shown) { mutableStateOf<Int?>(null) }
    val graphed = points.size >= 2
    val rating = touched?.let { points[it].rating } ?: current ?: points.lastOrNull()?.rating
    val change = if (graphed) points.last().rating - points.first().rating else 0

    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CtSpacing.gutter, vertical = CtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CtSpacing.lg),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .drawBehind { drawCircle(Brush.radialGradient(listOf(hue.copy(alpha = 0.3f), Color.Transparent), radius = size.minDimension), radius = size.minDimension) },
            ) {
                Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(72.dp))
            }
            Column {
                Text(rating?.toString() ?: "–", style = CtType.Display, maxLines = 1)
                when {
                    touched != null -> Text(formatDate(points[touched!!].dateMillis), style = CtType.Caption)
                    change != 0 -> Text(changeText(change), style = CtType.Label.copy(color = if (change > 0) CtColors.Success else CtColors.Error))
                }
            }
        }
        if (graphed) {
            Graph(points, hue, onTouch = { touched = it }, touched = touched)
            Row(Modifier.fillMaxWidth().padding(horizontal = CtSpacing.gutter)) {
                Text(formatDate(points.first().dateMillis), style = CtType.Caption, modifier = Modifier.weight(1f))
                Text(formatDate(points.last().dateMillis), style = CtType.Caption)
            }
            Periods(period, onPick = { periodName = it.name })
        }
    }
}

@Composable
private fun Graph(points: List<com.chesstutor.app.domain.account.RatingPoint>, hue: Color, touched: Int?, onTouch: (Int?) -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val drawn = remember(points) { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(points) {
        if (!reduceMotion) drawn.animateTo(1f, tween(CtMotion.DRAW_MS, easing = CtMotion.EaseOut))
    }
    val first = points.first().dateMillis
    val span = (points.last().dateMillis - first).coerceAtLeast(1L)
    val lowest = points.minOf { it.rating }
    val peak = points.maxOf { it.rating }
    val range = (peak - lowest).coerceAtLeast(1)
    val spread = range.coerceAtLeast(SMALLEST_RANGE)
    val rule = CtColors.Line
    val labelColor = CtColors.Text2
    val measurer = rememberTextMeasurer()
    val peakStyle = CtType.Caption.copy(color = labelColor)
    val inset = with(androidx.compose.ui.platform.LocalDensity.current) { CtSpacing.gutter.toPx() }
    fun nearest(x: Float, width: Float): Int {
        val share = ((x - inset) / (width - 2 * inset)).coerceIn(0f, 1f)
        val day = first + (share * span).toLong()
        return points.indices.minBy { abs(points[it].dateMillis - day) }
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .semantics { contentDescription = "Rating graph, now ${points.last().rating}. Hold to read a day." }
            .pointerInput(points) {
                detectTapGestures(
                    onPress = { at ->
                        onTouch(nearest(at.x, size.width.toFloat()))
                        tryAwaitRelease()
                        onTouch(null)
                    },
                )
            }
            .pointerInput(points) {
                detectHorizontalDragGestures(
                    onDragStart = { at -> onTouch(nearest(at.x, size.width.toFloat())) },
                    onDragEnd = { onTouch(null) },
                    onDragCancel = { onTouch(null) },
                ) { drag, _ -> onTouch(nearest(drag.position.x, size.width.toFloat())) }
            },
    ) {
        val top = 20.dp.toPx()
        val bottom = 6.dp.toPx()
        val tall = size.height - top - bottom
        val wide = size.width - 2 * inset
        fun x(i: Int) = inset + (points[i].dateMillis - first) / span.toFloat() * wide
        fun yOf(rating: Int) = top + tall * (1f - (rating - lowest + (spread - range) / 2f) / spread)
        fun y(i: Int) = yOf(points[i].rating)

        // The peak: a dashed line across, named at its right end.
        val peakY = yOf(peak)
        drawLine(rule, Offset(inset, peakY), Offset(size.width - inset, peakY), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
        val label = measurer.measure("Peak $peak", peakStyle)
        drawText(label, topLeft = Offset(size.width - inset - label.size.width, peakY - label.size.height - 2.dp.toPx()))

        val line = smoothLine(points.indices.map { x(it) }, points.indices.map { y(it) })
        val area = Path().apply {
            addPath(line)
            lineTo(x(points.lastIndex), size.height)
            lineTo(x(0), size.height)
            close()
        }
        clipRect(right = inset + wide * drawn.value) {
            drawPath(area, Brush.verticalGradient(listOf(hue.copy(alpha = 0.25f), Color.Transparent), startY = top, endY = size.height))
            drawPath(line, hue, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (touched != null) drawLine(rule, Offset(x(touched), 0f), Offset(x(touched), size.height), strokeWidth = 1.dp.toPx())
        if (touched != null || drawn.value == 1f) {
            val at = touched ?: points.lastIndex
            drawCircle(hue.copy(alpha = 0.2f), radius = 9.dp.toPx(), center = Offset(x(at), y(at)))
            drawCircle(hue, radius = 4.5.dp.toPx(), center = Offset(x(at), y(at)))
        }
    }
}

/**
 * A smooth line through every point that never bends past them (D65): a monotone cubic
 * (Fritsch and Carlson), so a rise stays a rise and the curve's highs and lows are real ratings.
 */
internal fun smoothLine(xs: List<Float>, ys: List<Float>): Path {
    val n = xs.size
    val path = Path().apply { moveTo(xs[0], ys[0]) }
    if (n < 3) {
        for (i in 1 until n) path.lineTo(xs[i], ys[i])
        return path
    }
    val slopes = List(n - 1) { i -> (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i]).coerceAtLeast(0.001f) }
    val tangents = FloatArray(n) { i ->
        when (i) {
            0 -> slopes[0]
            n - 1 -> slopes[n - 2]
            // Flat where the line turns, so it does not overshoot the turning point.
            else -> if (slopes[i - 1] * slopes[i] <= 0f) 0f else (slopes[i - 1] + slopes[i]) / 2f
        }
    }
    for (i in 0 until n - 1) {
        val s = slopes[i]
        if (s == 0f) {
            tangents[i] = 0f
            tangents[i + 1] = 0f
            continue
        }
        val a = tangents[i] / s
        val b = tangents[i + 1] / s
        val h = a * a + b * b
        if (h > 9f) {
            val t = 3f / kotlin.math.sqrt(h)
            tangents[i] = t * a * s
            tangents[i + 1] = t * b * s
        }
    }
    for (i in 0 until n - 1) {
        val dx = (xs[i + 1] - xs[i]) / 3f
        path.cubicTo(xs[i] + dx, ys[i] + tangents[i] * dx, xs[i + 1] - dx, ys[i + 1] - tangents[i + 1] * dx, xs[i + 1], ys[i + 1])
    }
    return path
}

/** 1M, 3M, 1Y, All: how far back the graph looks. */
@Composable
private fun Periods(selected: GraphPeriod, onPick: (GraphPeriod) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CtSpacing.gutter, vertical = CtSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.xs),
    ) {
        GraphPeriod.entries.forEach { period ->
            val on = period == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(CtShapes.Pill)
                    .then(if (on) Modifier.background(CtColors.Surface) else Modifier)
                    .ctPressable(role = Role.Tab, onClickLabel = "Show ${period.label}") { onPick(period) }
                    .semantics { this.selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(period.label, style = CtType.Label.copy(color = if (on) CtColors.Text else CtColors.Text2), maxLines = 1)
            }
        }
    }
}
