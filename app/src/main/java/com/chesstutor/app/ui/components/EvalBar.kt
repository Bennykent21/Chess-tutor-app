package com.chesstutor.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesstutor.app.ui.theme.ChessTutorColors
import kotlin.math.abs

@Composable
fun EvalBar(
    centipawns: Int?,
    mateIn: Int?,
    modifier: Modifier = Modifier,
    isWhiteOnBottom: Boolean = true
) {
    // Fraction of bar that is white (0.0 = completely black, 1.0 = completely white)
    val whiteRatioTarget = when {
        mateIn != null -> {
            if (mateIn > 0) 0.98f else 0.02f
        }
        centipawns != null -> {
            val clampedCp = centipawns.coerceIn(-1200, 1200)
            val sigmoid = 1.0f / (1.0f + kotlin.math.exp(-clampedCp / 320.0f))
            sigmoid.coerceIn(0.04f, 0.96f)
        }
        else -> 0.5f
    }

    val animatedWhiteRatio by animateFloatAsState(
        targetValue = whiteRatioTarget,
        animationSpec = tween(durationMillis = 300),
        label = "evalBarAnim"
    )

    val displayText = when {
        mateIn != null -> if (mateIn > 0) "+M${abs(mateIn)}" else "-M${abs(mateIn)}"
        centipawns != null -> {
            val pawns = centipawns / 100.0
            if (pawns > 0) "+%.1f".format(pawns) else "%.1f".format(pawns)
        }
        else -> "0.0"
    }

    // White's advantage grows upward when white is on bottom
    val whitePortion = if (isWhiteOnBottom) animatedWhiteRatio else (1f - animatedWhiteRatio)
    val blackPortion = (1f - whitePortion).coerceIn(0.02f, 0.98f)

    Box(
        modifier = modifier
            .width(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1E2127))
            .border(1.dp, ChessTutorColors.Border, RoundedCornerShape(6.dp))
    ) {
        Column(modifier = Modifier.fillMaxHeight().fillMaxWidth()) {
            // Top: Black portion
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(blackPortion)
                    .background(Color(0xFF262930)),
                contentAlignment = Alignment.TopCenter
            ) {
                if (whitePortion < 0.35f) {
                    Text(
                        text = displayText,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Bottom: White portion
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(whitePortion.coerceIn(0.02f, 0.98f))
                    .background(Color(0xFFE2E4E9)),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (whitePortion >= 0.35f) {
                    Text(
                        text = displayText,
                        color = Color(0xFF121418),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

