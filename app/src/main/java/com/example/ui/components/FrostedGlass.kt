package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface

/**
 * Fullscreen background with the frosted glass ambient radial mesh gradient
 * as defined in the design spec:
 * radial-gradient(at 0% 0%, rgba(34,211,238,0.15) 0px, transparent 50%),
 * radial-gradient(at 100% 100%, rgba(139,92,246,0.15) 0px, transparent 50%)
 */
fun Modifier.frostedMeshBackground(): Modifier = this.drawBehind {
  // Base dark background
  drawRect(color = DarkBackground)

  // Top-left cyan radial glow
  drawCircle(
    brush = Brush.radialGradient(
      colors = listOf(Color(0x2E22D3EE), Color(0x0022D3EE)),
      center = Offset(x = 0f, y = 0f),
      radius = size.width * 0.9f
    ),
    center = Offset(x = 0f, y = 0f),
    radius = size.width * 0.9f
  )

  // Bottom-right indigo/purple radial glow
  drawCircle(
    brush = Brush.radialGradient(
      colors = listOf(Color(0x298B5CF6), Color(0x008B5CF6)),
      center = Offset(x = size.width, y = size.height),
      radius = size.width * 1.1f
    ),
    center = Offset(x = size.width, y = size.height),
    radius = size.width * 1.1f
  )
}

/**
 * Translucent frosted glass card container with border stroke and rounded corners
 */
@Composable
fun FrostedGlassBox(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(24.dp),
  backgroundColor: Color = GlassSurface,
  borderColor: Color = GlassBorder,
  borderWidth: Dp = 1.dp,
  content: @Composable BoxScope.() -> Unit
) {
  Box(
    modifier = modifier
      .clip(shape)
      .background(
        brush = Brush.verticalGradient(
          colors = listOf(
            backgroundColor.copy(alpha = backgroundColor.alpha * 1.25f),
            backgroundColor.copy(alpha = backgroundColor.alpha * 0.75f)
          )
        )
      )
      .border(width = borderWidth, color = borderColor, shape = shape),
    content = content
  )
}
