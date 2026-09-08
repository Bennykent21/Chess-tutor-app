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

// Premium Dark Canvas with Deep Indigo-Slate Vignette
val LiquidCanvasStart = Color(0xFF090B10)
val LiquidCanvasMid = Color(0xFF0D111A)
val LiquidCanvasEnd = Color(0xFF07080C)

val LiquidCanvasBrush = Brush.verticalGradient(
  colors = listOf(LiquidCanvasStart, LiquidCanvasMid, LiquidCanvasEnd)
)

// Glass Base Surfaces (Translucent frosted acrylics)
val LiquidGlassSurface = Color(0x1AFFFFFF)          // 10% pure white translucency
val LiquidGlassSurfaceSubtle = Color(0x0EFFFFFF)    // 5.5% white translucency
val LiquidGlassSurfaceElevated = Color(0x28FFFFFF)  // 16% white translucency
val LiquidGlassSurfaceActive = Color(0x38FFFFFF)    // 22% white highlight

// Liquid Specular Highlight Rims (Borders simulating physical glass edges catching light)
val LiquidGlassBorder = Brush.verticalGradient(
  colors = listOf(
    Color(0x52FFFFFF), // 32% white top specular rim
    Color(0x18FFFFFF), // 9% mid refraction
    Color(0x08FFFFFF)  // 3% bottom falloff
  )
)

val LiquidGlassBorderGold = Brush.verticalGradient(
  colors = listOf(
    Color(0x99FBBF24), // Specular gold highlight
    Color(0x40F59E0B), // Warm amber mid
    Color(0x15F59E0B)  // Soft bottom glow
  )
)

val LiquidGlassBorderCyan = Brush.verticalGradient(
  colors = listOf(
    Color(0x9938BDF8), // Specular cyan highlight
    Color(0x400284C7), // Sky blue mid
    Color(0x150284C7)
  )
)

val LiquidGlassBorderSubtle = Brush.verticalGradient(
  colors = listOf(
    Color(0x33FFFFFF),
    Color(0x14FFFFFF),
    Color(0x08FFFFFF)
  )
)

// Liquid Glass Accent Glows (Ambient Backdrops)
val LiquidGlowAmber = Brush.radialGradient(
  colors = listOf(Color(0x33F59E0B), Color(0x00F59E0B))
)

val LiquidGlowCyan = Brush.radialGradient(
  colors = listOf(Color(0x3338BDF8), Color(0x0038BDF8))
)

val LiquidGlowPurple = Brush.radialGradient(
  colors = listOf(Color(0x28818CF8), Color(0x00818CF8))
)

/**
 * High-level helper Modifier to turn any layout element into a frosted Liquid Glass card.
 */
fun Modifier.liquidGlassCard(
  shape: Shape = RoundedCornerShape(18.dp),
  elevation: Dp = 8.dp,
  backgroundColor: Color = LiquidGlassSurface,
  borderBrush: Brush = LiquidGlassBorder,
  borderWidth: Dp = 1.dp
): Modifier = this
  .shadow(elevation, shape, ambientColor = Color(0x40000000), spotColor = Color(0x66000000))
  .clip(shape)
  .background(backgroundColor)
  .border(borderWidth, borderBrush, shape)

/**
 * Modifier for elevated interactive glass buttons & highlighted badges
 */
fun Modifier.liquidGlassPill(
  shape: Shape = RoundedCornerShape(12.dp),
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
