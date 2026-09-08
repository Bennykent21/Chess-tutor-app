package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.FrostedGlassBox
import com.example.ui.model.BoosterState
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentRose
import com.example.ui.theme.CyanDark
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderHighlight
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.TextSlate100
import com.example.ui.theme.TextSlate300
import com.example.ui.theme.TextSlate400

@Composable
fun BoostScreen(
  state: BoosterState,
  onLaunchGame: () -> Unit,
  onToggleFeature: (String) -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(horizontal = 24.dp)
      .padding(top = 16.dp, bottom = 90.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp)
  ) {
    // Top Header: System Device + Settings Icon
    HeaderSection(
      deviceName = state.deviceModel,
      onOpenSettings = onOpenSettings
    )

    // Hero Optimization Circular Indicator
    OptimizationGaugeSection(
      score = state.optimizationScore,
      isBoosting = state.isBoosting,
      connectionStatus = state.connectionStatus,
      onTriggerBoost = onLaunchGame
    )

    // Game Card: eFootball 2024
    GameCardSection(
      targetFps = state.targetFps,
      pingMs = state.pingMs,
      isBoosting = state.isBoosting,
      onLaunch = onLaunchGame
    )

    // 2x2 Grid of Frosted Glass Feature Cards
    FeatureGridSection(
      state = state,
      onToggleFeature = onToggleFeature
    )
  }
}

@Composable
private fun HeaderSection(
  deviceName: String,
  onOpenSettings: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column {
      Text(
        text = "SYSTEM DEVICE",
        color = TextSlate400,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp
      )
      Text(
        text = deviceName,
        color = TextSlate100,
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 2.dp)
      )
    }

    IconButton(
      onClick = onOpenSettings,
      modifier = Modifier
        .testTag("settings_button")
        .size(42.dp)
        .clip(CircleShape)
        .background(Color(0xFF1E293B).copy(alpha = 0.8f))
        .border(width = 1.dp, color = Color(0xFF334155), shape = CircleShape)
    ) {
      Icon(
        imageVector = Icons.Default.SettingsSuggest,
        contentDescription = "Device Settings",
        tint = CyanPrimary,
        modifier = Modifier.size(22.dp)
      )
    }
  }
}

@Composable
private fun OptimizationGaugeSection(
  score: Int,
  isBoosting: Boolean,
  connectionStatus: String,
  onTriggerBoost: () -> Unit
) {
  val infiniteTransition = rememberInfiniteTransition(label = "gauge_rotation")
  val rotation by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = if (isBoosting) 1800 else 8000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "gauge_spin"
  )

  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.4f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1000, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse_alpha"
  )

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 8.dp),
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .size(196.dp)
        .clickable(onClick = onTriggerBoost),
      contentAlignment = Alignment.Center
    ) {
      // Background track circle
      Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 3.dp.toPx()
        drawCircle(
          color = CyanPrimary.copy(alpha = 0.15f),
          style = Stroke(width = strokeWidth)
        )
      }

      // Animated rotating sweep arc
      Canvas(
        modifier = Modifier
          .fillMaxSize()
          .padding(8.dp)
      ) {
        val sweepStroke = 4.5.dp.toPx()
        drawArc(
          brush = Brush.sweepGradient(
            colors = listOf(
              Color.Transparent,
              CyanPrimary.copy(alpha = 0.3f),
              CyanPrimary,
              CyanGlow
            )
          ),
          startAngle = rotation,
          sweepAngle = 260f,
          useCenter = false,
          style = Stroke(width = sweepStroke, cap = StrokeCap.Round)
        )
      }

      // Center text: 98% Optimized
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Text(
          text = "$score%",
          color = CyanPrimary,
          fontSize = 42.sp,
          fontWeight = FontWeight.Light,
          letterSpacing = (-1).sp
        )
        Text(
          text = if (isBoosting) "OPTIMIZING..." else "OPTIMIZED",
          color = TextSlate400,
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 1.5.sp
        )
      }
    }

    // Bottom floating pill: Stable Connection with pulse dot
    FrostedGlassBox(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .offset(y = 12.dp)
        .padding(horizontal = 16.dp, vertical = 6.dp),
      shape = RoundedCornerShape(20.dp),
      backgroundColor = GlassSurfaceElevated,
      borderColor = GlassBorderHighlight
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Box(
          modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(AccentEmerald.copy(alpha = pulseAlpha))
        )
        Text(
          text = connectionStatus,
          color = AccentEmerald,
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium
        )
      }
    }
  }
}

