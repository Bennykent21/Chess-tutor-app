package com.chesstutor.app.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One stretch of a [CtRing]: [share] of the whole circle, 0 to 1. */
data class CtRingPart(val share: Float, val color: Color)

/** A ring that sweeps round from the top, on its track, with [content] in the middle. */
@Composable
fun CtRing(
    parts: List<CtRingPart>,
    diameter: Dp,
    modifier: Modifier = Modifier,
    stroke: Dp = 6.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reduceMotion = LocalReduceMotion.current
    val swept = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) swept.animateTo(1f, tween(CtMotion.DRAW_MS, easing = CtMotion.EaseOut))
    }
    val track = CtColors.Track
    Box(
        modifier = modifier.size(diameter).drawBehind {
            val width = stroke.toPx()
            val corner = Offset(width / 2, width / 2)
            val box = Size(size.width - width, size.height - width)
            val line = Stroke(width)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = corner, size = box, style = line)
            var start = -90f
            parts.forEach { part ->
                val sweep = part.share.coerceIn(0f, 1f) * 360f * swept.value
                drawArc(part.color, start, sweep, useCenter = false, topLeft = corner, size = box, style = line)
                start += sweep
            }
        },
        contentAlignment = Alignment.Center,
        content = content,
    )
}
