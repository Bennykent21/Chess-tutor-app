package com.example.chess.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.chess.core.PieceColor
import com.example.chess.ui.theme.CanvasBackground
import com.example.chess.ui.theme.CanvasCard
import com.example.chess.ui.theme.CanvasCardBorder
import com.example.chess.ui.theme.CoachAccentGold
import com.example.chess.ui.theme.CoachPrimary
import com.example.chess.ui.theme.LiquidGlassBorderCyan
import com.example.chess.ui.theme.LiquidGlassBorderGold
import com.example.chess.ui.theme.LiquidGlassSurface
import com.example.chess.ui.theme.LiquidGlassSurfaceElevated
import com.example.chess.ui.theme.LiquidGlassSurfaceSubtle
import com.example.chess.ui.theme.StatusBlunder
import com.example.chess.ui.theme.StatusExcellent
import com.example.chess.ui.theme.StatusMistake
import com.example.chess.ui.theme.TextBody
import com.example.chess.ui.theme.TextMuted
import com.example.chess.ui.theme.TextTitle
import com.example.chess.ui.theme.liquidGlassCard
import com.example.chess.ui.theme.liquidGlassPill
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Standard Chess Time Controls.
 */
enum class TimeControl(
  val label: String,
  val subtitle: String,
  val baseSeconds: Int,
  val incrementSeconds: Int
) {
  UNLIMITED("Casual", "Unlimited time per side", 0, 0),
  BLITZ_3_2("3 + 2 Blitz", "3 minutes with 2s increment", 180, 2),
  BLITZ_5_0("5 min Blitz", "5 minutes sudden death", 300, 0),
  RAPID_10_0("10 min Rapid", "10 minutes standard rapid", 600, 0),
  RAPID_15_10("15 + 10 Rapid", "15 minutes with 10s increment", 900, 10);

  fun formatSeconds(): String {
    if (baseSeconds == 0) return "∞"
    val mins = baseSeconds / 60
    return if (incrementSeconds > 0) "$mins+$incrementSeconds" else "${mins}m"
  }
}

/**
 * Format milliseconds into standard MM:SS display.
 */
fun formatClockTime(millis: Long): String {
  val totalSec = (millis.coerceAtLeast(0) / 1000).toInt()
  val m = totalSec / 60
  val s = totalSec % 60
  return String.format("%02d:%02d", m, s)
}

/**
 * Categorization breakdown of moves played during a game.
 */
data class MoveClassificationBreakdown(
  val brilliantCount: Int = 0,
  val bestCount: Int = 0,
  val excellentCount: Int = 0,
  val goodCount: Int = 0,
  val inaccuracyCount: Int = 0,
  val mistakeCount: Int = 0,
  val blunderCount: Int = 0,
  val accuracyPercent: Float = 100f
)

/**
 * Computes move quality breakdown and accuracy percentage using harmonic loss weighting.
 */
fun calculateAccuracyAndBreakdown(
  evalPoints: List<MoveEvaluationPoint>,
  color: PieceColor
): MoveClassificationBreakdown {
  val colorMoves = evalPoints.filter { it.color == color }
  if (colorMoves.isEmpty()) return MoveClassificationBreakdown()

  var brilliant = 0
  var best = 0
  var excellent = 0
  var good = 0
  var inaccuracy = 0
  var mistake = 0
  var blunder = 0
  var totalLoss = 0f

  for (pt in colorMoves) {
    // Loss is positive when eval dropped for this player
    val loss = if (color == PieceColor.WHITE) -pt.swingDeltaPawns else pt.swingDeltaPawns
    val lossClamped = max(0f, loss)
    totalLoss += lossClamped

    when {
      lossClamped <= 0.05f && pt.evalPawns > 1.5f && pt.swingDeltaPawns > 2.0f -> brilliant++
      lossClamped <= 0.15f -> best++
      lossClamped <= 0.35f -> excellent++
      lossClamped <= 0.70f -> good++
      lossClamped <= 1.40f -> inaccuracy++
      lossClamped <= 2.20f -> mistake++
      else -> blunder++
    }
  }

  val avgLoss = totalLoss / colorMoves.size
  // Accurate exponential scaling model (100% for 0 loss, ~85% for 0.4 loss, ~65% for 1.2 loss)
  val accuracy = (100f * exp(-0.35 * avgLoss.toDouble())).toFloat().coerceIn(15f, 100f)

  return MoveClassificationBreakdown(
    brilliantCount = brilliant,
    bestCount = best,
    excellentCount = excellent,
    goodCount = good,
    inaccuracyCount = inaccuracy,
    mistakeCount = mistake,
    blunderCount = blunder,
    accuracyPercent = accuracy
  )
}

