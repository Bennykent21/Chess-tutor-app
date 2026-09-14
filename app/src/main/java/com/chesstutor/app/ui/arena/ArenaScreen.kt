package com.chesstutor.app.ui.arena

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chesstutor.app.ui.components.AcademyCard
import com.chesstutor.app.ui.components.ChessBoard
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.ChessTutorTypography
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel

@Composable
fun ArenaScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val difficulties = listOf("Beginner", "Casual", "Intermediate", "Advanced")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChessTutorColors.Background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AcademyCard(sectionLabel = "Engine Arena") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Stockfish Sparring",
                        style = ChessTutorTypography.titleLarge
                    )
                    Text(
                        text = "Real-time blunder classification on every move.",
                        style = ChessTutorTypography.bodyMedium,
                        color = ChessTutorColors.TextSecondary
                    )
                }

                Button(
                    onClick = { viewModel.resetArenaGame() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChessTutorColors.SurfaceElevated,
                        contentColor = ChessTutorColors.Primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "New Game")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "PRESET BOT TIERS (FALLBACK)", style = ChessTutorTypography.labelSmall)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                difficulties.forEach { diff ->
                    val selected = !state.useLinkedRatingForBot && state.arenaDifficulty == diff
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.setArenaDifficulty(diff) },
                        label = { Text(diff) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ChessTutorColors.Primary,
                            selectedLabelColor = ChessTutorColors.Background,
                            containerColor = ChessTutorColors.SurfaceElevated,
                            labelColor = ChessTutorColors.TextPrimary
                        )
                    )
                }
            }
        }

        // Rating Linking and Bot Tuning
        RatingLinkCard(
            state = state,
            viewModel = viewModel
        )

        // Live Board
        ChessBoard(
            fen = state.fen,
            selectedSquare = state.selectedSquare,
            legalTargets = state.legalTargets,
            lastMove = state.lastMove,
            recommendedArrow = state.recommendedArrow,
            onSquareTapped = { square -> viewModel.onSquareTapped(square) }
        )

        // Real-time Blunder Alert
        AnimatedVisibility(visible = state.mistakeDetected) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x22EF4444))
                    .border(1.dp, ChessTutorColors.Blunder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Blunder Detected",
                            tint = ChessTutorColors.Blunder
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BLUNDER CLASSIFIED BY ENGINE",
                            style = ChessTutorTypography.labelSmall,
                            color = ChessTutorColors.Blunder
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.assessment?.coachingLabel ?: "Significant evaluation loss detected.",
                        style = ChessTutorTypography.titleMedium,
                        color = ChessTutorColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Saved to your Review Queue for spaced review.",
                        style = ChessTutorTypography.bodyMedium,
                        color = ChessTutorColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.retryMistake() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChessTutorColors.Primary,
                            contentColor = ChessTutorColors.Background
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry Move")
                    }
                }
            }
        }

        // Match Info
        AcademyCard(sectionLabel = "Game Status") {
            Text(
                text = if (state.busy) "Stockfish is calculating response..." else state.arenaStatusText.ifBlank { state.message },
                style = ChessTutorTypography.bodyMedium,
                color = ChessTutorColors.TextPrimary
            )

            if (state.evaluationCp != null || state.mateIn != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val evalText = if (state.mateIn != null) "Mate in ${state.mateIn}" else "${(state.evaluationCp ?: 0) / 100.0} cp"
                Text(
                    text = "Raw Engine Evaluation: $evalText",
                    style = ChessTutorTypography.labelSmall,
                    color = ChessTutorColors.Primary
                )
            }
        }

        // Engine Diagnostics & Smoke Test
        AcademyCard(sectionLabel = "Engine Diagnostics (Smoke Test)") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Verify subprocess execution, UCI pipes, depth, and evaluation.",
                    style = ChessTutorTypography.bodyMedium,
                    color = ChessTutorColors.TextSecondary
                )

                Button(
                    onClick = { viewModel.runEngineDiagnostics() },
                    enabled = !state.isRunningDiagnostics,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChessTutorColors.SurfaceElevated,
                        contentColor = ChessTutorColors.Primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (state.isRunningDiagnostics) "Running 1000ms Analysis..." else "Run Engine Smoke Test")
                }

                state.engineDiagnostics?.let { diag ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ChessTutorColors.SurfaceElevated, RoundedCornerShape(8.dp))
                            .border(1.dp, ChessTutorColors.Border, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Engine: ${diag.engineName}",
                            style = ChessTutorTypography.titleMedium,
                            color = ChessTutorColors.Primary
                        )
                        Text(
                            text = "Status: ${if (diag.isAlive) "Active (Subprocess Alive)" else "Disposed / Offline"}",
                            style = ChessTutorTypography.bodyMedium,
                            color = if (diag.isAlive) Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                        Text(
                            text = "Best Move: ${diag.bestMove}",
                            style = ChessTutorTypography.bodyMedium,
                            color = ChessTutorColors.TextPrimary
                        )
                        Text(
                            text = "Evaluation: ${diag.centipawns?.let { "$it cp" } ?: "N/A"}",
                            style = ChessTutorTypography.bodyMedium,
                            color = ChessTutorColors.TextPrimary
                        )
                        Text(
                            text = "Depth Reached: ${diag.depth ?: "N/A"}",
                            style = ChessTutorTypography.bodyMedium,
                            color = ChessTutorColors.TextPrimary
                        )
                        if (diag.pv.isNotBlank()) {
                            Text(
                                text = "Principal Variation: ${diag.pv}",
                                style = ChessTutorTypography.bodyMedium,
                                color = ChessTutorColors.TextSecondary
                            )
                        }
                        Text(
                            text = "Execution Latency: ${diag.latencyMs} ms",
                            style = ChessTutorTypography.labelSmall,
                            color = ChessTutorColors.TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
