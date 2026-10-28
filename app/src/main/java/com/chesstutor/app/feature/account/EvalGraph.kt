package com.chesstutor.app.feature.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.domain.account.MoveQuality
import kotlin.math.roundToInt

/**
 * The evaluation graph (plan 6.4): White's winning chances across the game, White's share
 * filled from the bottom. Mistakes and blunders are dotted; the current position has a line.
 * Tapping a point jumps to that position.
 */
@Composable
fun EvalGraph(
    /** White's winning chances (0 to 1) for each analysed position, the start first. */
    points: List<Float>,
    /** Every position of the game, so a half-analysed game fills only part of the width. */
    totalPositions: Int,
    currentPly: Int?,
    qualities: Map<Int, MoveQuality>,
    onSelect: (ply: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slots = (totalPositions - 1).coerceAtLeast(1)
    val midColor = CtColors.Line
    val areaColor = CtColors.Text.copy(alpha = 0.85f)
    val lineColor = CtColors.Text2
    val blunderColor = qualityColor(MoveQuality.BLUNDER)
    val mistakeColor = qualityColor(MoveQuality.MISTAKE)
    val cursorColor = CtColors.Accent
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(CtShapes.CardSmall)
            .background(CtColors.BgDeep)
            .semantics { contentDescription = "Evaluation graph. Tap to jump to a move." }
            .pointerInput(points.size, slots) {
                detectTapGestures { offset ->
                    val ply = (offset.x / size.width * slots).roundToInt().coerceIn(0, slots)
                    if (ply < points.size) onSelect(ply)
                }
            },
    ) {
        val w = size.width
        val h = size.height
        fun x(ply: Int) = ply / slots.toFloat() * w
        fun y(share: Float) = h * (1f - share.coerceIn(0.02f, 0.98f))

        // The middle line: equal chances.
        drawLine(midColor, Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 1.dp.toPx())
        if (points.isNotEmpty()) {
            val area = Path().apply {
                moveTo(0f, h)
                points.forEachIndexed { ply, share -> lineTo(x(ply), y(share)) }
                lineTo(x(points.lastIndex), h)
                close()
            }
            drawPath(area, areaColor)
            val line = Path().apply {
                points.forEachIndexed { ply, share -> if (ply == 0) moveTo(0f, y(share)) else lineTo(x(ply), y(share)) }
            }
            drawPath(line, lineColor, style = Stroke(width = 1.5.dp.toPx()))
            qualities.forEach { (ply, quality) ->
                if (ply >= points.size) return@forEach
                val color = when (quality) {
                    MoveQuality.BLUNDER -> blunderColor
                    MoveQuality.MISTAKE -> mistakeColor
                    else -> return@forEach
                }
                drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(x(ply), y(points[ply])))
            }
        }
        currentPly?.takeIf { it in 0..slots }?.let { ply ->
            drawLine(cursorColor, Offset(x(ply), 0f), Offset(x(ply), h), strokeWidth = 2.dp.toPx())
        }
    }
}
