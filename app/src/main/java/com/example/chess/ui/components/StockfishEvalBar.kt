package com.example.chess.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.chess.engine.Evaluation

/**
 * Vertical Stockfish Evaluation Bar.
 * Clean, animated bar displaying real-time White vs Black advantage,
 * formatted numerical evaluation (+1.4 / -0.8 / M2), and smooth transitions.
 */
@Composable
fun StockfishEvalBar(
  evaluation: Evaluation,
  modifier: Modifier = Modifier,
  flipped: Boolean = false
) {
  val winRate = evaluation.winningPercentageWhite()
  val animatedRatio by animateFloatAsState(
    targetValue = if (flipped) 1f - winRate else winRate,
    animationSpec = tween(durationMillis = 350),
    label = "eval_bar_ratio"
  )

  val barShape = RoundedCornerShape(8.dp)

  Box(
    modifier = modifier
      .width(26.dp)
      .clip(barShape)
      .border(1.dp, Color(0x334E5D6C), barShape)
      .background(Color(0xFF1E242B)) // Black side (dark slate)
  ) {
    // White portion (light ivory)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(animatedRatio.coerceIn(0.05f, 0.95f))
        .align(if (flipped) Alignment.TopCenter else Alignment.BottomCenter)
        .background(Color(0xFFE2E8F0))
    )

    // Evaluation text label
    val displayText = evaluation.format()
    val isWhiteAdvantage = (evaluation.centipawns ?: 0) >= 0 && (evaluation.mateInMoves ?: 0) >= 0

    Text(
      text = displayText,
      color = if (isWhiteAdvantage) Color(0xFF0F172A) else Color(0xFFF8FAFC),
      fontSize = 9.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier
        .align(
          if (isWhiteAdvantage) {
            if (flipped) Alignment.TopCenter else Alignment.BottomCenter
          } else {
            if (flipped) Alignment.BottomCenter else Alignment.TopCenter
          }
        )
    )
  }
}
