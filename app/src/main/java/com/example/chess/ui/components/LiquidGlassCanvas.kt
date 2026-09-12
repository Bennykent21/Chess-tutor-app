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
    // Clean, calm ambient atmosphere (single subtle warm tone)
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height

      // Subtle warm ambient vignette at top
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(Color(0x0CF59E0B), Color.Transparent),
          center = Offset(w * 0.5f, 0f),
          radius = w * 0.85f
        ),
        center = Offset(w * 0.5f, 0f),
        radius = w * 0.85f
      )
    }

    // Main UI content layer
    content()
  }
}

