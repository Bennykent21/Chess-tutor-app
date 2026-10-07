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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.domain.ReviewScheduler
import com.chesstutor.app.domain.ReviewStatsCalculator
import com.chesstutor.app.navigation.NavCommand
import com.chesstutor.app.navigation.OpeningMode
import com.chesstutor.app.navigation.PlayRequest
import com.chesstutor.app.navigation.Side
import com.chesstutor.app.navigation.TrainRequest
import com.chesstutor.app.ui.components.ChessBoard
import com.chesstutor.app.ui.components.EvalBar
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.bouncyClickable
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val dueReviews = state.reviews.filter { ReviewScheduler.isDue(it, Instant.now()) }
    val activeItem = state.activeReviewItem ?: dueReviews.firstOrNull() ?: state.reviews.firstOrNull()
    val dueCount = dueReviews.size

    val sideToMoveIsBlack = remember(activeItem?.fen) {
        activeItem?.fen?.let { runCatching { ChessPosition(it).sideToMove == 'b' }.getOrDefault(false) } ?: false
    }
    var isBoardFlipped by remember(sideToMoveIsBlack) { mutableStateOf(sideToMoveIsBlack) }
    var isProfileSheetOpen by remember { mutableStateOf(false) }
    val profileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedPlatform by remember(state.linkedProfile?.platform) {
        mutableStateOf(state.linkedProfile?.platform ?: RatingPlatform.LICHESS)
    }
    var usernameInput by remember(state.linkedProfile?.username) {
        mutableStateOf(state.linkedProfile?.username ?: "")
    }
    val dashboardStats = remember(state.recentGames, state.reviews) {
        ReviewStatsCalculator.compute(state.recentGames, state.reviews)
    }

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
                        .testTag("review_flip_board_button")
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
                        .testTag("review_profile_button")
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
                        .testTag("review_load_sample_button")
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
                                showCoordinates = state.showCoordinates,
                                showLegalDots = state.showLegalDots,
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
                        text = activeItem.explanation.ifBlank {
                            "Stage: ${ReviewScheduler.stageLabel(activeItem.stage)} · Attempts: ${activeItem.attempts}"
                        },
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
                                .testTag("review_hint_button")
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
                                    text = "${state.hintLevel}/4",
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
                                .testTag("review_show_answer_button")
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
        androidx.activity.compose.BackHandler { isProfileSheetOpen = false }
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                // User Profile Header & Connect Account Flow (§2.6)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
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

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.linkedProfile?.username ?: "Connect your account",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.01).sp,
                            color = ChessTutorColors.TextPrimary
                        )
                        Text(
                            text = if (state.linkedProfile != null) {
                                "${state.linkedProfile.platform.displayName} · ${state.linkedProfile.activeRating ?: "Unrated"} rating"
                            } else {
                                "Import your public games, openings and win rates"
                            },
                            fontSize = 12.5.sp,
                            color = ChessTutorColors.TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    if (state.linkedProfile != null) {
                        Text(
                            text = "Sync",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.Brass,
                            modifier = Modifier
                                .bouncyClickable { viewModel.refreshLinkedRating() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        Text(
                            text = "Unlink",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.Coral,
                            modifier = Modifier
                                .bouncyClickable { viewModel.unlinkRatingAccount() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (state.linkedProfile == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ChessTutorColors.Surface2)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RatingPlatform.entries.forEach { platform ->
                                val selected = selectedPlatform == platform
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) ChessTutorColors.Brass else ChessTutorColors.Surface3)
                                        .bouncyClickable { selectedPlatform = platform },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = platform.displayName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) ChessTutorColors.BrassInk else ChessTutorColors.TextPrimary
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            placeholder = { Text("Username", fontSize = 13.sp, color = ChessTutorColors.TextTertiary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ChessTutorColors.Brass,
                                unfocusedBorderColor = ChessTutorColors.Line,
                                focusedTextColor = ChessTutorColors.TextPrimary,
                                unfocusedTextColor = ChessTutorColors.TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(ChessTutorColors.Brass)
                                .bouncyClickable {
                                    viewModel.linkRatingAccount(selectedPlatform, usernameInput)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (state.isLinkingLoading) "Importing games..." else "Connect & Import Games",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ChessTutorColors.BrassInk
                            )
                        }
                        state.linkingError?.let { err ->
                            Text(
                                text = err,
                                fontSize = 12.sp,
                                color = ChessTutorColors.Coral,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                } else if (state.linkingSuccessMessage != null) {
                    Text(
                        text = state.linkingSuccessMessage,
                        fontSize = 12.sp,
                        color = ChessTutorColors.Sage,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                // Overview Cards (Computed from real games)
                Text(
                    text = "OVERVIEW",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.06.sp,
                    color = ChessTutorColors.TextTertiary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
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
                            text = "Games (${dashboardStats.wins}W/${dashboardStats.draws}D/${dashboardStats.losses}L)",
                            fontSize = 11.5.sp,
                            color = ChessTutorColors.TextSecondary
                        )
                        Text(
                            text = if (dashboardStats.totalGames > 0) "${dashboardStats.scorePercent}% score" else "0 games",
                            fontSize = 18.sp,
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
                            text = "Drill accuracy",
                            fontSize = 11.5.sp,
                            color = ChessTutorColors.TextSecondary
                        )
                        val accuracyText = if (state.tacticalAttempts > 0) {
                            "${(state.tacticalCorrect * 100 / state.tacticalAttempts)}%"
                        } else {
                            "—"
                        }
                        Text(
                            text = accuracyText,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = ChessTutorColors.Sage,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Actionable Insights (§2.6 & §3.6)
                if (dashboardStats.insights.isNotEmpty()) {
                    Text(
                        text = "INSIGHTS & NEXT ACTIONS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.06.sp,
                        color = ChessTutorColors.TextTertiary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dashboardStats.insights.forEach { insight ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ChessTutorColors.Surface2)
                                    .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(12.dp))
                                    .bouncyClickable {
                                        isProfileSheetOpen = false
                                        viewModel.navigate(insight.command)
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = insight.sentence,
                                    fontSize = 12.5.sp,
                                    color = ChessTutorColors.TextPrimary,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                Text(
                                    text = insight.actionLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ChessTutorColors.Brass
                                )
                            }
                        }
                    }
                }

                // Opening Performance (Computed from real games, never sample data)
                Text(
                    text = "OPENING PERFORMANCE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.06.sp,
                    color = ChessTutorColors.TextTertiary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (dashboardStats.openings.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ChessTutorColors.Surface2)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Play games in Play or connect your Chess.com / Lichess username above to see your real opening win rates.",
                            fontSize = 12.5.sp,
                            color = ChessTutorColors.TextSecondary
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ChessTutorColors.Surface2)
                            .padding(bottom = 1.dp)
                    ) {
                        dashboardStats.openings.forEachIndexed { idx, opening ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ChessTutorColors.Surface)
                                    .padding(12.dp, 11.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${opening.family} (${opening.userColor.replaceFirstChar { it.uppercase() }})",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = (-0.008).sp,
                                            color = ChessTutorColors.TextPrimary
                                        )
                                        Text(
                                            text = "${opening.games} games (${opening.wins}W/${opening.draws}D/${opening.losses}L)" +
                                                if (opening.needsWork) " · needs work" else "",
                                            fontSize = 11.5.sp,
                                            color = ChessTutorColors.TextTertiary,
                                            modifier = Modifier.padding(top = 1.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(52.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(ChessTutorColors.Surface3)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth((opening.scorePercent / 100f).coerceIn(0f, 1f))
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(if (opening.needsWork) ChessTutorColors.Coral else ChessTutorColors.Sage)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(9.dp))

                                    Text(
                                        text = "${opening.scorePercent}%",
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (opening.needsWork) ChessTutorColors.Coral else ChessTutorColors.Sage,
                                        modifier = Modifier.width(38.dp),
                                        textAlign = TextAlign.End
                                    )
                                }

                                // Deep links: Learn · Train · Play (§2.2 & §2.6)
                                Row(
                                    modifier = Modifier.padding(top = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    opening.relatedTopicId?.let { topicId ->
                                        Text(
                                            text = "Learn",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ChessTutorColors.Brass,
                                            modifier = Modifier.bouncyClickable {
                                                isProfileSheetOpen = false
                                                viewModel.navigate(NavCommand.OpenLearn(topicId))
                                            }
                                        )
                                    }
                                    opening.representativeLineId?.let { lineId ->
                                        Text(
                                            text = "Drill",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ChessTutorColors.Sage,
                                            modifier = Modifier.bouncyClickable {
                                                isProfileSheetOpen = false
                                                viewModel.startTrain(TrainRequest.Opening(lineId))
                                            }
                                        )
                                        Text(
                                            text = "Play Line",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ChessTutorColors.TextSecondary,
                                            modifier = Modifier.bouncyClickable {
                                                isProfileSheetOpen = false
                                                val side = if (opening.userColor == "black") Side.BLACK else Side.WHITE
                                                viewModel.startPlay(
                                                    PlayRequest.Opening(lineId, side, OpeningMode.LEARN_LINE)
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            if (idx < dashboardStats.openings.size - 1) {
                                Spacer(modifier = Modifier.height(1.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (state.recentGames.isNotEmpty()) {
                    Text(
                        text = "SAVED ARENA GAMES",
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
                            .padding(bottom = 20.dp)
                    ) {
                        state.recentGames.take(3).forEach { game ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bouncyClickable {
                                        isProfileSheetOpen = false
                                        viewModel.selectGameForPgn(game)
                                    }
                                    .padding(12.dp, 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "vs. ${game.botName} (${game.botRating})",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ChessTutorColors.TextPrimary
                                    )
                                    Text(
                                        text = "${game.moveCount} moves · ${game.result}",
                                        fontSize = 11.5.sp,
                                        color = ChessTutorColors.TextSecondary
                                    )
                                }
                                Text(
                                    text = "PGN",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ChessTutorColors.Brass
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
