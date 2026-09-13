package com.chesstutor.app.ui.review

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chesstutor.app.domain.ReviewItem
import com.chesstutor.app.domain.ReviewScheduler
import com.chesstutor.app.ui.components.AcademyCard
import com.chesstutor.app.ui.components.ChessBoard
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.ChessTutorTypography
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel
import java.time.Instant

@Composable
fun ReviewScreen(
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
        AcademyCard(sectionLabel = "Your Review Queue") {
            Text(
                text = "Spaced Repetition Memory Loop",
                style = ChessTutorTypography.titleLarge
            )
            Text(
                text = "Mistakes are automatically scheduled on a 1d → 3d → 7d → 14d → 30d interval. Unassisted solves advance the stage; failures reset to stage -1 (due immediately).",
                style = ChessTutorTypography.bodyMedium,
                color = ChessTutorColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Queued: ${state.reviews.size}",
                    style = ChessTutorTypography.labelSmall
                )
                val dueCount = state.reviews.count { ReviewScheduler.isDue(it, Instant.now()) }
                Text(
                    text = "Due Today: $dueCount",
                    style = ChessTutorTypography.labelSmall,
                    color = if (dueCount > 0) ChessTutorColors.Primary else ChessTutorColors.Success
                )
            }
        }

        // Horizontal Queue Carousel
        if (state.reviews.isNotEmpty()) {
            Text(text = "SELECT MISTAKE TO REVIEW", style = ChessTutorTypography.labelSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(state.reviews) { index, item ->
                    ReviewItemChip(
                        item = item,
                        isSelected = state.activeReviewIndex == index,
                        onClick = { viewModel.selectReviewItem(index) }
                    )
                }
            }
        }

        // Active Review Board
        if (state.activeReviewItem != null) {
            ChessBoard(
                fen = state.fen,
                selectedSquare = state.selectedSquare,
                legalTargets = state.legalTargets,
                lastMove = state.lastMove,
                recommendedArrow = state.recommendedArrow,
                onSquareTapped = { square -> viewModel.onSquareTapped(square) }
            )

            // Result banner
            AnimatedVisibility(visible = state.reviewSolved) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x2210B981))
                        .border(1.dp, ChessTutorColors.Success, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ChessTutorColors.Success)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SOLVED CORRECTLY!",
                                style = ChessTutorTypography.labelSmall,
                                color = ChessTutorColors.Success
                            )
                            Text(
                                text = "Advanced to Stage ${state.activeReviewItem.stage}. Interval updated in local storage.",
                                style = ChessTutorTypography.bodyMedium,
                                color = ChessTutorColors.TextPrimary
                            )
                        }
                    }
                }
            }

            // Review Controls & Hint Ladder
            AcademyCard(sectionLabel = "Review Mission") {
                Text(
                    text = state.message.ifBlank { "Play the best move to prove mastery." },
                    style = ChessTutorTypography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Original mistake: ${state.activeReviewItem.explanation}",
                    style = ChessTutorTypography.bodyMedium,
                    color = ChessTutorColors.TextSecondary
                )

                if (state.hintLevel > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Hint (${state.hintLevel}/4): ${state.hintText}",
                        style = ChessTutorTypography.bodyMedium,
                        color = ChessTutorColors.Primary
                    )
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
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Use Hint")
                    }
                }
            }
        } else {
            // Empty state
            AcademyCard(sectionLabel = "Queue Status") {
                Text(
                    text = "No Mistakes Currently Queued",
                    style = ChessTutorTypography.titleMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Play in the Coach loop or sparring in the Arena. Any missed mate-in-one or verified blunder will automatically be captured here for spaced review.",
                    style = ChessTutorTypography.bodyMedium,
                    color = ChessTutorColors.TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ReviewItemChip(
    item: ReviewItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) ChessTutorColors.Primary else ChessTutorColors.Border
    val bg = if (isSelected) ChessTutorColors.SurfaceElevated else ChessTutorColors.Surface

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = ChessTutorColors.Primary,
                    modifier = Modifier.width(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (item.stage < 0) "Stage: Immediate" else "Stage: ${item.stage} (${ReviewScheduler.intervalsDays.getOrNull(item.stage) ?: 1}d)",
                    style = ChessTutorTypography.labelSmall
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.explanation.take(28) + if (item.explanation.length > 28) "..." else "",
                style = ChessTutorTypography.bodyMedium,
                color = ChessTutorColors.TextSecondary
            )
        }
    }
}
