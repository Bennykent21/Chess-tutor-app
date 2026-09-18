package com.chesstutor.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesstutor.app.ui.theme.ChessTutorColors
import kotlin.math.abs
import kotlin.math.exp

/**
 * Vertical evaluation bar.
 *
 * Fill: proportion of the bar filled white vs. black. A forced mate for
 * either side saturates the fill completely (100%/0%) for the winning side —
 * there is no "almost full" state for mate, only for ordinary centipawn
 * evaluations.
 *
 * Label: a single signed number in standard White-POV convention (positive =
 * White ahead, negative = Black ahead). Its position is pinned to whichever
 * physical edge of the bar — top or bottom — the currently-ahead side
 * occupies. It never drifts along the fill boundary and never animates
 * between the two edges: it is always flush against one of them.
 */
@Composable
fun EvalBar(
    centipawns: Int?,
    mateIn: Int?,
    modifier: Modifier = Modifier,
    isWhiteOnBottom: Boolean = true
) {
    // 1. White's fill fraction: 0f = fully black, 1f = fully white.
    val whiteRatioTarget = when {
        mateIn != null -> if (mateIn > 0) 1.0f else 0.0f
        centipawns != null -> {
            val sigmoid = 1.0f / (1.0f + exp(-centipawns / 380.0f))
            sigmoid.coerceIn(0.03f, 0.97f)
        }
        else -> 0.5f
    }

    val animatedWhiteRatio by animateFloatAsState(
        targetValue = whiteRatioTarget,
        animationSpec = tween(durationMillis = 400),
        label = "evalBarHeightAnim"
    )

    // 2. Display string, always in standard White-POV sign convention.
    val displayText = when {
        mateIn != null -> if (mateIn > 0) "M${abs(mateIn)}" else "-M${abs(mateIn)}"
        centipawns != null -> {
            val pawns = centipawns / 100.0
            if (pawns > 0) "+%.1f".format(pawns) else "%.1f".format(pawns)
        }
        else -> "0.0"
    }

    val isWhiteWinning = when {
        mateIn != null -> mateIn > 0
        centipawns != null -> centipawns >= 0
        else -> true
    }

    // 3. Which physical edge the label docks to: whichever edge the
    // currently-ahead side occupies. This is the only thing that decides
    // top-vs-bottom, and it's a hard snap, not a slide or a cross-fade.
    val labelAtBottom = if (isWhiteOnBottom) isWhiteWinning else !isWhiteWinning

    // Text color follows whichever fill sits behind that docked edge.
    val textColor = if (isWhiteWinning) Color(0xFF14181D) else Color(0xFFF2F1EC)
    val textShadow = if (!isWhiteWinning) {
        Shadow(color = Color.Black.copy(alpha = 0.4f), offset = Offset(0f, 2f), blurRadius = 4f)
    } else null

    val whitePortion = if (isWhiteOnBottom) animatedWhiteRatio else (1f - animatedWhiteRatio)

    Box(
        modifier = modifier
            .width(22.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF14181D)) // Dark base fill
            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(3.dp))
    ) {
        // White fill layer, anchored to whichever edge White occupies.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(whitePortion)
                .align(if (isWhiteOnBottom) Alignment.BottomCenter else Alignment.TopCenter)
                .background(Color(0xFFF2F1EC))
        )

        // Label: flush at the top edge or the bottom edge, never in between.
        Box(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            contentAlignment = if (labelAtBottom) Alignment.BottomCenter else Alignment.TopCenter
        ) {
            Text(
                text = displayText,
                color = textColor,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.03).sp,
                    shadow = textShadow
                ),
                modifier = Modifier.padding(
                    top = if (!labelAtBottom) 5.dp else 0.dp,
                    bottom = if (labelAtBottom) 5.dp else 0.dp
                )
            )
        }
    }
}
