package com.chesstutor.app.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.domain.LearnCurriculumRepository
import com.chesstutor.app.domain.LessonTrainSession
import com.chesstutor.app.domain.ReviewScheduler
import com.chesstutor.app.domain.SessionStepKind
import com.chesstutor.app.domain.TrainDrillsRepository
import com.chesstutor.app.navigation.NavCommand
import com.chesstutor.app.navigation.TrainRequest
import com.chesstutor.app.ui.components.ChessBoard
import com.chesstutor.app.ui.components.HorizontalEvalBar
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.TabularTextStyle
import com.chesstutor.app.ui.theme.bouncyClickable
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel
import com.chesstutor.app.viewmodel.PuzzlePhase
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val activeSession = state.activeLessonSession
    val activeStep = activeSession?.currentStep
    val currentDrill = TrainDrillsRepository.drills.getOrElse(state.currentDrillIndex) {
        TrainDrillsRepository.drills.first()
    }
    val pos = remember(state.fen) { runCatching { ChessPosition(state.fen) }.getOrNull() }
    val isWhiteToMove = (pos?.sideToMove ?: 'w') == 'w'
    val dueReviewCount = remember(state.reviews) {
        val now = Instant.now()
        state.reviews.count { ReviewScheduler.isDue(it, now) }
    }

    // S1: Check if a verified legal solution exists for the current FEN so we never invent hints
    val hasVerifiedHint = state.hasVerifiedHintSolution

    if (state.isDrillSheetVisible && activeSession == null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setDrillSheetVisible(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = ChessTutorColors.SurfaceRaised
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Choose a tactical drill",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChessTutorColors.TextPrimary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Practice curated tactical patterns or start a structured lesson in Learn.",
                    fontSize = 14.sp,
                    color = ChessTutorColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    itemsIndexed(TrainDrillsRepository.drills) { index, drill ->
                        val isSelected = index == state.currentDrillIndex
                        val isSolved = state.practicedModules.contains(drill.id) || state.masteredModules.contains(drill.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) ChessTutorColors.BrassFaint else ChessTutorColors.Surface
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) ChessTutorColors.Brass.copy(alpha = 0.5f) else ChessTutorColors.Line,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { viewModel.selectDrill(index) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "${index + 1}. ${drill.title}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) ChessTutorColors.Brass else ChessTutorColors.TextPrimary
                                    )
                                    Text(
                                        text = drill.category.uppercase(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ChessTutorColors.TextTertiary
                                    )
                                }
                                Text(
                                    text = drill.prompt,
                                    fontSize = 14.sp,
                                    color = ChessTutorColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            if (isSolved) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Solved",
                                    tint = ChessTutorColors.Success,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // S4: If the active lesson session is on the summary step, show the Lesson Summary screen
    if (activeSession != null && activeSession.isSummary) {
        LessonSummaryContent(
            session = activeSession,
            onBackToLesson = { viewModel.navigate(NavCommand.OpenLearn(activeSession.topicId)) },
            onNextLesson = { viewModel.startNextLessonInCourse() },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    // S3: Responsive layout with BoxWithConstraints and anchored Bottom Action Bar
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ChessTutorColors.Background)
    ) {
        val reservedHeight = if (activeSession != null) 280.dp else 270.dp
        val maxBoardFromHeight = (maxHeight - reservedHeight).coerceAtLeast(210.dp)
        val maxBoardFromWidth = (maxWidth - 28.dp).coerceAtLeast(210.dp)
        val boardSize = min(maxBoardFromWidth, maxBoardFromHeight)

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Scrollable upper content so controls never clip even at 1.3x / 1.5x font scale
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (activeSession != null && activeStep != null) {
                    LessonSessionHeader(
                        session = activeSession,
                        onBackToLesson = { viewModel.navigate(NavCommand.OpenLearn(activeSession.topicId)) },
                        onOpenSettings = { viewModel.setSettingsVisible(true) }
                    )
                } else {
                    DrillTopHeader(
                        state = state,
                        currentDrillTitle = currentDrill.title,
                        dueReviewCount = dueReviewCount,
                        onOpenDrillSheet = { viewModel.setDrillSheetVisible(true) },
                        onStartDueReview = { viewModel.startTrain(TrainRequest.ReviewDue) },
                        onOpenSettings = { viewModel.setSettingsVisible(true) }
                    )
                }

                // S3 / §2.4: Hide eval bar in teaching lessons; show horizontal eval bar in standalone drills
                if (activeSession == null) {
                    HorizontalEvalBar(
                        centipawns = state.evaluationCp,
                        mateIn = state.mateIn,
                        isWhiteOnBottom = isWhiteToMove,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 6.dp)
                    )
                }

                // Prompt / Explanation banner above board
                if (activeSession != null && activeStep != null) {
                    LessonStepBanner(
                        stepTitle = activeStep.title,
                        stepBadge = activeStep.kind.badge,
                        prompt = activeStep.prompt,
                        explanationBullets = if (activeStep.kind == SessionStepKind.EXPLAIN) activeStep.explanationBullets else emptyList(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp)
                    )
                } else {
                    DrillSideBanner(
                        isWhiteToMove = isWhiteToMove,
                        objective = state.activeCoachSubtitle.ifBlank { currentDrill.prompt },
                        themeTag = state.activeCoachCategory.ifBlank { currentDrill.category },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp)
                    )
                }

                // Square Chess Board sized dynamically from available width & height
                Box(
                    modifier = Modifier
                        .size(boardSize)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, ChessTutorColors.LineStrong, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    ChessBoard(
                        fen = state.fen,
                        selectedSquare = state.selectedSquare,
                        legalTargets = state.legalTargets,
                        lastMove = state.lastMove,
                        recommendedArrow = state.recommendedArrow,
                        flipped = !isWhiteToMove,
                        showCoordinates = state.showCoordinates,
                        showLegalDots = state.showLegalDots,
                        onSquareTapped = viewModel::onSquareTapped,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("coach_chess_board")
                    )
                }

                // Feedback Card below board
                CoachFeedbackCard(
                    state = state,
                    activeSession = activeSession,
                    onRetry = { viewModel.retryMistake() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            // Anchored Bottom Action Bar — always visible above the bottom navigation bar (S3)
            CoachBottomActionBar(
                state = state,
                activeSession = activeSession,
                hasVerifiedHint = hasVerifiedHint,
                onHint = { viewModel.showHint() },
                onSkipOrNext = {
                    if (activeSession != null) {
                        viewModel.advanceLessonStep()
                    } else {
                        viewModel.nextDrill()
                    }
                },
                onRetry = { viewModel.retryMistake() }
            )
        }
    }
}

@Composable
private fun LessonSessionHeader(
    session: LessonTrainSession,
    onBackToLesson: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val step = session.currentStep
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ChessTutorColors.Surface)
                        .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(10.dp))
                        .testTag("lesson_header_back_button")
                        .bouncyClickable(onClick = onBackToLesson)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to lesson",
                            tint = ChessTutorColors.Brass,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Lesson",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.Brass
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.topicTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ChessTutorColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Step ${session.currentStepIndex + 1} of ${session.totalSteps} · ${step.kind.badge}",
                        fontSize = 13.sp,
                        style = TabularTextStyle,
                        color = ChessTutorColors.TextSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ChessTutorColors.Surface)
                    .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(12.dp))
                    .testTag("coach_settings_button")
                    .bouncyClickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = ChessTutorColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        LinearProgressIndicator(
            progress = { session.progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = ChessTutorColors.Brass,
            trackColor = ChessTutorColors.SurfaceRaised
        )
    }
}

