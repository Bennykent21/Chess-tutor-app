package com.chesstutor.app.ui.coach

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesstutor.app.domain.VerifiedConsequence
import com.chesstutor.app.ui.components.AcademyCard
import com.chesstutor.app.ui.components.ChessBoard
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.ChessTutorTypography
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel

@Composable
fun CoachScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChessTutorColors.Background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Dynamic Session Header Card
        AcademyCard(sectionLabel = state.activeCoachCategory) {
            Column {
                Text(
                    text = state.activeCoachTitle,
                    style = ChessTutorTypography.titleLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.activeCoachSubtitle,
                    style = ChessTutorTypography.bodyMedium,
                    color = ChessTutorColors.TextSecondary
                )
                if (state.activeCoachRecommendedMove != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.playActivePrincipleMove() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChessTutorColors.SurfaceElevated,
                            contentColor = ChessTutorColors.Primary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.border(1.dp, ChessTutorColors.Border, RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = ChessTutorColors.Primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Demonstrate Principle Move (${state.activeCoachRecommendedMove})",
                            style = ChessTutorTypography.labelSmall,
                            color = ChessTutorColors.Primary
                        )
                    }
                }
            }
        }

        // The Interactive Chess Board with distinct frame and elevation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ChessTutorColors.SurfaceElevated)
                .border(1.5.dp, ChessTutorColors.Border, RoundedCornerShape(16.dp))
                .padding(6.dp)
        ) {
            ChessBoard(
                fen = state.fen,
                selectedSquare = state.selectedSquare,
                legalTargets = state.legalTargets,
                lastMove = state.lastMove,
                recommendedArrow = state.recommendedArrow,
                onSquareTapped = { square -> viewModel.onSquareTapped(square) }
            )
        }

        // Verified Consequence / Mistake Banner
        AnimatedVisibility(
            visible = state.mistakeDetected,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
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
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified Mistake",
                            tint = ChessTutorColors.Blunder
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "VERIFIED MISTAKE DETECTED",
                            style = ChessTutorTypography.labelSmall,
                            color = ChessTutorColors.Blunder
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.assessment?.coachingLabel
                            ?: "You missed a forced checkmate in 1! Concrete fact verified.",
                        style = ChessTutorTypography.titleMedium,
                        color = ChessTutorColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "This position has been queued in your Spaced Repetition Review queue.",
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
                        Text("Guided Retry (Reset Position)")
                    }
                }
            }
        }

        // Status or Instruction text
        AcademyCard(sectionLabel = "Coach Feedback") {
            Text(
                text = state.message.ifBlank { "Study the position and find the winning line." },
                style = ChessTutorTypography.titleMedium
            )

            // Hint Ladder Box
            if (state.hintLevel > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ChessTutorColors.SurfaceElevated)
                        .border(1.dp, ChessTutorColors.Primary.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "HINT LADDER: LEVEL ${state.hintLevel} OF 4",
                            style = ChessTutorTypography.labelSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.hintText,
                            style = ChessTutorTypography.bodyMedium,
                            color = ChessTutorColors.TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { viewModel.showHint() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChessTutorColors.SurfaceElevated,
                        contentColor = ChessTutorColors.Primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder(true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(ChessTutorColors.Border)
                    )
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hint Ladder (${state.hintLevel}/4)")
                }

                if (state.canRetryMistake) {
                    Button(
                        onClick = { viewModel.retryMistake() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChessTutorColors.Primary,
                            contentColor = ChessTutorColors.Background
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry")
                    }
                }
            }
        }

        // Preset Practice Exercises
        AcademyCard(sectionLabel = "Practice Scenarios") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScenarioRow("Scholar's Mate Missed", "White has Queen on f3, Black King open") {
                    viewModel.loadCoachPosition(
                        fen = AppViewModel.FEN_MATE_IN_ONE,
                        title = "Missed Forced Mate (Scholar's Mate)",
                        subtitle = "White Queen delivers immediate mate on f7.",
                        category = "PRACTICE SCENARIO",
                        recommendedMoveUci = "f3f7"
                    )
                }
                ScenarioRow("Back-Rank Decoy", "Unprotected 8th rank mate in 1") {
                    viewModel.loadCoachPosition(
                        fen = AppViewModel.FEN_BACK_RANK_MATE,
                        title = "Back-Rank Checkmate Decoy",
                        subtitle = "Rook penetrates 8th rank with trapped King.",
                        category = "PRACTICE SCENARIO",
                        recommendedMoveUci = "d1d8"
                    )
                }
                ScenarioRow("Hanging Piece Trap", "Overextended bishop undefended") {
                    viewModel.loadCoachPosition(
                        fen = AppViewModel.FEN_HANGING_PIECE,
                        title = "Hanging Piece Vulnerability",
                        subtitle = "Overextended bishop has zero defenders.",
                        category = "PRACTICE SCENARIO",
                        recommendedMoveUci = "d8d4"
                    )
                }
                ScenarioRow("Royal Fork Tactic", "Knight on e5 fork target") {
                    viewModel.loadCoachPosition(
                        fen = AppViewModel.FEN_FORK_TACTIC,
                        title = "Royal Knight Fork",
                        subtitle = "Delivering double threats with knight.",
                        category = "PRACTICE SCENARIO",
                        recommendedMoveUci = "e5f7"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ScenarioRow(title: String, subtitle: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ChessTutorColors.SurfaceElevated)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column {
            Text(text = title, style = ChessTutorTypography.titleMedium)
            Text(text = subtitle, style = ChessTutorTypography.bodyMedium, color = ChessTutorColors.TextSecondary)
        }
    }
}
