package com.example.chess.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass UI Design System Tokens & Modifiers.
 * High-end translucent surfaces, specular rim highlights, subtle chromatic sheen,
 * and backdrop glow for an immersive, tactile chess experience.
 */

// Clean Dark Canvas with Deep Slate
val LiquidCanvasStart = Color(0xFF0F1115)
val LiquidCanvasMid = Color(0xFF12161F)
val LiquidCanvasEnd = Color(0xFF0B0D12)

val LiquidCanvasBrush = Brush.verticalGradient(
  colors = listOf(LiquidCanvasStart, LiquidCanvasMid, LiquidCanvasEnd)
)

// Clean Card Surfaces (Solid and calm, avoiding muddy rainbow translucency)
val LiquidGlassSurface = Color(0xFF161A22)          // Crisp solid card surface
val LiquidGlassSurfaceSubtle = Color(0xFF1B202A)    // Slightly lighter card/section
val LiquidGlassSurfaceElevated = Color(0xFF222834)  // Floating card surface
val LiquidGlassSurfaceActive = Color(0xFF2B3342)    // Highlighted surface

// Clean borders with consistent tone
val LiquidGlassBorder = Brush.verticalGradient(
  colors = listOf(
    Color(0xFF2D3543),
    Color(0xFF222834)
  )
)

val LiquidGlassBorderGold = Brush.verticalGradient(
  colors = listOf(
    Color(0xFFFBBF24),
    Color(0xFFD97706)
  )
)

// Align secondary border to subtle slate rather than clashing cyan
val LiquidGlassBorderCyan = Brush.verticalGradient(
  colors = listOf(
    Color(0xFF3B4656),
    Color(0xFF262E3B)
  )
)

val LiquidGlassBorderSubtle = Brush.verticalGradient(
  colors = listOf(
    Color(0xFF2A313E),
    Color(0xFF1E242E)
  )
)

// Ambient backdrops (Restrained to single Coach warm amber)
val LiquidGlowAmber = Brush.radialGradient(
  colors = listOf(Color(0x18F59E0B), Color.Transparent)
)

val LiquidGlowCyan = Brush.radialGradient(
  colors = listOf(Color(0x0CF59E0B), Color.Transparent)
)

val LiquidGlowPurple = Brush.radialGradient(
  colors = listOf(Color(0x0CF59E0B), Color.Transparent)
)

/**
 * Clean, tactile card modifier with sharp contrast and restrained border styling.
 */
fun Modifier.liquidGlassCard(
  shape: Shape = RoundedCornerShape(16.dp),
  elevation: Dp = 2.dp,
  backgroundColor: Color = LiquidGlassSurface,
  borderBrush: Brush = LiquidGlassBorder,
  borderWidth: Dp = 1.dp
): Modifier = this
  .shadow(elevation, shape, ambientColor = Color(0x33000000), spotColor = Color(0x4D000000))
  .clip(shape)
  .background(backgroundColor)
  .border(borderWidth, borderBrush, shape)

/**
 * Clean pill/badge modifier with clear contrast
 */
fun Modifier.liquidGlassPill(
  shape: Shape = RoundedCornerShape(10.dp),
  isActive: Boolean = false,
  activeBorderBrush: Brush = LiquidGlassBorderGold
): Modifier = this
  .clip(shape)
  .background(if (isActive) Color(0x26F59E0B) else LiquidGlassSurfaceSubtle)
  .border(
    width = 1.dp,
    brush = if (isActive) activeBorderBrush else LiquidGlassBorderSubtle,
    shape = shape
  )

