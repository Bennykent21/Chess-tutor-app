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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesstutor.app.ui.theme.ChessTutorColors
import kotlin.math.abs
import kotlin.math.exp

/**
 * Vertical evaluation bar.
 *
 * FILL - 0f (top) vs 1f (bottom) of the bar filled white vs. black, computed
 * as `whiteRatioTarget` below. A forced mate for either side saturates the
 * fill completely (1.0f / 0.0f) - never an "almost full" state for mate.
 * For an ordinary centipawn score, this is a sigmoid centered on 0: negative
 * centipawns (Black ahead) push the ratio toward 0f, meaning LESS of the bar
 * is white and MORE of it is black. Positive centipawns push it toward 1f
 * (more white). This is standard signed White-POV scoring - nothing here is
 * inverted.
 *
 * LABEL - a single signed number, same White-POV convention (+ = White
 * ahead, - = Black ahead). It docks flush against whichever physical edge
 * (top or bottom) the *currently ahead* side occupies, and never drifts
 * along the fill boundary or cross-fades between edges - it's always fully
 * at one edge or the other.
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

    // 3. Which physical edge the label docks to - the only thing that
    // decides top-vs-bottom. Hard snap, no slide, no cross-fade.
    val labelAtBottom = if (isWhiteOnBottom) isWhiteWinning else !isWhiteWinning

    val textColor = if (isWhiteWinning) Color(0xFF14181D) else Color(0xFFF2F1EC)
    val textShadow = if (!isWhiteWinning) {
        Shadow(color = Color.Black.copy(alpha = 0.4f), offset = Offset(0f, 2f), blurRadius = 4f)
    } else null

    // White fill grows from White's edge: if White is on the bottom, the
    // white rectangle is anchored to the bottom and grows upward as White's
    // ratio increases. If Black is winning (lower ratio), that rectangle is
    // shorter, leaving more of the (already-dark) background exposed at the
    // top - i.e. visibly more black.
    val whitePortion = if (isWhiteOnBottom) animatedWhiteRatio else (1f - animatedWhiteRatio)

    Box(
        modifier = modifier
            .width(30.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF14181D)) // dark base = "black" side, always visible underneath
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
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.05).sp,
                    shadow = textShadow
                ),
                modifier = Modifier.padding(horizontal = 1.dp, vertical = 3.dp)
            )
        }
    }
}
