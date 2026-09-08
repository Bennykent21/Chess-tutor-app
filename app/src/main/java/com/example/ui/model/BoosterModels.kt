package com.example.ui.model

enum class NavTab(val label: String) {
  BOOST("BOOST"),
  GAMES("GAMES"),
  MONITOR("MONITOR"),
  S22_TIPS("S22 TIPS")
}

data class PerformanceTelemetryPoint(
  val timestampSeconds: Int,
  val fps: Float,
  val cpuUtilization: Float, // 0.0 - 100.0%
  val gpuUtilization: Float, // 0.0 - 100.0%
  val temperatureCelsius: Float // e.g. 38.4
)

data class BoosterState(
  val deviceModel: String = "Samsung Galaxy S22",
  val optimizationScore: Int = 98,
  val isBoosting: Boolean = false,
  val boostProgress: Float = 0.98f,
  val connectionStatus: String = "Stable Connection",
  val pingMs: Int = 24,
  val jitterMs: Int = 2,
  val packetLossPercent: Float = 0.0f,
  val targetFps: Int = 60,
  val thermalTempCelsius: Float = 36.8f,
  val thermalLevelText: String = "Normal (No Throttling)",
  val ramUsedGb: Float = 5.6f,
  val ramTotalGb: Float = 8.0f,
  val ramPurgedMb: Int = 2400,
  val isNetworkTunnelEnabled: Boolean = true,
  val isRamPurgeEnabled: Boolean = true,
  val isFocusModeEnabled: Boolean = true,
  val isCpuCoolerEnabled: Boolean = true,
  val lastBoostTimestamp: String = "Just now",
  // Real-time gameplay performance monitor telemetry
  val currentFps: Float = 59.4f,
  val currentCpuPercent: Float = 48.2f,
  val currentGpuPercent: Float = 62.5f,
  val currentTempCelsius: Float = 37.2f,
  val isLiveMonitoringActive: Boolean = true,
  val activeGameSessionName: String = "eFootball™ 2027",
  val matchPhase: String = "2nd Half - In Match",
  val historyPoints: List<PerformanceTelemetryPoint> = emptyList()
)

data class GameProfile(
  val id: String,
  val name: String,
  val packageName: String,
  val targetFps: Int,
  val pingMs: Int,
  val graphicsPreset: String,
  val isSelected: Boolean = false
)

data class S22OptimizationTip(
  val id: String,
  val title: String,
  val subtitle: String,
  val detailedSteps: String,
  val benefit: String,
  val isRecommended: Boolean = true
)