@Composable
private fun DrillTopHeader(
    state: AppUiState,
    currentDrillTitle: String,
    dueReviewCount: Int,
    onOpenDrillSheet: () -> Unit,
    onStartDueReview: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .testTag("drill_selector_button")
                .bouncyClickable(onClick = onOpenDrillSheet)
                .padding(vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = state.activeCoachTitle.ifBlank { currentDrillTitle },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChessTutorColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Switch drill",
                    tint = ChessTutorColors.Brass,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = "Tactical Drill ${state.currentDrillIndex + 1} of ${TrainDrillsRepository.drills.size}",
                fontSize = 13.sp,
                style = TabularTextStyle,
                color = ChessTutorColors.TextSecondary
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dueReviewCount > 0) {
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ChessTutorColors.BrassFaint)
                        .border(1.dp, ChessTutorColors.Brass.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .testTag("train_due_reviews_chip")
                        .bouncyClickable(onClick = onStartDueReview)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$dueReviewCount due",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = TabularTextStyle,
                        color = ChessTutorColors.Brass
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ChessTutorColors.Surface)
                    .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(12.dp))
                    .testTag("coach_settings_button")
                    .bouncyClickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = ChessTutorColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun LessonStepBanner(
    stepTitle: String,
    stepBadge: String,
    prompt: String,
    explanationBullets: List<String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ChessTutorColors.Surface)
            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stepTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChessTutorColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stepBadge.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChessTutorColors.Brass
            )
        }
        Text(
            text = prompt,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = ChessTutorColors.TextSecondary
        )
        if (explanationBullets.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                explanationBullets.forEach { bullet ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ChessTutorColors.Brass
                        )
                        Text(
                            text = bullet,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = ChessTutorColors.TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrillSideBanner(
    isWhiteToMove: Boolean,
    objective: String,
    themeTag: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ChessTutorColors.Surface)
            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (isWhiteToMove) Color(0xFFF3EFE6) else Color(0xFF1E2228))
                    .border(1.5.dp, ChessTutorColors.Brass, CircleShape)
            )
            Text(
                text = "${if (isWhiteToMove) "White" else "Black"} to move · $objective",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = ChessTutorColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = themeTag,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = ChessTutorColors.Brass,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun CoachFeedbackCard(
    state: AppUiState,
    activeSession: LessonTrainSession?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val phase = state.puzzlePhase
    val step = activeSession?.currentStep

    if (step?.kind == SessionStepKind.EXPLAIN) {
        return
    }

    val borderColor = when (phase) {
        PuzzlePhase.CORRECT -> ChessTutorColors.Success.copy(alpha = 0.55f)
        PuzzlePhase.WRONG -> ChessTutorColors.Danger.copy(alpha = 0.55f)
        PuzzlePhase.SOLVING, PuzzlePhase.REVEALED -> ChessTutorColors.Line
    }
    val bgColor = when (phase) {
        PuzzlePhase.CORRECT -> ChessTutorColors.Success.copy(alpha = 0.10f)
        PuzzlePhase.WRONG -> ChessTutorColors.Danger.copy(alpha = 0.10f)
        PuzzlePhase.SOLVING, PuzzlePhase.REVEALED -> ChessTutorColors.Surface
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when (phase) {
                        PuzzlePhase.CORRECT -> Icons.Default.CheckCircle
                        PuzzlePhase.WRONG -> Icons.Default.ErrorOutline
                        PuzzlePhase.SOLVING, PuzzlePhase.REVEALED -> Icons.Default.Lightbulb
                    },
                    contentDescription = null,
                    tint = when (phase) {
                        PuzzlePhase.CORRECT -> ChessTutorColors.Success
                        PuzzlePhase.WRONG -> ChessTutorColors.Danger
                        PuzzlePhase.SOLVING, PuzzlePhase.REVEALED -> ChessTutorColors.Brass
                    },
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = when (phase) {
                        PuzzlePhase.CORRECT -> "Correct"
                        PuzzlePhase.WRONG -> "Not quite"
                        PuzzlePhase.REVEALED -> "Solution revealed"
                        PuzzlePhase.SOLVING -> if (state.hintLevel > 0) "Hint ${state.hintLevel} of 4" else "Your turn"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (phase) {
                        PuzzlePhase.CORRECT -> ChessTutorColors.Success
                        PuzzlePhase.WRONG -> ChessTutorColors.Danger
                        PuzzlePhase.SOLVING, PuzzlePhase.REVEALED -> ChessTutorColors.Brass
                    }
                )
            }

            if (phase == PuzzlePhase.WRONG) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChessTutorColors.SurfaceRaised)
                        .border(1.dp, ChessTutorColors.Danger.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .testTag("coach_try_again_button")
                        .bouncyClickable(onClick = onRetry)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Try again",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ChessTutorColors.TextPrimary
                    )
                }
            }
        }

        val displayFeedback = if (state.hintLevel > 0 && state.hintText.isNotBlank() && phase == PuzzlePhase.SOLVING) {
            state.hintText
        } else {
            state.message
        }
        Text(
            text = displayFeedback,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = ChessTutorColors.TextPrimary
        )
    }
}

