package com.chesstutor.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            if (mateIn > 0) 1.0f else 0.0f
        }
        centipawns != null -> {
            // Sigmoid-style conversion: 0 cp -> 0.5 ratio, +500 cp -> ~0.85, -500 cp -> ~0.15
            val clampedCp = centipawns.coerceIn(-1500, 1500)
            val sigmoid = 1.0f / (1.0f + kotlin.math.exp(-clampedCp / 400.0f))
            sigmoid.toFloat()
        }
        else -> 0.5f
    }

    val animatedWhiteRatio by animateFloatAsState(
        targetValue = whiteRatioTarget,
        animationSpec = tween(durationMillis = 350),
        label = "evalBarAnim"
    )

    val displayText = when {
        mateIn != null -> "M${abs(mateIn)}"
        centipawns != null -> {
            val pawns = centipawns / 100.0
            if (pawns > 0) "+%.1f".format(pawns) else "%.1f".format(pawns)
        }
        else -> "0.0"
    }

    val whitePortion = if (isWhiteOnBottom) animatedWhiteRatio else (1f - animatedWhiteRatio)
    val blackPortion = (1f - whitePortion).coerceIn(0.02f, 0.98f)

    Box(
        modifier = modifier
            .width(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF2B2926))
    ) {
        Column(modifier = Modifier.fillMaxHeight().fillMaxWidth()) {
            // Top: Black section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(blackPortion)
                    .background(Color(0xFF3B3935)),
                contentAlignment = Alignment.TopCenter
            ) {
                if (whiteRatioTarget < 0.45f) {
                    Text(
                        text = displayText,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            // Bottom: White section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(whitePortion.coerceIn(0.02f, 0.98f))
                    .background(Color(0xFFE8E8E8)),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (whiteRatioTarget >= 0.45f) {
                    Text(
                        text = displayText,
                        color = Color(0xFF1E1E1E),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
