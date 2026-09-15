package com.chesstutor.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.ChessTutorTypography
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    state: AppUiState,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var devToolsExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ChessTutorColors.Surface,
        contentColor = ChessTutorColors.TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Settings & Preferences",
                        style = ChessTutorTypography.titleLarge
                    )
                    Text(
                        text = "Customize tutor behavior and audio feedback",
                        style = ChessTutorTypography.bodyMedium,
                        color = ChessTutorColors.TextSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close Settings")
                }
            }

            // General Preferences
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ChessTutorColors.SurfaceElevated)
                    .border(1.dp, ChessTutorColors.Border, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "AUDIO & FEEDBACK",
                        style = ChessTutorTypography.labelSmall,
                        color = ChessTutorColors.Primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = ChessTutorColors.Primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Tactile Move Audio",
                                    style = ChessTutorTypography.titleSmall,
                                    color = ChessTutorColors.TextPrimary
                                )
                                Text(
                                    text = "Play move sound on legal drops",
                                    style = ChessTutorTypography.bodyMedium,
                                    color = ChessTutorColors.TextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = state.isSoundEnabled,
                            onCheckedChange = { viewModel.setSoundEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ChessTutorColors.Background,
                                checkedTrackColor = ChessTutorColors.Primary
                            )
                        )
                    }
                }
            }

            // Gated Developer & Diagnostics Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ChessTutorColors.SurfaceElevated)
                    .border(1.dp, ChessTutorColors.Border, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { devToolsExpanded = !devToolsExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DeveloperMode,
                                contentDescription = null,
                                tint = ChessTutorColors.Primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Developer & Diagnostics",
                                    style = ChessTutorTypography.titleSmall,
                                    color = ChessTutorColors.TextPrimary
                                )
                                Text(
                                    text = "Engine smoke test, test queue seed & reset tools",
                                    style = ChessTutorTypography.labelSmall,
                                    color = ChessTutorColors.TextSecondary
                                )
                            }
                        }
                        IconButton(
                            onClick = { devToolsExpanded = !devToolsExpanded },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (devToolsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = ChessTutorColors.Primary
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = devToolsExpanded,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Engine Smoke Test
                            Button(
                                onClick = { viewModel.runEngineDiagnostics() },
                                enabled = !state.isRunningDiagnostics,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ChessTutorColors.Surface,
                                    contentColor = ChessTutorColors.Primary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, ChessTutorColors.Border, RoundedCornerShape(10.dp))
                            ) {
                                Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (state.isRunningDiagnostics) "Running Engine Smoke Test..." else "Run Engine Smoke Test",
                                    style = ChessTutorTypography.labelSmall,
                                    color = ChessTutorColors.Primary
                                )
                            }

                            state.engineDiagnostics?.let { diag ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(ChessTutorColors.Surface, RoundedCornerShape(10.dp))
                                        .border(1.dp, ChessTutorColors.Border, RoundedCornerShape(10.dp))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Engine: ${diag.engineName}",
                                        style = ChessTutorTypography.titleSmall,
                                        color = ChessTutorColors.Primary
                                    )
                                    Text(
                                        text = "Status: ${if (diag.isAlive) "Active (Subprocess Alive)" else "Offline / Disposed"}",
                                        style = ChessTutorTypography.bodyMedium,
                                        color = if (diag.isAlive) Color(0xFF10B981) else Color(0xFFEF4444)
                                    )
                                    Text(
                                        text = "Best Move: ${diag.bestMove}",
                                        style = ChessTutorTypography.bodyMedium,
                                        color = ChessTutorColors.TextPrimary
                                    )
                                    Text(
                                        text = "Centipawns: ${diag.centipawns?.let { "$it cp" } ?: "N/A"} (Depth ${diag.depth ?: "N/A"})",
                                        style = ChessTutorTypography.bodyMedium,
                                        color = ChessTutorColors.TextPrimary
                                    )
                                    Text(
                                        text = "Execution Latency: ${diag.latencyMs} ms",
                                        style = ChessTutorTypography.labelSmall,
                                        color = ChessTutorColors.TextSecondary
                                    )
                                    diag.resolvedBinaryPath?.let { path ->
                                        Text(
                                            text = "Binary Path: $path",
                                            style = ChessTutorTypography.labelSmall,
                                            color = ChessTutorColors.TextSecondary
                                        )
                                    }
                                }
                            }

                            // Seed Sample Review Mistake
                            OutlinedButton(
                                onClick = {
                                    viewModel.loadSampleMistakeForReview()
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Seed Sample Mistake to Review Queue",
                                    style = ChessTutorTypography.labelSmall,
                                    color = ChessTutorColors.TextPrimary
                                )
                            }

                            // Reset Curriculum Progress
                            OutlinedButton(
                                onClick = { viewModel.resetCurriculumProgress() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Reset Curriculum Progress",
                                    style = ChessTutorTypography.labelSmall,
                                    color = ChessTutorColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
