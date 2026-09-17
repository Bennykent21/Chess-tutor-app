package com.chesstutor.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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

@Composable
fun EvalBar(
    centipawns: Int?,
    mateIn: Int?,
    modifier: Modifier = Modifier,
    isWhiteOnBottom: Boolean = true
) {
    // 1. Determine target white fill fraction (0.0f = completely black, 1.0f = completely white)
    // Full bar for the winning side on forced mate
    val whiteRatioTarget = when {
        mateIn != null -> {
            if (mateIn > 0) 1.0f else 0.0f
        }
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

    // 2. Format display evaluation string
    val displayText = when {
        mateIn != null -> {
            if (mateIn > 0) "M${abs(mateIn)}" else "-M${abs(mateIn)}"
        }
        centipawns != null -> {
            val pawns = centipawns / 100.0
            if (pawns > 0) "+%.1f".format(pawns) else "%.1f".format(pawns)
        }
        else -> "0.0"
    }

    // 3. Determine which side has advantage
    // If White is winning (cp >= 0 or mateIn > 0), advantage is White.
    // If Black is winning (cp < 0 or mateIn < 0), advantage is Black.
    val isWhiteWinning = when {
        mateIn != null -> mateIn > 0
        centipawns != null -> centipawns >= 0
        else -> true
    }

    // Determine whether the winning side is located at the top or bottom of the board
    val isAdvantageAtBottom = if (isWhiteOnBottom) isWhiteWinning else !isWhiteWinning

    // Text color:
    // When text is on the White fill (#F2F1EC), color is dark #14181D.
    // When text is on the Black fill (#14181D), color is light #F2F1EC.
    // In our fixed-placement model:
    // If White is winning, the text sits on White's end (Dark text on Light fill).
    // If Black is winning, the text sits on Black's end (Light text on Dark fill).
    val targetTextColor = if (isWhiteWinning) {
        Color(0xFF14181D)
    } else {
        Color(0xFFF2F1EC)
    }

    val animatedTextColor by animateColorAsState(
        targetValue = targetTextColor,
        animationSpec = tween(durationMillis = 250),
        label = "evalTextColorAnim"
    )

    // Subtle drop shadow when Black is winning (light text) to guarantee legibility
    val textShadow = if (!isWhiteWinning) {
        Shadow(
            color = Color.Black.copy(alpha = 0.4f),
            offset = Offset(0f, 2f),
            blurRadius = 4f
        )
    } else null

    // White portion fill height (measured from bottom if White is on bottom)
    val whitePortion = if (isWhiteOnBottom) animatedWhiteRatio else (1f - animatedWhiteRatio)

    Box(
        modifier = modifier
            .width(22.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF14181D)) // Dark base fill
            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(3.dp))
    ) {
        // White Fill Layer (anchored to bottom if isWhiteOnBottom, else anchored to top)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(whitePortion)
                .align(if (isWhiteOnBottom) Alignment.BottomCenter else Alignment.TopCenter)
                .background(Color(0xFFF2F1EC))
        )

        // Text display anchored to either Top or Bottom with smooth AnimatedContent transition
        AnimatedContent(
            targetState = isAdvantageAtBottom,
            transitionSpec = {
                fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(250))
            },
            modifier = Modifier.fillMaxHeight().fillMaxWidth(),
            label = "evalTextPlacement"
        ) { advantageAtBottom ->
            Box(
                modifier = Modifier.fillMaxHeight().fillMaxWidth(),
                contentAlignment = if (advantageAtBottom) Alignment.BottomCenter else Alignment.TopCenter
            ) {
                Text(
                    text = displayText,
                    color = animatedTextColor,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.03).sp,
                        shadow = textShadow
                    ),
                    modifier = Modifier.padding(
                        top = if (!advantageAtBottom) 5.dp else 0.dp,
                        bottom = if (advantageAtBottom) 5.dp else 0.dp
                    )
                )
            }
        }
    }
}
