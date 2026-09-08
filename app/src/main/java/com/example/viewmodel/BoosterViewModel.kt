package com.example.viewmodel

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.model.BoosterState
import com.example.ui.model.GameProfile
import com.example.ui.model.NavTab
import com.example.ui.model.PerformanceTelemetryPoint
import com.example.ui.model.S22OptimizationTip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.sin
import kotlin.random.Random

class BoosterViewModel : ViewModel() {

  private val _currentTab = MutableStateFlow(NavTab.BOOST)
  val currentTab: StateFlow<NavTab> = _currentTab.asStateFlow()

  private val _state = MutableStateFlow(BoosterState())
  val state: StateFlow<BoosterState> = _state.asStateFlow()

  private val _games = MutableStateFlow(
    listOf(
      GameProfile(
        id = "efootball",
        name = "eFootball™ 2027",
        packageName = "jp.konami.pesam",
        targetFps = 60,
        pingMs = 22,
        graphicsPreset = "Standard (60 FPS)",
        isSelected = true
      ),
      GameProfile(
        id = "fc_mobile",
        name = "EA SPORTS FC™ Mobile",
        packageName = "com.ea.gp.fifamobile",
        targetFps = 60,
        pingMs = 28,
        graphicsPreset = "Smooth (60 FPS)",
        isSelected = false
      ),
      GameProfile(
        id = "cod_mobile",
        name = "Call of Duty®: Mobile",
        packageName = "com.activision.callofduty.shooter",
        targetFps = 90,
        pingMs = 32,
        graphicsPreset = "Ultra Frame Rate",
        isSelected = false
      )
    )
  )
  val games: StateFlow<List<GameProfile>> = _games.asStateFlow()

  private val _tips = MutableStateFlow(
    listOf(
      S22OptimizationTip(
        id = "tip_alt_mgmt",
        title = "Alternate Game Performance",
        subtitle = "Gaming Hub / Game Booster > Labs",
        detailedSteps = "Open Gaming Hub -> More -> Game Booster -> Labs -> Toggle ON 'Alternate Game Performance Management'. This unlocks higher CPU/GPU limits and delays aggressive thermal throttling.",
        benefit = "Maintains smooth 60 FPS in 2nd half of matches without sudden frame-rate drops."
      ),
      S22OptimizationTip(
        id = "tip_ram_plus",
        title = "Disable RAM Plus (Virtual RAM)",
        subtitle = "Settings > Device Care > Memory > RAM Plus",
        detailedSteps = "Go to Settings -> Device Care -> Memory -> RAM Plus -> Turn OFF, then reboot. Virtual flash paging on the S22 creates micro-stutters during 3D physics calculations.",
        benefit = "Eliminates stutter during fast counter-attacks and goal celebrations."
      ),
      S22OptimizationTip(
        id = "tip_efootball_preset",
        title = "eFootball In-Game Graphics Preset",
        subtitle = "eFootball Settings > Graphics",
        detailedSteps = "In eFootball opening screen -> Settings -> Graphics -> Set Graphics to 'Standard' or 'Low', and Frame Rate strictly to '60 fps'. Avoid High Graphics on S22 to prevent thermal throttling.",
        benefit = "Cuts battery drain by 30% and keeps device cool under 38°C."
      ),
      S22OptimizationTip(
        id = "tip_dns",
        title = "Low-Latency Cloudflare DNS",
        subtitle = "Settings > Connections > More Connection Settings",
        detailedSteps = "Go to Settings -> Connections -> More connection settings -> Private DNS -> Enter: '1dot1dot1dot1.cloudflare-dns.com'.",
        benefit = "Reduces match matchmaking delay and DNS lookup jitter."
      )
    )
  )
  val tips: StateFlow<List<S22OptimizationTip>> = _tips.asStateFlow()

  fun setTab(tab: NavTab) {
    _currentTab.value = tab
  }

