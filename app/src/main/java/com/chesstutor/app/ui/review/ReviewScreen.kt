package com.chesstutor.app.ui.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesstutor.app.domain.ReviewItem
import com.chesstutor.app.domain.ReviewScheduler
import com.chesstutor.app.ui.components.ChessBoard
import com.chesstutor.app.ui.components.ChessPlayerBar
import com.chesstutor.app.ui.components.CoachSpeechBubble
import com.chesstutor.app.ui.components.EvalBar
import com.chesstutor.app.ui.components.MoveClassification
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
    val scrollState = rememberScrollState()
    var isBoardFlipped by remember { mutableStateOf(false) }

    val dueCount = state.reviews.count { ReviewScheduler.isDue(it, Instant.now()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChessTutorColors.Background)
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Top HUD: Game Review & Key Moments Overview (Chess.com Style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF312E2B))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "GAME REVIEW & KEY MOMENTS",
                    color = ChessTutorColors.Primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${state.reviews.size} Mistakes Queued • $dueCount Due Today",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Accuracy / Mastery Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ChessTutorColors.Primary)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (state.reviews.isNotEmpty()) "Stage ${state.activeReviewItem?.stage ?: 1}" else "Clean",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 2. Key Moments Carousel (Horizontal Selector)
        if (state.reviews.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(state.reviews) { index, item ->
                    val isSelected = state.activeReviewIndex == index
                    val isDue = ReviewScheduler.isDue(item, Instant.now())

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF383531) else Color(0xFF2B2926))
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) ChessTutorColors.Primary else Color(0xFF3D3A34),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .bouncyClickable { viewModel.selectReviewItem(index) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isDue) ChessTutorColors.Blunder else ChessTutorColors.Success)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Moment #${index + 1}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "(${ReviewScheduler.stageLabel(item.stage)})",
                            color = ChessTutorColors.TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // 3. Opponent / Coach Player Bar
        ChessPlayerBar(
            name = "Stockfish Reviewer",
            rating = "3200",
            isBot = true,
            isThinking = state.opponentThinking,
            statusBadge = if (state.opponentThinking) null else (if (state.activeReviewItem != null) "Due: ${ReviewScheduler.stageLabel(state.activeReviewItem.stage)}" else "Completed")
        )

        // 4. Active Review Board with EvalBar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EvalBar(
                centipawns = state.evaluationCp,
                mateIn = state.mateIn,
                isWhiteOnBottom = !isBoardFlipped,
                modifier = Modifier.fillMaxHeight()
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.5.dp, Color(0xFF383531), RoundedCornerShape(6.dp))
            ) {
                ChessBoard(
                    fen = state.fen,
                    selectedSquare = state.selectedSquare,
                    legalTargets = state.legalTargets,
                    lastMove = state.lastMove,
                    recommendedArrow = state.recommendedArrow,
                    flipped = isBoardFlipped,
                    onSquareTapped = { square -> viewModel.onSquareTapped(square) }
                )
            }
        }

        // 5. User Player Bar
        val userRatingStr = state.linkedProfile?.activeRating?.toString() ?: "1500"
        ChessPlayerBar(
            name = state.linkedProfile?.username ?: "You",
            rating = userRatingStr,
            isBot = false,
            isActiveTurn = !state.busy
        )

        // 6. Speech Bubble Feedback (Chess.com Style)
        val classification = when {
            state.reviewSolved -> MoveClassification.EXCELLENT
            state.activeReviewItem != null -> MoveClassification.MISTAKE
            else -> MoveClassification.COACH
        }

        val speechTitle = when {
            state.reviewSolved -> "KEY MOMENT SOLVED!"
            state.activeReviewItem != null -> "RETRY YOUR BLUNDER"
            else -> "ALL REVIEW DRILLS UP TO DATE"
        }

        val speechText = when {
            state.reviewSolved -> "Excellent! You found the decisive continuation. Interval spaced to the next retention milestone."
            state.activeReviewItem != null -> state.activeReviewItem.explanation
            else -> "Your memory queue is clean! Practice drills from the Academy or play bots in the Arena to detect new learning moments."
        }

        CoachSpeechBubble(
            message = speechText,
            classification = classification,
            title = speechTitle
        )

        // 7. Hint Display
        if (state.hintLevel > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF312E2B))
                    .border(1.dp, ChessTutorColors.Primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "Hint (${state.hintLevel}/4): ${state.hintText}",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }

        // 8. Bottom Action Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.showHint() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF312E2B),
                    contentColor = ChessTutorColors.Primary
                ),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3D3A34))
            ) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Hint", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            IconButton(
                onClick = { isBoardFlipped = !isBoardFlipped },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF312E2B))
                    .border(1.dp, Color(0xFF3D3A34), RoundedCornerShape(8.dp))
                    .bouncyClickable { isBoardFlipped = !isBoardFlipped }
            ) {
                Icon(Icons.Default.SwapVert, contentDescription = "Flip", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            Button(
                onClick = { viewModel.loadSampleMistakeForReview() },
                modifier = Modifier.weight(1.3f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ChessTutorColors.Primary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Load Drill", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
