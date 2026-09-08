package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.model.PerformanceTelemetryPoint
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderHighlight
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.TextSlate100
import com.example.ui.theme.TextSlate300
import com.example.ui.theme.TextSlate400
import com.example.ui.theme.TextSlate500

enum class TelemetryMetricType(val title: String, val unit: String, val maxScale: Float, val minScale: Float, val color: Color) {
  FPS("Frame Rate", "FPS", 65f, 40f, CyanPrimary),
  CPU("CPU Utilization", "%", 100f, 0f, AccentAmber),
  GPU("GPU Utilization", "%", 100f, 0f, AccentIndigo),
  TEMPERATURE("Device Temp", "°C", 45f, 30f, AccentEmerald)
}

@Composable
fun TelemetryGraphCard(
  history: List<PerformanceTelemetryPoint>,
  modifier: Modifier = Modifier
) {
  var selectedMetric by remember { mutableStateOf(TelemetryMetricType.FPS) }

  FrostedGlassBox(
    modifier = modifier
      .fillMaxWidth()
      .testTag("telemetry_graph_card"),
    shape = RoundedCornerShape(26.dp),
    backgroundColor = GlassSurface,
    borderColor = GlassBorderHighlight
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Header and Metric Selector Tabs
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "HISTORICAL MATCH TELEMETRY",
            color = TextSlate400,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
          )
          Text(
            text = "${selectedMetric.title} Dynamics",
            color = TextSlate100,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp)
          )
        }

        val currentValue = when (selectedMetric) {
          TelemetryMetricType.FPS -> "${history.lastOrNull()?.fps ?: 60f} FPS"
          TelemetryMetricType.CPU -> "${history.lastOrNull()?.cpuUtilization ?: 48f}%"
          TelemetryMetricType.GPU -> "${history.lastOrNull()?.gpuUtilization ?: 62f}%"
          TelemetryMetricType.TEMPERATURE -> "${history.lastOrNull()?.temperatureCelsius ?: 37f}°C"
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(selectedMetric.color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
          Text(
            text = currentValue,
            color = selectedMetric.color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }

      // Filter chips
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        TelemetryMetricType.values().forEach { metric ->
          val isSelected = metric == selectedMetric
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .background(if (isSelected) metric.color.copy(alpha = 0.22f) else Color(0x14FFFFFF))
              .clickable { selectedMetric = metric }
              .padding(vertical = 7.dp)
              .testTag("metric_chip_${metric.name.lowercase()}"),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = metric.name,
              color = if (isSelected) metric.color else TextSlate400,
              fontSize = 11.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
          }
        }
      }

      // Live Interactive Canvas Graph
      PerformanceLineChart(
        history = history,
        metric = selectedMetric,
        modifier = Modifier
          .fillMaxWidth()
          .height(160.dp)
      )

      // Graph Legend & Analysis Footer
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0x12FFFFFF))
          .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(selectedMetric.color)
          )
          Text(
            text = "60-sec rolling match window",
            color = TextSlate300,
            fontSize = 11.sp
          )
        }

        val avgValue = if (history.isNotEmpty()) {
          val sum = history.map {
            when (selectedMetric) {
              TelemetryMetricType.FPS -> it.fps
              TelemetryMetricType.CPU -> it.cpuUtilization
              TelemetryMetricType.GPU -> it.gpuUtilization
              TelemetryMetricType.TEMPERATURE -> it.temperatureCelsius
            }
          }.sum()
          Math.round((sum / history.size) * 10) / 10f
        } else 0f

        Text(
          text = "Avg: $avgValue ${selectedMetric.unit}",
          color = TextSlate400,
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium
        )
      }
    }
  }
}

@Composable
fun PerformanceLineChart(
  history: List<PerformanceTelemetryPoint>,
  metric: TelemetryMetricType,
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier) {
    if (history.size < 2) return@Canvas

    val width = size.width
    val height = size.height
    val paddingBottom = 24f
    val paddingTop = 12f
    val graphHeight = height - paddingBottom - paddingTop

    // Draw grid guide lines (3 horizontal dashed guides)
    val gridCount = 3
    for (i in 0..gridCount) {
      val y = paddingTop + (graphHeight / gridCount) * i
      drawLine(
        color = Color(0x1AFFFFFF),
        start = Offset(0f, y),
        end = Offset(width, y),
        strokeWidth = 1.dp.toPx()
      )
    }

    // Map points to canvas coordinates
    val minVal = metric.minScale
    val maxVal = metric.maxScale
    val stepX = width / (history.size - 1)

    val points = history.mapIndexed { index, item ->
      val rawVal = when (metric) {
        TelemetryMetricType.FPS -> item.fps
        TelemetryMetricType.CPU -> item.cpuUtilization
        TelemetryMetricType.GPU -> item.gpuUtilization
        TelemetryMetricType.TEMPERATURE -> item.temperatureCelsius
      }
      val normalized = ((rawVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
      val x = index * stepX
      val y = paddingTop + graphHeight * (1f - normalized)
      Offset(x, y)
    }

    // Draw gradient area under the curve
    val fillPath = Path().apply {
      moveTo(points.first().x, height - paddingBottom)
      lineTo(points.first().x, points.first().y)
      for (i in 1 until points.size) {
        val prev = points[i - 1]
        val curr = points[i]
        val cX = (prev.x + curr.x) / 2f
        cubicTo(cX, prev.y, cX, curr.y, curr.x, curr.y)
      }
      lineTo(points.last().x, height - paddingBottom)
      close()
    }

    drawPath(
      path = fillPath,
      brush = Brush.verticalGradient(
        colors = listOf(
          metric.color.copy(alpha = 0.35f),
          metric.color.copy(alpha = 0.02f)
        ),
        startY = paddingTop,
        endY = height - paddingBottom
      )
    )

    // Draw main line path
    val strokePath = Path().apply {
      moveTo(points.first().x, points.first().y)
      for (i in 1 until points.size) {
        val prev = points[i - 1]
        val curr = points[i]
        val cX = (prev.x + curr.x) / 2f
        cubicTo(cX, prev.y, cX, curr.y, curr.x, curr.y)
      }
    }

    drawPath(
      path = strokePath,
      color = metric.color,
      style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
    )

    // Draw glowing pulsing dot at the latest data point
    val lastPoint = points.last()
    drawCircle(
      color = metric.color.copy(alpha = 0.35f),
      radius = 7.dp.toPx(),
      center = lastPoint
    )
    drawCircle(
      color = Color.White,
      radius = 3.5.dp.toPx(),
      center = lastPoint
    )
    drawCircle(
      color = metric.color,
      radius = 2.5.dp.toPx(),
      center = lastPoint
    )
  }
}
