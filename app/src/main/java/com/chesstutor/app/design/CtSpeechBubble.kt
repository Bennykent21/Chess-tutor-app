package com.chesstutor.app.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Which side of a speech bubble points at the speaker. Start and End follow the reading direction. */
enum class CtTail { None, Start, End, Top, Bottom }

private val TAIL_DEPTH = 10.dp
private val TAIL_BASE = 16.dp
private val TAIL_OFFSET = 16.dp

/** A speech bubble's body and the three corners of its tail (empty for [CtTail.None]), in pixels. */
internal data class BubbleGeometry(val body: Rect, val tail: List<Offset>)

/**
 * Where the body and tail of a bubble of [size] go. The tail is [depth] deep and [base] wide,
 * [offset] from the body's top (side tails) or start (top and bottom tails); Start and End
 * follow the reading direction.
 */
internal fun bubbleGeometry(tail: CtTail, size: Size, rtl: Boolean, depth: Float, base: Float, offset: Float): BubbleGeometry {
    val left = (tail == CtTail.Start && !rtl) || (tail == CtTail.End && rtl)
    val right = (tail == CtTail.End && !rtl) || (tail == CtTail.Start && rtl)
    val body = Rect(
        left = if (left) depth else 0f,
        top = if (tail == CtTail.Top) depth else 0f,
        right = size.width - if (right) depth else 0f,
        bottom = size.height - if (tail == CtTail.Bottom) depth else 0f,
    )
    val points = when {
        left -> listOf(Offset(body.left, body.top + offset), Offset(0f, body.top + offset + base / 2), Offset(body.left, body.top + offset + base))
        right -> listOf(Offset(body.right, body.top + offset), Offset(size.width, body.top + offset + base / 2), Offset(body.right, body.top + offset + base))
        tail == CtTail.Top -> listOf(Offset(body.left + offset, body.top), Offset(body.left + offset + base / 2, 0f), Offset(body.left + offset + base, body.top))
        tail == CtTail.Bottom -> listOf(Offset(body.left + offset, body.bottom), Offset(body.left + offset + base / 2, size.height), Offset(body.left + offset + base, body.bottom))
        else -> emptyList()
    }
    return BubbleGeometry(body, points)
}

/** A rounded bubble with a small triangle on one side, [TAIL_OFFSET] from its top or start. */
private class BubbleShape(private val tail: CtTail, private val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val g = with(density) {
            bubbleGeometry(tail, size, layoutDirection == LayoutDirection.Rtl, TAIL_DEPTH.toPx(), TAIL_BASE.toPx(), TAIL_OFFSET.toPx())
        }
        val path = Path().apply {
            addRoundRect(RoundRect(g.body, CornerRadius(with(density) { radius.toPx() })))
            if (g.tail.isNotEmpty()) {
                moveTo(g.tail[0].x, g.tail[0].y)
                lineTo(g.tail[1].x, g.tail[1].y)
                lineTo(g.tail[2].x, g.tail[2].y)
                close()
            }
        }
        return Outline.Generic(path)
    }
}

/**
 * A speech bubble whose tail points at whoever is speaking, a coach or a bot (D41). Place the
 * speaker on the [tail] side. Screen readers hear its content once, as one item.
 */
@Composable
fun CtSpeechBubble(
    modifier: Modifier = Modifier,
    tail: CtTail = CtTail.Start,
    color: Color = CtColors.Surface,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .clip(BubbleShape(tail, 18.dp))
            .background(color)
            .semantics(mergeDescendants = true) {}
            .padding(
                start = 14.dp + if (tail == CtTail.Start) TAIL_DEPTH else 0.dp,
                end = 14.dp + if (tail == CtTail.End) TAIL_DEPTH else 0.dp,
                top = CtSpacing.md + if (tail == CtTail.Top) TAIL_DEPTH else 0.dp,
                bottom = CtSpacing.md + if (tail == CtTail.Bottom) TAIL_DEPTH else 0.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}