@Composable
private fun GameCardSection(
  targetFps: Int,
  pingMs: Int,
  isBoosting: Boolean,
  onLaunch: () -> Unit
) {
  FrostedGlassBox(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("game_card_efootball"),
    shape = RoundedCornerShape(28.dp),
    backgroundColor = GlassSurface,
    borderColor = GlassBorder
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // App soccer icon badge with vibrant indigo-to-blue gradient
      Box(
        modifier = Modifier
          .size(56.dp)
          .clip(RoundedCornerShape(18.dp))
          .background(
            brush = Brush.linearGradient(
              colors = listOf(Color(0xFF4F46E5), Color(0xFF1D4ED8))
            )
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.SportsSoccer,
          contentDescription = "eFootball",
          tint = Color.White,
          modifier = Modifier.size(32.dp)
        )
      }

      // Title & real-time gaming indicators
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "eFootball™ 2027",
          color = TextSlate100,
          fontSize = 17.sp,
          fontWeight = FontWeight.SemiBold
        )
        Row(
          modifier = Modifier.padding(top = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Speed,
              contentDescription = "Target Frame Rate",
              tint = CyanPrimary,
              modifier = Modifier.size(15.dp)
            )
            Text(
              text = "$targetFps FPS",
              color = TextSlate300,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
          ) {
            Icon(
              imageVector = Icons.Default.NetworkPing,
              contentDescription = "Network Latency",
              tint = CyanPrimary,
              modifier = Modifier.size(15.dp)
            )
            Text(
              text = "$pingMs ms",
              color = TextSlate300,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }
      }

      // Action Button: Solid glowing Cyan
      Button(
        onClick = onLaunch,
        colors = ButtonDefaults.buttonColors(
          containerColor = CyanPrimary,
          contentColor = DarkBackground
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .testTag("launch_game_button")
          .height(38.dp)
      ) {
        Text(
          text = if (isBoosting) "BOOSTING" else "LAUNCH",
          fontWeight = FontWeight.ExtraBold,
          fontSize = 12.sp,
          letterSpacing = 0.5.sp
        )
      }
    }
  }
}

@Composable
private fun FeatureGridSection(
  state: BoosterState,
  onToggleFeature: (String) -> Unit
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      FeatureCard(
        title = "Network Tunnel",
        description = if (state.isNetworkTunnelEnabled) "Prioritizing UDP game packets" else "Standard routing",
        icon = Icons.Default.WifiTethering,
        iconTint = AccentIndigo,
        isActive = state.isNetworkTunnelEnabled,
        onClick = { onToggleFeature("tunnel") },
        testTag = "feature_network_tunnel",
        modifier = Modifier.weight(1f)
      )

      FeatureCard(
        title = "RAM Purge",
        description = if (state.isRamPurgeEnabled) "${state.ramPurgedMb / 1000.0} GB cache cleared" else "Normal memory allocation",
        icon = Icons.Default.Memory,
        iconTint = AccentAmber,
        isActive = state.isRamPurgeEnabled,
        onClick = { onToggleFeature("ram") },
        testTag = "feature_ram_purge",
        modifier = Modifier.weight(1f)
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      FeatureCard(
        title = "Focus Mode",
        description = if (state.isFocusModeEnabled) "Notifications suppressed" else "Alerts active",
        icon = Icons.Default.DoNotDisturbOn,
        iconTint = AccentRose,
        isActive = state.isFocusModeEnabled,
        onClick = { onToggleFeature("focus") },
        testTag = "feature_focus_mode",
        modifier = Modifier.weight(1f)
      )

      FeatureCard(
        title = "CPU Cooler",
        description = if (state.isCpuCoolerEnabled) "Throttling prevented" else "Standard thermal governor",
        icon = Icons.Default.Thermostat,
        iconTint = AccentEmerald,
        isActive = state.isCpuCoolerEnabled,
        onClick = { onToggleFeature("cooler") },
        testTag = "feature_cpu_cooler",
        modifier = Modifier.weight(1f)
      )
    }
  }
}

@Composable
private fun FeatureCard(
  title: String,
  description: String,
  icon: ImageVector,
  iconTint: Color,
  isActive: Boolean,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier
) {
  FrostedGlassBox(
    modifier = modifier
      .testTag(testTag)
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(26.dp),
    backgroundColor = if (isActive) GlassSurface else GlassSurface.copy(alpha = 0.04f),
    borderColor = if (isActive) GlassBorder else GlassBorder.copy(alpha = 0.05f)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = if (isActive) iconTint else TextSlate400,
          modifier = Modifier.size(24.dp)
        )
        Box(
          modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (isActive) iconTint else Color.Transparent)
            .border(
              width = 1.dp,
              color = if (isActive) iconTint else TextSlate400.copy(alpha = 0.4f),
              shape = CircleShape
            )
        )
      }

      Text(
        text = title,
        color = TextSlate100,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 2.dp)
      )

      Text(
        text = description,
        color = TextSlate400,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        maxLines = 2
      )
    }
  }
}