/**
 * Dialog for selecting standard Chess Time Controls.
 */
@Composable
fun TimeControlDialog(
  current: TimeControl,
  onSelect: (TimeControl) -> Unit,
  onDismiss: () -> Unit
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .clip(RoundedCornerShape(20.dp))
        .background(CanvasBackground)
        .border(1.dp, CanvasCardBorder, RoundedCornerShape(20.dp))
        .padding(20.dp)
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Timer,
              contentDescription = null,
              tint = CoachAccentGold,
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = "Time Controls",
              color = TextTitle,
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold
            )
          }
          IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
          }
        }

        Text(
          text = "Select clock time per side. Clocks begin after White's first move.",
          color = TextBody,
          fontSize = 12.sp
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          TimeControl.values().forEach { tc ->
            val isSelected = tc == current
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) CoachPrimary.copy(alpha = 0.15f) else LiquidGlassSurface)
                .border(
                  1.dp,
                  if (isSelected) CoachPrimary else LiquidGlassSurfaceSubtle,
                  RoundedCornerShape(12.dp)
                )
                .clickable {
                  onSelect(tc)
                  onDismiss()
                }
                .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text(
                    text = tc.label,
                    color = if (isSelected) CoachPrimary else TextTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = tc.subtitle,
                    color = TextMuted,
                    fontSize = 11.sp
                  )
                }
                if (isSelected) {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = CoachPrimary,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Dialog for selecting Custom Chessboard & Piece Visual Themes.
 */
@Composable
fun BoardThemeDialog(
  currentTheme: ChessBoardTheme,
  onSelectTheme: (ChessBoardTheme) -> Unit,
  onDismiss: () -> Unit
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .clip(RoundedCornerShape(20.dp))
        .background(CanvasBackground)
        .border(1.dp, CanvasCardBorder, RoundedCornerShape(20.dp))
        .padding(20.dp)
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Palette,
              contentDescription = null,
              tint = CoachAccentGold,
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = "Board Themes",
              color = TextTitle,
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold
            )
          }
          IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          ChessBoardTheme.values().forEach { theme ->
            val isSelected = theme == currentTheme
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) CoachPrimary.copy(alpha = 0.15f) else LiquidGlassSurface)
                .border(
                  1.dp,
                  if (isSelected) CoachPrimary else LiquidGlassSurfaceSubtle,
                  RoundedCornerShape(12.dp)
                )
                .clickable {
                  onSelectTheme(theme)
                  onDismiss()
                }
                .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  // Swatch showing 2x2 miniature board preview
                  Column(
                    modifier = Modifier
                      .size(32.dp)
                      .clip(RoundedCornerShape(6.dp))
                      .border(1.dp, theme.boardBorder, RoundedCornerShape(6.dp))
                  ) {
                    Row(modifier = Modifier.weight(1f)) {
                      Box(modifier = Modifier.weight(1f).background(theme.lightSquare))
                      Box(modifier = Modifier.weight(1f).background(theme.darkSquare))
                    }
                    Row(modifier = Modifier.weight(1f)) {
                      Box(modifier = Modifier.weight(1f).background(theme.darkSquare))
                      Box(modifier = Modifier.weight(1f).background(theme.lightSquare))
                    }
                  }

                  Text(
                    text = theme.label,
                    color = if (isSelected) CoachPrimary else TextTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                  )
                }

                if (isSelected) {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = CoachPrimary,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Dialog for Exporting match PGN and FEN with quick Copy & Android Share.
 */
@Composable
fun ExportGameDialog(
  pgnText: String,
  fenText: String,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  var copiedText by remember { mutableStateOf<String?>(null) }

  fun copyToClipboard(label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    copiedText = "$label copied to clipboard!"
  }

  fun sharePgn() {
    val sendIntent = Intent().apply {
      action = Intent.ACTION_SEND
      putExtra(Intent.EXTRA_TEXT, pgnText)
      type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Share Chess PGN")
    context.startActivity(shareIntent)
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .clip(RoundedCornerShape(20.dp))
        .background(CanvasBackground)
        .border(1.dp, CanvasCardBorder, RoundedCornerShape(20.dp))
        .padding(20.dp)
    ) {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Share,
              contentDescription = null,
              tint = CoachAccentGold,
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = "Export Game (PGN & FEN)",
              color = TextTitle,
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold
            )
          }
          IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
          }
        }

        if (copiedText != null) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(CoachPrimary.copy(alpha = 0.2f))
              .padding(8.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(text = copiedText!!, color = CoachPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }

        // FEN Section
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(text = "CURRENT FEN", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(LiquidGlassSurface)
              .padding(10.dp)
          ) {
            Text(text = fenText, color = TextBody, fontSize = 11.sp, maxLines = 2)
          }
          Button(
            onClick = { copyToClipboard("FEN", fenText) },
            modifier = Modifier.fillMaxWidth().height(36.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LiquidGlassSurfaceElevated, contentColor = TextTitle)
          ) {
            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Copy FEN", fontSize = 12.sp)
          }
        }

        // PGN Section
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(text = "STANDARD PGN NOTATION", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(140.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(LiquidGlassSurface)
              .padding(10.dp)
              .verticalScroll(rememberScrollState())
          ) {
            Text(text = pgnText, color = TextBody, fontSize = 11.5.sp)
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Button(
              onClick = { copyToClipboard("PGN", pgnText) },
              modifier = Modifier.weight(1f).height(40.dp),
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.buttonColors(containerColor = CoachPrimary, contentColor = Color(0xFF0F1115))
            ) {
              Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Copy PGN", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }

            Button(
              onClick = { sharePgn() },
              modifier = Modifier.weight(1f).height(40.dp),
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.buttonColors(containerColor = CoachAccentGold, contentColor = Color(0xFF0F1115))
            ) {
              Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Share PGN", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}

/**
 * Comprehensive Post-Game Accuracy Report & Move Graph Modal.
 * Shows player vs bot accuracy score, move classification badges, and interactive advantage curve.
 */
@Composable
fun GameAccuracyReportDialog(
  gameTitle: String,
  gameResultSummary: String,
  evalPoints: List<MoveEvaluationPoint>,
  playerColor: PieceColor,
  botName: String,
  onRematch: () -> Unit,
  onDismiss: () -> Unit
) {
  var selectedScrubMoveIndex by remember { mutableIntStateOf(evalPoints.size - 1) }

  val playerBreakdown = remember(evalPoints, playerColor) {
    calculateAccuracyAndBreakdown(evalPoints, playerColor)
  }
  val botBreakdown = remember(evalPoints, playerColor) {
    calculateAccuracyAndBreakdown(evalPoints, playerColor.opposite())
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .clip(RoundedCornerShape(22.dp))
        .background(CanvasBackground)
        .border(1.dp, CanvasCardBorder, RoundedCornerShape(22.dp))
        .padding(18.dp)
    ) {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "GAME ACCURACY REPORT",
              color = CoachAccentGold,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.1.sp
            )
            Text(
              text = gameResultSummary,
              color = TextTitle,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold
            )
          }

          IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
          }
        }

        // Accuracy Score Cards (Player vs Bot)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Player Accuracy Card
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(14.dp))
              .background(CoachPrimary.copy(alpha = 0.12f))
              .border(1.dp, CoachPrimary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
              .padding(12.dp)
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "YOUR ACCURACY",
                color = CoachPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "${String.format("%.1f", playerBreakdown.accuracyPercent)}%",
                color = TextTitle,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
              )
              LinearProgressIndicator(
                progress = { playerBreakdown.accuracyPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = CoachPrimary,
                trackColor = LiquidGlassSurface,
                strokeCap = StrokeCap.Round
              )
            }
          }

          // Bot Accuracy Card
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(14.dp))
              .background(CanvasCard)
              .border(1.dp, CanvasCardBorder, RoundedCornerShape(14.dp))
              .padding(12.dp)
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "$botName".uppercase(),
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
              )
              Text(
                text = "${String.format("%.1f", botBreakdown.accuracyPercent)}%",
                color = TextTitle,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
              )
              LinearProgressIndicator(
                progress = { botBreakdown.accuracyPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = CoachAccentGold,
                trackColor = LiquidGlassSurface,
                strokeCap = StrokeCap.Round
              )
            }
          }
        }

        // Move Quality Classification Breakdown
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "YOUR MOVE CLASSIFICATION",
            color = TextMuted,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            ClassificationBadge(
              modifier = Modifier.weight(1f),
              title = "Brilliant",
              count = playerBreakdown.brilliantCount,
              badgeColor = Color(0xFF06B6D4),
              icon = "⚡"
            )
            ClassificationBadge(
              modifier = Modifier.weight(1f),
              title = "Best",
              count = playerBreakdown.bestCount,
              badgeColor = CoachPrimary,
              icon = "⭐"
            )
            ClassificationBadge(
              modifier = Modifier.weight(1f),
              title = "Good",
              count = playerBreakdown.goodCount + playerBreakdown.excellentCount,
              badgeColor = Color(0xFF60A5FA),
              icon = "👍"
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            ClassificationBadge(
              modifier = Modifier.weight(1f),
              title = "Inaccuracy",
              count = playerBreakdown.inaccuracyCount,
              badgeColor = StatusMistake,
              icon = "❓"
            )
            ClassificationBadge(
              modifier = Modifier.weight(1f),
              title = "Mistake",
              count = playerBreakdown.mistakeCount,
              badgeColor = Color(0xFFF97316),
              icon = "⚠️"
            )
            ClassificationBadge(
              modifier = Modifier.weight(1f),
              title = "Blunder",
              count = playerBreakdown.blunderCount,
              badgeColor = StatusBlunder,
              icon = "❌"
            )
          }
        }

        // Interactive Centipawn Evaluation Curve
        if (evalPoints.isNotEmpty()) {
          CentipawnEvaluationGraph(
            evalPoints = evalPoints,
            currentMoveIndex = selectedScrubMoveIndex.coerceIn(0, evalPoints.size - 1),
            onSelectMoveIndex = { idx -> selectedScrubMoveIndex = idx }
          )
        }

        // Action Buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f).height(42.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextTitle)
          ) {
            Text("Close", fontSize = 13.sp)
          }

          Button(
            onClick = {
              onRematch()
              onDismiss()
            },
            modifier = Modifier.weight(1.2f).height(42.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CoachPrimary, contentColor = Color(0xFF0F1115))
          ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Rematch", fontSize = 13.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
private fun ClassificationBadge(
  title: String,
  count: Int,
  badgeColor: Color,
  icon: String,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(10.dp))
      .background(LiquidGlassSurface)
      .border(1.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
      .padding(horizontal = 8.dp, vertical = 6.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Text(text = icon, fontSize = 11.sp)
        Text(text = title, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
      }
      Text(
        text = "$count",
        color = if (count > 0) badgeColor else TextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
      )
    }
  }
}
