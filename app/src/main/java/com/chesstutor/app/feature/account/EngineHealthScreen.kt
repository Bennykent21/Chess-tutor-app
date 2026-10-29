package com.chesstutor.app.feature.account

import com.chesstutor.app.design.CtDivider
import com.chesstutor.app.design.CtSectionHeader
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Check
import com.chesstutor.app.design.CtActionPage
import com.chesstutor.app.data.diagnostics.PerfSnapshot
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtIconBubble
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.engine.EngineSource
import com.chesstutor.app.engine.EngineStatus
import com.chesstutor.app.engine.NativeEngineState
import com.chesstutor.app.ui.theme.JetBrainsMonoFontFamily

/**
 * Engine health (plan section 4.5): which engine is answering, and an on-demand check
 * whose numbers all come from Stockfish's own output.
 */
@Composable
fun EngineHealthScreen(
    status: EngineStatus,
    report: String?,
    isRunning: Boolean,
    onRunCheck: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Speed measured on this phone since the app started (plan Phase 6D). */
    perf: PerfSnapshot = PerfSnapshot(),
) {
    val healthy = !status.isDegraded && status.nativeState == NativeEngineState.READY
    val (headline, detail) = when {
        healthy -> "Stockfish is running" to
            "Bots, hints and analysis use Stockfish on this phone, with its neural network."
        status.nativeState == NativeEngineState.NOT_STARTED || status.nativeState == NativeEngineState.STARTING ->
            "Starting Stockfish" to "Run the check below to start it now."
        else -> "Using the Lite engine" to
            "Stockfish is not running on this phone, so answers come from " +
            "${if (status.lastAnswerSource == EngineSource.CLOUD) "an online engine" else "the small built-in engine"}. " +
            "Bots and analysis are weaker until it works. Run the check for details."
    }

    CtActionPage(
        title = "Engine",
        onBack = onBack,
        modifier = modifier,
        bottom = { CtButton(text = if (isRunning) "Checking…" else "Run check", onClick = onRunCheck, enabled = !isRunning, modifier = Modifier.weight(1f)) },
    ) {
        Row(Modifier.padding(top = CtSpacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(48.dp).clip(CtShapes.Pill).background(if (healthy) CtColors.Success else CtColors.Error),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when {
                        healthy -> Icons.Rounded.Check
                        status.nativeState == NativeEngineState.STARTING -> Icons.Rounded.HourglassTop
                        else -> Icons.Rounded.Warning
                    },
                    contentDescription = null,
                    tint = if (healthy) CtColors.OnSuccess else CtColors.OnError,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(text = headline, style = CtType.Headline)
                Text(text = detail, style = CtType.Caption)
            }
        }
        status.problem?.let { Text(text = it, style = CtType.Caption.copy(color = CtColors.Error), modifier = Modifier.padding(top = CtSpacing.md)) }
        if (!report.isNullOrBlank()) {
            Text(
                text = report,
                style = CtType.Caption.copy(color = CtColors.Text, fontFamily = JetBrainsMonoFontFamily),
                modifier = Modifier
                    .padding(top = CtSpacing.md)
                    .fillMaxWidth()
                    .clip(CtShapes.CardSmall)
                    .background(CtColors.Surface)
                    .padding(CtSpacing.md),
            )
        }
        SpeedCard(perf)
    }
}

/** "Speed on this phone": start-up, loading, the last analysis and import, measured here (plan Phase 6D). */
@Composable
private fun SpeedCard(perf: PerfSnapshot) {
    CtSectionHeader(title = "Speed on this phone")
    speedLines(perf).forEachIndexed { i, (label, value) ->
        // Both sides get a share of the width, so a long value wraps instead of crushing its label.
        Row(Modifier.fillMaxWidth().padding(vertical = CtSpacing.sm), horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
            Text(label, style = CtType.BodySmall, modifier = Modifier.weight(0.4f))
            Text(value, style = CtType.BodySmall.copy(color = CtColors.Text), textAlign = TextAlign.End, modifier = Modifier.weight(0.6f))
        }
        if (i < speedLines(perf).lastIndex) CtDivider()
    }
}

/** The speed card's rows, in plain words. */
internal fun speedLines(perf: PerfSnapshot): List<Pair<String, String>> {
    fun ms(value: Long?): String = when {
        value == null -> "not yet"
        value < 1_000 -> "$value ms"
        else -> String.format(java.util.Locale.ROOT, "%.1f s", value / 1000.0)
    }
    val rows = mutableListOf("App start" to ms(perf.startupMillis))
    listOf("Puzzles", "Openings", "Lessons").forEach { rows += "Loading ${it.lowercase()}" to ms(perf.loads[it]) }
    rows += "Last game review" to (perf.analysis?.let { "${it.positions} positions in ${ms(it.millis)} (${ms(it.msPerPosition)} each, depth ${it.depth})" } ?: "not yet")
    rows += "Last import" to (perf.import?.let { "${it.games} games from ${it.source} in ${ms(it.millis)}" } ?: "not yet")
    return rows
}