@Composable
private fun CoachBottomActionBar(
    state: AppUiState,
    activeSession: LessonTrainSession?,
    hasVerifiedHint: Boolean,
    onHint: () -> Unit,
    onSkipOrNext: () -> Unit,
    onRetry: () -> Unit
) {
    val step = activeSession?.currentStep
    val isExplainStep = step?.kind == SessionStepKind.EXPLAIN
    val isSolved = state.puzzlePhase == PuzzlePhase.CORRECT || state.puzzlePhase == PuzzlePhase.REVEALED
    val isIncorrect = state.puzzlePhase == PuzzlePhase.WRONG

    Surface(
        color = ChessTutorColors.Surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isExplainStep) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ChessTutorColors.Brass)
                        .testTag("lesson_continue_button")
                        .bouncyClickable(onClick = onSkipOrNext),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Continue to Guided Try",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.Background
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = ChessTutorColors.Background,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                // S1: Only display the Hint button when a verified legal solution exists for this FEN
                if (hasVerifiedHint && !isSolved) {
                    val hintLabel = if (state.hintLevel == 0) {
                        "Hint"
                    } else {
                        "Hint (${state.hintLevel}/4)"
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (state.hintLevel > 0) ChessTutorColors.BrassFaint else ChessTutorColors.SurfaceRaised
                            )
                            .border(
                                width = 1.dp,
                                color = if (state.hintLevel > 0) ChessTutorColors.Brass.copy(alpha = 0.5f) else ChessTutorColors.LineStrong,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .testTag("coach_hint_button")
                            .bouncyClickable(onClick = onHint),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = "Hint",
                                tint = ChessTutorColors.Brass,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = hintLabel,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                style = TabularTextStyle,
                                color = if (state.hintLevel > 0) ChessTutorColors.Brass else ChessTutorColors.TextPrimary
                            )
                        }
                    }
                }

                if (isIncorrect) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ChessTutorColors.SurfaceRaised)
                            .border(1.dp, ChessTutorColors.LineStrong, RoundedCornerShape(12.dp))
                            .testTag("coach_retry_action_button")
                            .bouncyClickable(onClick = onRetry),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = ChessTutorColors.TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Try again",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ChessTutorColors.TextPrimary
                            )
                        }
                    }
                }

                val primaryLabel = when {
                    activeSession != null && isSolved -> {
                        if (activeSession.currentStepIndex + 1 >= activeSession.totalSteps - 1) "Finish Lesson" else "Continue"
                    }
                    activeSession != null -> "Skip Step"
                    isSolved -> "Next Drill"
                    else -> "Skip"
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSolved) ChessTutorColors.Brass else ChessTutorColors.SurfaceRaised)
                        .border(
                            width = 1.dp,
                            color = if (isSolved) ChessTutorColors.Brass else ChessTutorColors.LineStrong,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .testTag("coach_next_or_skip_button")
                        .bouncyClickable(onClick = onSkipOrNext),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = primaryLabel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSolved) ChessTutorColors.Background else ChessTutorColors.TextPrimary
                        )
                        Icon(
                            imageVector = if (isSolved) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.SkipNext,
                            contentDescription = primaryLabel,
                            tint = if (isSolved) ChessTutorColors.Background else ChessTutorColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonSummaryContent(
    session: LessonTrainSession,
    onBackToLesson: () -> Unit,
    onNextLesson: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topic = remember(session.topicId) {
        LearnCurriculumRepository.topics.firstOrNull { it.id == session.topicId }
    }

    Column(
        modifier = modifier
            .background(ChessTutorColors.Background)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = ChessTutorColors.Success,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "LESSON COMPLETE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ChessTutorColors.Success
                )
            }

            Text(
                text = session.topicTitle,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChessTutorColors.TextPrimary
            )

            Text(
                text = topic?.summary ?: session.currentStep.prompt,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = ChessTutorColors.TextSecondary
            )

            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryStatTile(
                    label = "Accuracy",
                    value = "${session.accuracyPercent}%",
                    modifier = Modifier.weight(1f)
                )
                SummaryStatTile(
                    label = "Steps Done",
                    value = "${session.totalSteps}/${session.totalSteps}",
                    modifier = Modifier.weight(1f)
                )
                SummaryStatTile(
                    label = "First-Try Wins",
                    value = "${session.interactiveFirstTryCorrect}",
                    modifier = Modifier.weight(1f)
                )
            }

            val principles = topic?.keyPrinciples ?: session.currentStep.explanationBullets
            if (principles.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ChessTutorColors.Surface)
                        .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Key Takeaways",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ChessTutorColors.Brass
                    )
                    principles.forEach { principle ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = ChessTutorColors.Brass
                            )
                            Text(
                                text = principle,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = ChessTutorColors.TextPrimary
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ChessTutorColors.Brass)
                    .testTag("back_to_lesson_button")
                    .bouncyClickable(onClick = onBackToLesson),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Back to Lesson in Learn",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChessTutorColors.Background
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ChessTutorColors.SurfaceRaised)
                    .border(1.dp, ChessTutorColors.LineStrong, RoundedCornerShape(14.dp))
                    .testTag("next_lesson_button")
                    .bouncyClickable(onClick = onNextLesson),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Start Next Lesson",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChessTutorColors.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun SummaryStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ChessTutorColors.Surface)
            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            style = TabularTextStyle,
            color = ChessTutorColors.TextPrimary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = ChessTutorColors.TextSecondary
        )
    }
}