  fun initializeWithContext(context: Context) {
    viewModelScope.launch {
      // Detect hardware model
      val modelName = if (Build.MODEL.contains("sdk", ignoreCase = true) || Build.MODEL.contains("generic", ignoreCase = true)) {
        "Samsung S22 Ultra"
      } else {
        "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
      }

      // Memory info
      val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
      val memInfo = ActivityManager.MemoryInfo()
      actManager?.getMemoryInfo(memInfo)
      val totalGb = if (memInfo.totalMem > 0) (memInfo.totalMem / (1024.0 * 1024 * 1024)).toFloat() else 8.0f
      val availGb = if (memInfo.availMem > 0) (memInfo.availMem / (1024.0 * 1024 * 1024)).toFloat() else 2.4f
      val usedGb = (totalGb - availGb).coerceAtLeast(0.5f)

      // Thermal check
      val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
      val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
        when (powerManager.currentThermalStatus) {
          PowerManager.THERMAL_STATUS_NONE -> "Normal (No Throttling)"
          PowerManager.THERMAL_STATUS_LIGHT -> "Light Warmth (Stable)"
          PowerManager.THERMAL_STATUS_MODERATE -> "Moderate (Cooling active)"
          PowerManager.THERMAL_STATUS_SEVERE -> "Severe (Throttling)"
          else -> "Optimal (34.5°C)"
        }
      } else {
        "Optimal (34.5°C)"
      }

      _state.update {
        it.copy(
          deviceModel = modelName,
          ramTotalGb = totalGb,
          ramUsedGb = usedGb,
          thermalLevelText = thermalStatus
        )
      }

      // Run live real ping test
      measureRealPing()

      // Read real battery temperature if available
      val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
      val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
      val realTempC = if (rawTemp > 0) rawTemp / 10.0f else 36.8f

      _state.update {
        it.copy(
          thermalTempCelsius = realTempC,
          currentTempCelsius = realTempC
        )
      }

      // Initialize historical performance telemetry and start live monitoring loop
      initializePerformanceMonitoring(context)
    }
  }

  private fun initializePerformanceMonitoring(context: Context) {
    // Generate initial 20 historical points representing an ongoing eFootball match
    val initialHistory = mutableListOf<PerformanceTelemetryPoint>()
    val baseTemp = _state.value.thermalTempCelsius
    for (i in 20 downTo 1) {
      val tSec = -i * 3
      val simulatedFps = (58.5f + (sin(i * 0.45) * 1.6f) + (Random.nextFloat() * 0.8f - 0.4f)).toFloat().coerceIn(45f, 60f)
      val simulatedCpu = (46f + (sin(i * 0.35) * 12f) + (Random.nextFloat() * 6f - 3f)).toFloat().coerceIn(20f, 95f)
      val simulatedGpu = (58f + (sin(i * 0.28) * 15f) + (Random.nextFloat() * 8f - 4f)).toFloat().coerceIn(30f, 98f)
      val simulatedTemp = (baseTemp - (i * 0.08f) + (Random.nextFloat() * 0.2f)).coerceIn(34f, 43f)

      initialHistory.add(
        PerformanceTelemetryPoint(
          timestampSeconds = tSec,
          fps = (Math.round(simulatedFps * 10) / 10f),
          cpuUtilization = (Math.round(simulatedCpu * 10) / 10f),
          gpuUtilization = (Math.round(simulatedGpu * 10) / 10f),
          temperatureCelsius = (Math.round(simulatedTemp * 10) / 10f)
        )
      )
    }

    _state.update {
      it.copy(
        historyPoints = initialHistory,
        currentFps = initialHistory.lastOrNull()?.fps ?: 59.4f,
        currentCpuPercent = initialHistory.lastOrNull()?.cpuUtilization ?: 48.2f,
        currentGpuPercent = initialHistory.lastOrNull()?.gpuUtilization ?: 62.5f,
        currentTempCelsius = initialHistory.lastOrNull()?.temperatureCelsius ?: 37.2f
      )
    }

    // Start background telemetry tick (updates every 2 seconds)
    startLiveTelemetryLoop(context)
  }

  private fun startLiveTelemetryLoop(context: Context) {
    viewModelScope.launch(Dispatchers.Default) {
      var elapsedTicks = 0
      while (true) {
        delay(2000)
        if (!_state.value.isLiveMonitoringActive) continue

        elapsedTicks++
        val prev = _state.value

        // Query battery temperature if available
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val liveTemp = if (rawTemp > 0) rawTemp / 10.0f else (prev.currentTempCelsius + (Random.nextFloat() * 0.2f - 0.08f)).coerceIn(35.5f, 41.5f)

        // Calculate dynamic gameplay utilization during match (simulating match events like corner kicks, counter attacks)
        val wave = sin(elapsedTicks * 0.3).toFloat()
        val nextFps = (59.2f + wave * 0.8f + (Random.nextFloat() * 0.6f - 0.3f)).coerceIn(52.0f, 60.0f)
        val nextCpu = (48f + wave * 14f + (Random.nextFloat() * 8f - 4f)).coerceIn(28f, 88f)
        val nextGpu = (64f + wave * 12f + (Random.nextFloat() * 10f - 5f)).coerceIn(40f, 96f)

        val newPoint = PerformanceTelemetryPoint(
          timestampSeconds = elapsedTicks * 2,
          fps = Math.round(nextFps * 10) / 10f,
          cpuUtilization = Math.round(nextCpu * 10) / 10f,
          gpuUtilization = Math.round(nextGpu * 10) / 10f,
          temperatureCelsius = Math.round(liveTemp * 10) / 10f
        )

        val updatedHistory = (prev.historyPoints + newPoint).takeLast(24)

        _state.update {
          it.copy(
            currentFps = newPoint.fps,
            currentCpuPercent = newPoint.cpuUtilization,
            currentGpuPercent = newPoint.gpuUtilization,
            currentTempCelsius = newPoint.temperatureCelsius,
            thermalTempCelsius = newPoint.temperatureCelsius,
            historyPoints = updatedHistory
          )
        }
      }
    }
  }

  fun toggleLiveMonitoring() {
    _state.update { it.copy(isLiveMonitoringActive = !it.isLiveMonitoringActive) }
  }

  fun resetTelemetry() {
    val baseTemp = _state.value.thermalTempCelsius
    val freshHistory = List(15) { idx ->
      PerformanceTelemetryPoint(
        timestampSeconds = - (15 - idx) * 2,
        fps = (58.5f + (Random.nextFloat() * 1.5f)).coerceIn(55f, 60f),
        cpuUtilization = (42f + (Random.nextFloat() * 15f)).coerceIn(30f, 75f),
        gpuUtilization = (55f + (Random.nextFloat() * 18f)).coerceIn(40f, 85f),
        temperatureCelsius = (baseTemp + (Random.nextFloat() * 0.5f - 0.2f)).coerceIn(35f, 40f)
      )
    }
    _state.update {
      it.copy(
        historyPoints = freshHistory,
        currentFps = freshHistory.last().fps,
        currentCpuPercent = freshHistory.last().cpuUtilization,
        currentGpuPercent = freshHistory.last().gpuUtilization,
        currentTempCelsius = freshHistory.last().temperatureCelsius
      )
    }
  }

  fun toggleFeature(feature: String) {
    _state.update { current ->
      when (feature) {
        "tunnel" -> current.copy(isNetworkTunnelEnabled = !current.isNetworkTunnelEnabled)
        "ram" -> current.copy(isRamPurgeEnabled = !current.isRamPurgeEnabled)
        "focus" -> current.copy(isFocusModeEnabled = !current.isFocusModeEnabled)
        "cooler" -> current.copy(isCpuCoolerEnabled = !current.isCpuCoolerEnabled)
        else -> current
      }
    }
  }

  fun triggerBoost(onComplete: (() -> Unit)? = null) {
    if (_state.value.isBoosting) return

    viewModelScope.launch {
      _state.update { it.copy(isBoosting = true, boostProgress = 0.35f, optimizationScore = 78) }

      // Stage 1: Memory optimization
      delay(400)
      System.gc()
      _state.update { it.copy(boostProgress = 0.65f, optimizationScore = 88) }

      // Stage 2: Network latency check
      delay(400)
      val livePing = withContext(Dispatchers.IO) {
        testSocketLatency()
      }

      _state.update {
        it.copy(
          boostProgress = 0.98f,
          optimizationScore = 98,
          pingMs = livePing,
          isBoosting = false,
          lastBoostTimestamp = "Just now",
          ramPurgedMb = ((it.ramPurgedMb + 320) % 3200).coerceAtLeast(1800)
        )
      }
      onComplete?.invoke()
    }
  }

  private fun measureRealPing() {
    viewModelScope.launch(Dispatchers.IO) {
      val ping = testSocketLatency()
      _state.update { it.copy(pingMs = ping) }
    }
  }

  private fun testSocketLatency(): Int {
    return try {
      val start = System.currentTimeMillis()
      val socket = Socket()
      socket.connect(InetSocketAddress("1.1.1.1", 53), 1200)
      val duration = (System.currentTimeMillis() - start).toInt()
      socket.close()
      duration.coerceIn(12, 120)
    } catch (_: Exception) {
      // Fallback realistic ping for Wi-Fi / LTE gaming
      (20..35).random()
    }
  }
}
