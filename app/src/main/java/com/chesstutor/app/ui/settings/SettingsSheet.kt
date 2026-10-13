package com.chesstutor.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.TabularTextStyle
import com.chesstutor.app.ui.theme.bouncyClickable
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
    var showResetConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = ChessTutorColors.SurfaceRaised,
            titleContentColor = ChessTutorColors.TextPrimary,
            textContentColor = ChessTutorColors.TextSecondary,
            title = {
                Text(
                    text = "Reset training progress?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "This will clear your completed lessons and solved drill history.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        viewModel.resetCurriculumProgress()
                    }
                ) {
                    Text(
                        text = "Reset",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ChessTutorColors.Coral
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(
                        text = "Cancel",
                        fontSize = 14.sp,
                        color = ChessTutorColors.TextSecondary
                    )
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ChessTutorColors.SurfaceRaised,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 2.dp)
                    .size(width = 34.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(ChessTutorColors.LineStrong)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChessTutorColors.TextPrimary
            )
            Text(
                text = "Board preferences, connected accounts, and engine diagnostics",
                fontSize = 14.sp,
                color = ChessTutorColors.TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            SettingToggleRow(
                title = "Sound effects",
                subtitle = "Move, capture, check, and puzzle cues",
                checked = state.isSoundEnabled,
                onCheckedChange = viewModel::setSoundEnabled,
                testTag = "setting_sound_toggle"
            )

            SettingToggleRow(
                title = "Legal move dots",
                subtitle = "Show target squares when a piece is selected",
                checked = state.showLegalDots,
                onCheckedChange = viewModel::setShowLegalDots,
                testTag = "setting_legal_dots_toggle"
            )

            SettingToggleRow(
                title = "Board coordinates",
                subtitle = "Files a–h and ranks 1–8 along the edge",
                checked = state.showCoordinates,
                onCheckedChange = viewModel::setShowCoordinates,
                testTag = "setting_coordinates_toggle"
            )

            // Connected Account Section
            val linked = state.linkedProfile
            if (linked != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ChessTutorColors.Surface)
                        .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${linked.platform.displayName} · @${linked.username}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.TextPrimary
                        )
                        Text(
                            text = "Active Rating: ${linked.activeRating ?: state.estimatedRating}",
                            fontSize = 13.sp,
                            style = TabularTextStyle,
                            color = ChessTutorColors.TextSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ChessTutorColors.SurfaceRaised)
                            .border(1.dp, ChessTutorColors.LineStrong, RoundedCornerShape(8.dp))
                            .bouncyClickable { viewModel.unlinkRatingAccount() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Unlink",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.Coral
                        )
                    }
                }
            }

            // S10: Engine Self-Test Diagnostics
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ChessTutorColors.Surface)
                    .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Engine Self-Test (UCI)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.TextPrimary
                        )
                        Text(
                            text = "Verify uciok, readyok, depth 8 search, bestmove, and nodes/sec",
                            fontSize = 13.sp,
                            color = ChessTutorColors.TextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(ChessTutorColors.Brass)
                            .testTag("engine_self_test_button")
                            .bouncyClickable(enabled = !state.isRunningEngineSelfTest) {
                                viewModel.runEngineSelfTest()
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.isRunningEngineSelfTest) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = ChessTutorColors.BrassInk,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Run Test",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ChessTutorColors.BrassInk
                            )
                        }
                    }
                }

                if (!state.engineSelfTestReport.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ChessTutorColors.Background)
                            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                            .testTag("engine_self_test_report")
                    ) {
                        Text(
                            text = state.engineSelfTestReport,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            style = TabularTextStyle,
                            color = ChessTutorColors.TextPrimary
                        )
                    }
                }
            }

            // Reset progress
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ChessTutorColors.Surface)
                    .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(10.dp))
                    .testTag("reset_progress_button")
                    .bouncyClickable { showResetConfirm = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Reset Training Progress",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = ChessTutorColors.Coral
                )
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ChessTutorColors.Surface)
            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChessTutorColors.TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = ChessTutorColors.TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = ChessTutorColors.Background,
                checkedTrackColor = ChessTutorColors.Brass,
                uncheckedThumbColor = ChessTutorColors.TextSecondary,
                uncheckedTrackColor = ChessTutorColors.SurfaceHighest,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
