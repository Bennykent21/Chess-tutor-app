package com.example.chess.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.analysis.ParsedPgnMove
import com.example.chess.core.PieceColor
import com.example.chess.ui.theme.CanvasCard
import com.example.chess.ui.theme.CanvasCardBorder
import com.example.chess.ui.theme.CanvasCardElevated
import com.example.chess.ui.theme.CoachAccentGold
import com.example.chess.ui.theme.CoachPrimary
import com.example.chess.ui.theme.StatusBlunder
import com.example.chess.ui.theme.StatusMistake
import com.example.chess.ui.theme.TextMuted
import com.example.chess.ui.theme.TextTitle
import kotlin.math.abs

data class MoveEvaluationPoint(
  val moveIndex: Int,
  val san: String,
  val moveNumber: Int,
  val color: PieceColor,
  val centipawns: Int, // from White's perspective, e.g. +180 or -250
  val evalPawns: Float, // centipawns / 100f
  val swingDeltaPawns: Float = 0f
)

/**
 * Interactive Centipawn Advantage Evaluation Graph for the PGN Replayer.
 * Draws an interactive area/line chart with zero-baseline (+ = White advantage, - = Black advantage),
 * highlighting blunders/swings and allowing tap-to-scrub to any move.
 */
@Composable
fun CentipawnEvaluationGraph(
  evalPoints: List<MoveEvaluationPoint>,
  currentMoveIndex: Int,
  onSelectMoveIndex: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  if (evalPoints.isEmpty()) return

  val currentPoint = evalPoints.getOrNull(currentMoveIndex) ?: evalPoints.last()
  val activeEvalPawns = currentPoint.evalPawns
  val evalText = if (activeEvalPawns >= 0) "+${String.format("%.2f", activeEvalPawns)}" else String.format("%.2f", activeEvalPawns)

  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(CanvasCardElevated)
      .border(1.dp, CanvasCardBorder, RoundedCornerShape(14.dp))
      .padding(14.dp)
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      // Header with Live Evaluation Pill & Leading Player
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "EVALUATION CURVE",
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
          )
          Text(
            text = if (activeEvalPawns > 0.5f) "White is winning (+${String.format("%.1f", activeEvalPawns)})"
            else if (activeEvalPawns < -0.5f) "Black is winning (${String.format("%.1f", activeEvalPawns)})"
            else "Dynamic Equality ($evalText)",
            color = TextTitle,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
          )
        }

        // Live eval badge
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
              if (activeEvalPawns > 1.0f) CoachPrimary.copy(alpha = 0.2f)
              else if (activeEvalPawns < -1.0f) StatusBlunder.copy(alpha = 0.2f)
              else CanvasCard
            )
            .border(
              1.dp,
              if (activeEvalPawns > 1.0f) CoachPrimary
              else if (activeEvalPawns < -1.0f) StatusBlunder
              else CanvasCardBorder,
              RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
          Text(
            text = evalText,
            color = if (activeEvalPawns > 1.0f) CoachPrimary else if (activeEvalPawns < -1.0f) StatusBlunder else CoachAccentGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold
          )
        }
      }

      // Canvas Interactive Chart
      val maxPawnsRange = 6.0f // clamp +/- 6 pawns for clean visual curve
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(110.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(Color(0xFF13151A))
          .pointerInput(evalPoints) {
            detectTapGestures { offset ->
              val totalPoints = evalPoints.size
              if (totalPoints > 1) {
                val stepWidth = size.width / (totalPoints - 1)
                val tappedIdx = ((offset.x + stepWidth / 2f) / stepWidth).toInt().coerceIn(0, totalPoints - 1)
                onSelectMoveIndex(tappedIdx)
              }
            }
          }
      ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
          val canvasWidth = size.width
          val canvasHeight = size.height
          val midY = canvasHeight / 2f

          // 1. Draw Zero Advantage Center Baseline (0.0)
          drawLine(
            color = Color.White.copy(alpha = 0.15f),
            start = Offset(0f, midY),
            end = Offset(canvasWidth, midY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
          )

          if (evalPoints.size < 2) return@Canvas

          val stepX = canvasWidth / (evalPoints.size - 1)

          // Helper to map pawn eval to canvas Y (-maxPawns is bottom, +maxPawns is top)
          fun evalToY(pawns: Float): Float {
            val clamped = pawns.coerceIn(-maxPawnsRange, maxPawnsRange)
            val normalized = (clamped + maxPawnsRange) / (2f * maxPawnsRange) // 0 (black max) to 1 (white max)
            return canvasHeight - (normalized * canvasHeight)
          }

          // 2. Build Path for Smooth Advantage Area Fill
          val fillPath = Path()
          fillPath.moveTo(0f, midY)

          for (i in evalPoints.indices) {
            val x = i * stepX
            val y = evalToY(evalPoints[i].evalPawns)
            fillPath.lineTo(x, y)
          }

          val lastX = (evalPoints.size - 1) * stepX
          fillPath.lineTo(lastX, midY)
          fillPath.close()

          // Gradient fill
          drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
              colors = listOf(
                CoachPrimary.copy(alpha = 0.35f),
                CoachPrimary.copy(alpha = 0.05f),
                StatusBlunder.copy(alpha = 0.05f),
                StatusBlunder.copy(alpha = 0.35f)
              ),
              startY = 0f,
              endY = canvasHeight
            )
          )

          // 3. Draw Continuous Stroke Line
          val linePath = Path()
          for (i in evalPoints.indices) {
            val x = i * stepX
            val y = evalToY(evalPoints[i].evalPawns)
            if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
          }

          drawPath(
            path = linePath,
            color = CoachPrimary,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
          )

          // 4. Highlight Significant Swings / Blunders
          for (i in 1 until evalPoints.size) {
            val pt = evalPoints[i]
            if (abs(pt.swingDeltaPawns) >= 1.5f) {
              val x = i * stepX
              val y = evalToY(pt.evalPawns)
              val dotColor = if (abs(pt.swingDeltaPawns) >= 2.5f) StatusBlunder else StatusMistake
              drawCircle(
                color = dotColor,
                radius = 3.5.dp.toPx(),
                center = Offset(x, y)
              )
            }
          }

          // 5. Draw Active Cursor Needle at currentMoveIndex
          if (currentMoveIndex in evalPoints.indices) {
            val cursorX = currentMoveIndex * stepX
            val cursorY = evalToY(evalPoints[currentMoveIndex].evalPawns)

            // Vertical indicator line
            drawLine(
              color = CoachAccentGold,
              start = Offset(cursorX, 0f),
              end = Offset(cursorX, canvasHeight),
              strokeWidth = 1.5.dp.toPx()
            )

            // Cursor pulse ring
            drawCircle(
              color = CoachAccentGold.copy(alpha = 0.3f),
              radius = 8.dp.toPx(),
              center = Offset(cursorX, cursorY)
            )

            // Center solid dot
            drawCircle(
              color = CoachAccentGold,
              radius = 4.5.dp.toPx(),
              center = Offset(cursorX, cursorY)
            )
          }
        }
      }

      // Legend and quick info row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(CoachPrimary))
            Text(text = "White Advantage", color = TextMuted, fontSize = 10.sp)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(StatusBlunder))
            Text(text = "Swing / Blunder", color = TextMuted, fontSize = 10.sp)
          }
        }

        Text(
          text = "Tap graph to scrub move",
          color = TextMuted,
          fontSize = 10.sp
        )
      }
    }
  }
}
