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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.font.FontFamily
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

data class OpeningPerformance(
    val name: String,
    val games: Int,
    val winRate: Int,
    val needsWork: Boolean
)

val SAMPLE_OPENINGS = listOf(
    OpeningPerformance("Italian Game", 14, 71, false),
    OpeningPerformance("Queen's Gambit", 9, 56, false),
    OpeningPerformance("Ruy Lopez", 6, 50, false),
    OpeningPerformance("Sicilian Defence (Black)", 11, 27, true)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    var isBoardFlipped by remember { mutableStateOf(false) }
    var isProfileSheetOpen by remember { mutableStateOf(false) }
    val profileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val dueReviews = state.reviews.filter { ReviewScheduler.isDue(it, Instant.now()) }
    val activeItem = state.activeReviewItem ?: dueReviews.firstOrNull() ?: state.reviews.firstOrNull()
    val dueCount = dueReviews.size.coerceAtLeast(if (activeItem != null) 1 else 0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChessTutorColors.Background)
    ) {
        // ==================== HEAD ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "Review",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.015).sp,
                    color = ChessTutorColors.TextPrimary
                )
                Text(
                    text = "$dueCount positions due",
                    fontSize = 12.5.sp,
                    letterSpacing = (-0.005).sp,
                    color = ChessTutorColors.TextSecondary,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Flip Board Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .bouncyClickable { isBoardFlipped = !isBoardFlipped },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Flip Board",
                        tint = ChessTutorColors.TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Profile Button (#open-profile)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ChessTutorColors.Surface2)
                        .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(10.dp))
                        .bouncyClickable { isProfileSheetOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = ChessTutorColors.Brass,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        if (activeItem == null) {
            // Empty State: All Caught Up
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(ChessTutorColors.Surface2),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = ChessTutorColors.Brass,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "All Caught Up",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChessTutorColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "No mistakes currently due for review.",
                    fontSize = 13.sp,
                    color = ChessTutorColors.TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(ChessTutorColors.Surface2)
                        .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(11.dp))
                        .bouncyClickable { viewModel.loadSampleMistakeForReview() }
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Practice Sample Mistake",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ChessTutorColors.TextPrimary
                    )
                }
            }
        } else {
            // Active Review View
            val currentIndex = state.reviews.indexOfFirst { it.id == activeItem.id }.coerceAtLeast(0)
            val totalDots = state.reviews.size.coerceAtLeast(5)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    // Board Row: Eval Bar (22dp) + Board
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EvalBar(
                            centipawns = state.evaluationCp,
                            mateIn = state.mateIn,
                            isWhiteOnBottom = !isBoardFlipped,
                            modifier = Modifier
                                .width(22.dp)
                                .fillMaxHeight()
                                .padding(end = 8.dp)
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(0.dp))
                        ) {
                            ChessBoard(
                                fen = activeItem.fen,
                                selectedSquare = state.selectedSquare,
                                legalTargets = state.legalTargets,
                                lastMove = state.lastMove,
                                recommendedArrow = state.recommendedArrow,
                                flipped = isBoardFlipped,
                                onSquareTapped = { square ->
                                    viewModel.onReviewSquareTapped(square, activeItem)
                                }
                            )
                        }
                    }

                    // Prompt (.prompt #pr-review)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (state.reviewSolved) ChessTutorColors.Sage else ChessTutorColors.Brass)
                        )

                        val promptText = if (state.reviewSolved) {
                            "Well played! Mistake resolved."
                        } else if (state.message.isNotBlank()) {
                            state.message
                        } else {
                            "You missed a tactical strike here."
                        }

                        Text(
                            text = promptText,
                            fontSize = 14.5.sp,
                            letterSpacing = (-0.008).sp,
                            fontWeight = FontWeight.Normal,
                            color = if (state.reviewSolved) ChessTutorColors.Sage else ChessTutorColors.TextPrimary
                        )
                    }

                    Text(
                        text = "From your game against Wayne",
                        fontSize = 12.sp,
                        color = ChessTutorColors.TextTertiary,
                        modifier = Modifier.padding(start = 15.dp, top = 4.dp)
                    )
                }

                Column {
                    // Action Buttons Row: [ Hint 0/3 ] and [ Show answer ]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(ChessTutorColors.Surface2)
                                .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(11.dp))
                                .bouncyClickable { viewModel.showHint() },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = "Hint",
                                    tint = ChessTutorColors.TextPrimary,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = "Hint",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.008).sp,
                                    color = ChessTutorColors.TextPrimary
                                )
                                Text(
                                    text = "${state.hintLevel}/3",
                                    fontSize = 12.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = ChessTutorColors.TextTertiary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Color.Transparent)
                                .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(11.dp))
                                .bouncyClickable { viewModel.showReviewAnswer(activeItem) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Show answer",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.008).sp,
                                color = ChessTutorColors.TextSecondary
                            )
                        }
                    }

                    // Progress Dots (.dots)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (dotIndex in 0 until totalDots) {
                            val isActive = dotIndex <= currentIndex
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isActive) ChessTutorColors.Brass else ChessTutorColors.Surface3)
                            )
                        }
                    }
                }
            }
        }
    }

    // ==================== PROFILE BOTTOM SHEET (#sheet-profile) ====================
    if (isProfileSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isProfileSheetOpen = false },
            sheetState = profileSheetState,
            containerColor = ChessTutorColors.Surface,
            contentColor = ChessTutorColors.TextPrimary,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 4.dp)
                        .size(width = 34.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ChessTutorColors.Surface3)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                // User Profile Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ChessTutorColors.Surface3),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = ChessTutorColors.Brass,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(13.dp))

                    Column {
                        Text(
                            text = state.linkedProfile?.username ?: "You",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.01).sp,
                            color = ChessTutorColors.TextPrimary
                        )
                        Text(
                            text = if (state.linkedProfile != null) {
                                "${state.linkedProfile.platform.displayName} · ${state.linkedProfile.activeRating} rating"
                            } else {
                                "Not connected · playing locally"
                            },
                            fontSize = 12.5.sp,
                            color = ChessTutorColors.TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Opening Performance
                Text(
                    text = "OPENING PERFORMANCE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.06.sp,
                    color = ChessTutorColors.TextTertiary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ChessTutorColors.Surface2)
                        .padding(bottom = 1.dp)
                ) {
                    SAMPLE_OPENINGS.forEachIndexed { idx, opening ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ChessTutorColors.Surface)
                                .padding(12.dp, 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = opening.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = (-0.008).sp,
                                    color = ChessTutorColors.TextPrimary
                                )
                                Text(
                                    text = "${opening.games} games" + if (opening.needsWork) " · needs work" else "",
                                    fontSize = 11.5.sp,
                                    color = ChessTutorColors.TextTertiary,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }

                            // Track bar
                            Box(
                                modifier = Modifier
                                    .width(52.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(ChessTutorColors.Surface3)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(opening.winRate / 100f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (opening.needsWork) ChessTutorColors.Coral else ChessTutorColors.Sage)
                                )
                            }

                            Spacer(modifier = Modifier.width(9.dp))

                            Text(
                                text = "${opening.winRate}%",
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = if (opening.needsWork) ChessTutorColors.Coral else ChessTutorColors.Sage,
                                modifier = Modifier.width(34.dp),
                                textAlign = TextAlign.End
                            )
                        }

                        if (idx < SAMPLE_OPENINGS.size - 1) {
                            Spacer(modifier = Modifier.height(1.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // This Month Cards
                Text(
                    text = "THIS MONTH",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.06.sp,
                    color = ChessTutorColors.TextTertiary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ChessTutorColors.Surface2)
                            .padding(12.dp, 13.dp)
                    ) {
                        Text(
                            text = "Puzzles solved",
                            fontSize = 12.sp,
                            color = ChessTutorColors.TextSecondary
                        )
                        Text(
                            text = "142",
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = ChessTutorColors.TextPrimary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ChessTutorColors.Surface2)
                            .padding(12.dp, 13.dp)
                    ) {
                        Text(
                            text = "Accuracy",
                            fontSize = 12.sp,
                            color = ChessTutorColors.TextSecondary
                        )
                        Text(
                            text = "78%",
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = ChessTutorColors.Sage,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
