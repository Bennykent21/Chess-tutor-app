package com.example.chess.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.chess.ui.theme.LiquidCanvasStart
import com.example.chess.ui.theme.LiquidCanvasMid
import com.example.chess.ui.theme.LiquidCanvasEnd

/**
 * LiquidGlassScaffoldCanvas:
 * An atmospheric, rich canvas with subtle refractive orbs (radial color bleeds)
 * underneath the UI layer, giving translucent glass cards true depth and caustic shimmer.
 */
@Composable
fun LiquidGlassCanvas(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          listOf(LiquidCanvasStart, LiquidCanvasMid, LiquidCanvasEnd)
        )
      )
  ) {
    // Atmospheric Refractive Light Bleeds for Glass Depth
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height

      // Top-right warm amber/gold atmospheric light
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(Color(0x22F59E0B), Color(0x08F59E0B), Color.Transparent),
          center = Offset(w * 0.85f, h * 0.12f),
          radius = w * 0.7f
        ),
        center = Offset(w * 0.85f, h * 0.12f),
        radius = w * 0.7f
      )

      // Center-left subtle cyan glass caustics
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(Color(0x1538BDF8), Color(0x0538BDF8), Color.Transparent),
          center = Offset(w * 0.1f, h * 0.45f),
          radius = w * 0.8f
        ),
        center = Offset(w * 0.1f, h * 0.45f),
        radius = w * 0.8f
      )

      // Bottom-right deep violet/indigo glass refraction
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(Color(0x18818CF8), Color(0x04818CF8), Color.Transparent),
          center = Offset(w * 0.75f, h * 0.85f),
          radius = w * 0.75f
        ),
        center = Offset(w * 0.75f, h * 0.85f),
        radius = w * 0.75f
      )
    }

    // Main UI content layer
    content()
  }
}
