package com.chesstutor.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesstutor.app.domain.ReviewScheduler
import com.chesstutor.app.ui.components.ChessBoard
import com.chesstutor.app.ui.components.EvalBar
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.bouncyClickable
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel
import java.time.Instant

@Composable
fun ReviewScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    var isBoardFlipped by remember { mutableStateOf(false) }

    val dueReviews = state.reviews.filter { ReviewScheduler.isDue(it, Instant.now()) }
    val activeItem = state.activeReviewItem ?: dueReviews.firstOrNull() ?: state.reviews.firstOrNull()

    if (activeItem == null) {
        // Empty State: All caught up (§6.2, §6.3)
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(ChessTutorColors.Background)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(ChessTutorColors.SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = ChessTutorColors.Accent,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "All Caught Up",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChessTutorColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "No mistakes currently due for review.",
                fontSize = 14.sp,
                color = ChessTutorColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = { viewModel.loadSampleMistakeForReview() },
                modifier = Modifier
                    .height(44.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ChessTutorColors.Accent
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(ChessTutorColors.Border)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Practice Sample Mistake",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    } else {
        // Active Review View: Structurally identical to Train screen (§6.2, §6.3)
        val currentIndex = state.reviews.indexOfFirst { it.id == activeItem.id }.coerceAtLeast(0)
        val totalCount = state.reviews.size

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(ChessTutorColors.Background)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Header: "Review Mistake", "1 of 4 due for review"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Review Mistake",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ChessTutorColors.TextPrimary
                    )
                    Text(
                        text = "${currentIndex + 1} of $totalCount in queue",
                        fontSize = 13.sp,
                        color = ChessTutorColors.TextSecondary
                    )
                }

                IconButton(
                    onClick = { isBoardFlipped = !isBoardFlipped },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Flip Board",
                        tint = ChessTutorColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. The Hero: Chess Board with Left Eval Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EvalBar(
                    centipawns = state.evaluationCp,
                    mateIn = state.mateIn,
                    modifier = Modifier
                        .width(28.dp)
                        .fillMaxHeight()
                        .padding(end = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, ChessTutorColors.Border, RoundedCornerShape(8.dp))
                ) {
                    ChessBoard(
                        fen = state.fen,
                        selectedSquare = state.selectedSquare,
                        legalTargets = state.legalTargets,
                        lastMove = state.lastMove,
                        recommendedArrow = state.recommendedArrow,
                        flipped = isBoardFlipped,
                        onSquareTapped = { square ->
                            viewModel.onSquareTapped(square)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Task Line: "Find the move you missed" / feedback (§6.2)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (state.reviewSolved) {
                        "Correct! Spaced repetition updated."
                    } else if (state.message.contains("Incorrect", ignoreCase = true)) {
                        "Incorrect move. Try again!"
                    } else {
                        "Find the move you missed"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (state.reviewSolved) {
                        ChessTutorColors.Best
                    } else if (state.message.contains("Incorrect", ignoreCase = true)) {
                        ChessTutorColors.Mistake
                    } else {
                        ChessTutorColors.TextPrimary
                    },
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Bottom Actions: [ 💡 Hint ] and [ Skip → ] / [ Next Mistake → ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.reviewSolved) {
                    // When solved: Next Mistake button
                    Button(
                        onClick = {
                            val nextIndex = (currentIndex + 1) % state.reviews.size
                            viewModel.selectReviewItem(nextIndex)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChessTutorColors.Accent,
                            contentColor = ChessTutorColors.Background
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Next Mistake",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    val hintLabel = when (state.hintLevel) {
                        1 -> "Hint 1/3"
                        2 -> "Hint 2/3"
                        3 -> "Hint 3/3"
                        else -> "Hint"
                    }

                    OutlinedButton(
                        onClick = { viewModel.showHint() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ChessTutorColors.Accent
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(ChessTutorColors.Border)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = ChessTutorColors.Accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = hintLabel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.TextPrimary
                        )
                    }

                    Button(
                        onClick = {
                            val nextIndex = (currentIndex + 1) % state.reviews.size
                            viewModel.selectReviewItem(nextIndex)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChessTutorColors.SurfaceElevated,
                            contentColor = ChessTutorColors.TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Skip",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = ChessTutorColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}
