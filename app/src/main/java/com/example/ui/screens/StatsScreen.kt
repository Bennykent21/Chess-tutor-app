package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.FrostedGlassBox
import com.example.ui.components.TelemetryGraphCard
import com.example.ui.model.BoosterState
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentRose
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderHighlight
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.TextSlate100
import com.example.ui.theme.TextSlate300
import com.example.ui.theme.TextSlate400

@Composable
fun StatsScreen(
  state: BoosterState,
  onRefreshStats: () -> Unit,
  onToggleMonitoring: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "pulse_telemetry")
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.3f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1000),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse_alpha"
  )

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 24.dp)
      .padding(top = 16.dp, bottom = 90.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "MATCH TELEMETRY & HUD",
            color = TextSlate400,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
          )
          Text(
            text = "Live eFootball™ 2027 Monitor",
            color = TextSlate100,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp)
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = onToggleMonitoring,
            colors = ButtonDefaults.buttonColors(
              containerColor = if (state.isLiveMonitoringActive) Color(0x2234D399) else Color(0x22F43F5E),
              contentColor = if (state.isLiveMonitoringActive) AccentEmerald else AccentRose
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.testTag("toggle_monitoring_button")
          ) {
            Icon(
              imageVector = if (state.isLiveMonitoringActive) Icons.Default.Stop else Icons.Default.PlayArrow,
              contentDescription = "Toggle Monitor",
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
              text = if (state.isLiveMonitoringActive) "Live" else "Paused",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }

          Button(
            onClick = onRefreshStats,
            colors = ButtonDefaults.buttonColors(
              containerColor = GlassSurface,
              contentColor = CyanPrimary
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.testTag("refresh_stats_button")
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Refresh Telemetry",
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }
    }

    // Live Gameplay Session Pill
    item {
      FrostedGlassBox(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        backgroundColor = Color(0x1822D3EE),
        borderColor = GlassBorderHighlight
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(AccentEmerald.copy(alpha = pulseAlpha))
            )
            Text(
              text = "Session: ${state.activeGameSessionName}",
              color = CyanPrimary,
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold
            )
          }
          Text(
            text = state.matchPhase,
            color = TextSlate300,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
          )
        }
      }
    }

    // Quad Real-time Metrics Grid: FPS, CPU, GPU, Temperature
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          TelemetryMetricCard(
            title = "REAL-TIME FPS",
            value = "${state.currentFps}",
            targetUnit = "/ 60 target",
            icon = Icons.Default.Speed,
            accentColor = CyanPrimary,
            subtext = if (state.currentFps >= 58f) "Smooth 60 FPS Lock" else "Minor frame drop",
            modifier = Modifier.weight(1f)
          )
          TelemetryMetricCard(
            title = "DEVICE TEMP",
            value = "${state.currentTempCelsius}°C",
            targetUnit = "< 42°C limit",
            icon = Icons.Default.Thermostat,
            accentColor = if (state.currentTempCelsius > 40f) AccentRose else AccentEmerald,
            subtext = state.thermalLevelText,
            modifier = Modifier.weight(1f)
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          TelemetryMetricCard(
            title = "CPU LOAD",
            value = "${state.currentCpuPercent}%",
            targetUnit = "Snapdragon 8 Gen 1",
            icon = Icons.Default.Memory,
            accentColor = AccentAmber,
            subtext = "Octa-core balance",
            modifier = Modifier.weight(1f)
          )
          TelemetryMetricCard(
            title = "GPU LOAD",
            value = "${state.currentGpuPercent}%",
            targetUnit = "Adreno 730",
            icon = Icons.Default.SportsSoccer,
            accentColor = AccentIndigo,
            subtext = "Vulkan Renderer",
            modifier = Modifier.weight(1f)
          )
        }
      }
    }

    // Historical Visual Graphs with Tab Selector
    item {
      TelemetryGraphCard(
        history = state.historyPoints
      )
    }

    // Reality Check / Performance Assessment Card answering user's question
    item {
      FrostedGlassBox(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = GlassSurface,
        borderColor = GlassBorder
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Info,
              contentDescription = null,
              tint = CyanPrimary,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = "Does a Game Booster actually improve performance?",
              color = TextSlate100,
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold
            )
          }

          Text(
            text = "• Placebo Boosters vs Real Hardware: Third-party RAM killers do NOT boost FPS on Android because Android automatically caches memory and restarts killed apps.\n• What ACTUALLY Works: 1) Disabling Samsung RAM Plus eliminates virtual memory page-swapping stutter. 2) Samsung's 'Alternate Game Performance Management' lifts aggressive thermal caps. 3) Locking 60 FPS + Standard graphics avoids GPU thermal throttle.",
            color = TextSlate300,
            fontSize = 11.5.sp,
            lineHeight = 16.sp
          )
        }
      }
    }

    // Network Ping Diagnostics
    item {
      FrostedGlassBox(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = GlassSurface,
        borderColor = GlassBorder
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.NetworkCheck,
              contentDescription = null,
              tint = AccentIndigo,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = "Matchmaking & Packet Latency",
              color = TextSlate100,
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            MetricStat(label = "PING (REAL)", value = "${state.pingMs} ms", highlightColor = CyanPrimary)
            MetricStat(label = "JITTER", value = "±${state.jitterMs} ms", highlightColor = AccentEmerald)
            MetricStat(label = "PACKET LOSS", value = "${state.packetLossPercent}%", highlightColor = AccentEmerald)
          }
        }
      }
    }
  }
}

@Composable
private fun TelemetryMetricCard(
  title: String,
  value: String,
  targetUnit: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  accentColor: Color,
  subtext: String,
  modifier: Modifier = Modifier
) {
  FrostedGlassBox(
    modifier = modifier,
    shape = RoundedCornerShape(20.dp),
    backgroundColor = GlassSurface,
    borderColor = GlassBorder
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = title,
          color = TextSlate400,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp
        )
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(16.dp)
        )
      }

      Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Text(
          text = value,
          color = TextSlate100,
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = targetUnit,
          color = TextSlate400,
          fontSize = 10.sp,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.padding(bottom = 2.dp)
        )
      }

      Text(
        text = subtext,
        color = accentColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1
      )
    }
  }
}

@Composable
private fun MetricStat(
  label: String,
  value: String,
  highlightColor: Color
) {
  Column {
    Text(
      text = label,
      color = TextSlate400,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      letterSpacing = 1.sp
    )
    Text(
      text = value,
      color = highlightColor,
      fontSize = 18.sp,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(top = 2.dp)
    )
  }
}
