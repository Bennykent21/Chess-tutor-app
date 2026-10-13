package com.chesstutor.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesstutor.app.engine.WinProbability
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.abs

fun formatEvalLabel(centipawns: Int?, mateIn: Int?): String = when {
    mateIn != null -> if (mateIn > 0) "M${abs(mateIn)}" else "-M${abs(mateIn)}"
    centipawns != null -> {
        val pawns = centipawns / 100.0
        if (pawns > 0) "+%.1f".format(pawns) else "%.1f".format(pawns)
    }
    else -> "0.0"
}

/**
 * Slim horizontal evaluation bar placed above the board (Review 3 §2.4 & §3).
 * Never clips score text and leaves full horizontal room for the board with margins.
 */
@Composable
fun HorizontalEvalBar(
    centipawns: Int?,
    mateIn: Int?,
    modifier: Modifier = Modifier,
    isWhiteOnBottom: Boolean = true
) {
    val whiteRatioTarget = WinProbability.whiteBarFraction(centipawns = centipawns, mateInMoves = mateIn)
    val animatedWhiteRatio by animateFloatAsState(
        targetValue = whiteRatioTarget,
        animationSpec = tween(durationMillis = 320),
        label = "horizontalEvalBarAnim"
    )
    val displayText = formatEvalLabel(centipawns, mateIn)
    val isWhiteWinning = when {
        mateIn != null -> mateIn > 0
        centipawns != null -> centipawns >= 0
        else -> true
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("eval_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF1A2128))
                .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(5.dp))
        ) {
            val fillFraction = if (isWhiteOnBottom) animatedWhiteRatio else (1f - animatedWhiteRatio)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fillFraction.coerceIn(0.03f, 0.97f))
                    .align(if (isWhiteOnBottom) Alignment.CenterStart else Alignment.CenterEnd)
                    .background(Color(0xFFF2F1EC))
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isWhiteWinning) Color(0xFFF2F1EC) else ChessTutorColors.Surface2)
                .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayText,
                color = if (isWhiteWinning) Color(0xFF14181D) else ChessTutorColors.TextPrimary,
                maxLines = 1,
                softWrap = false,
                style = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum"
                )
            )
        }
    }
}

@Composable
fun EvalBar(
    centipawns: Int?,
    mateIn: Int?,
    modifier: Modifier = Modifier,
    isWhiteOnBottom: Boolean = true
) {
    val whiteRatioTarget = WinProbability.whiteBarFraction(centipawns = centipawns, mateInMoves = mateIn)
    val animatedWhiteRatio by animateFloatAsState(
        targetValue = whiteRatioTarget,
        animationSpec = tween(durationMillis = 400),
        label = "evalBarHeightAnim"
    )
    val displayText = formatEvalLabel(centipawns, mateIn)
    val isWhiteWinning = when {
        mateIn != null -> mateIn > 0
        centipawns != null -> centipawns >= 0
        else -> true
    }
    val labelAtBottom = if (isWhiteOnBottom) isWhiteWinning else !isWhiteWinning
    val textColor = if (isWhiteWinning) Color(0xFF14181D) else Color(0xFFF2F1EC)
    val whitePortion = if (isWhiteOnBottom) animatedWhiteRatio else (1f - animatedWhiteRatio)

    Box(
        modifier = modifier
            .testTag("eval_bar")
            .width(34.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF14181D))
            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(4.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(whitePortion)
                .align(if (isWhiteOnBottom) Alignment.BottomCenter else Alignment.TopCenter)
                .background(Color(0xFFF2F1EC))
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            contentAlignment = if (labelAtBottom) Alignment.BottomCenter else Alignment.TopCenter
        ) {
            Text(
                text = displayText,
                color = textColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                style = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                ),
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
            )
        }
    }
}
