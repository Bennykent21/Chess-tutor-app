package com.chesstutor.app.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesstutor.app.domain.TrainDrillsRepository
import com.chesstutor.app.ui.components.ChessBoard
import com.chesstutor.app.ui.components.EvalBar
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.bouncyClickable
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    var isBoardFlipped by remember { mutableStateOf(false) }
    var isDrillSheetOpen by remember { mutableStateOf(false) }

    val drills = TrainDrillsRepository.drills
    val drillSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // NOTE: `state.activeCoachTitle` / `state.activeCoachSubtitle` are the
    // single source of truth for what's actually loaded on the board right
    // now - they're kept in sync whether you got here via a drill, a
    // curriculum lesson, or "Practise this" from Learn. We never read the
    // raw `drills` list for display text; it's only used to populate the
    // "choose a drill" sheet below.
    val isSolved = state.message.contains("Correct", ignoreCase = true) ||
            state.message.contains("Checkmate", ignoreCase = true)

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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .bouncyClickable { isDrillSheetOpen = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = state.activeCoachTitle,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.015).sp,
                        color = ChessTutorColors.TextPrimary
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select Drill",
                        tint = ChessTutorColors.TextTertiary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = state.activeCoachSubtitle,
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

                // Settings Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .bouncyClickable { viewModel.setSettingsVisible(true) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = ChessTutorColors.TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        // ==================== BODY ====================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Board Row: Eval Bar (22dp) + Board, spanning the FULL screen
                // width edge-to-edge (no horizontal padding here - only the
                // eval bar and the board live in this row).
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

                // Prompt line with Dot - back to being inset by 16dp.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isSolved) ChessTutorColors.Sage else ChessTutorColors.Brass)
                    )

                    val promptMessage = when {
                        isSolved -> "That's mate. Next drill..."
                        state.mistakeDetected -> "Incorrect move. Try again."
                        state.message.isNotBlank() -> state.message
                        else -> state.activeCoachSubtitle
                    }

                    Text(
                        text = promptMessage,
                        fontSize = 14.5.sp,
                        letterSpacing = (-0.008).sp,
                        fontWeight = FontWeight.Normal,
                        color = if (isSolved) ChessTutorColors.Sage else if (state.mistakeDetected) ChessTutorColors.Coral else ChessTutorColors.TextPrimary
                    )
                }
            }

            // Actions Row: [ Hint 0/3 ] and [ Skip ] - inset by 16dp.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp, top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                // Hint Button
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

                // Skip / Next Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (isSolved) ChessTutorColors.Brass else Color.Transparent)
                        .border(
                            1.dp,
                            if (isSolved) ChessTutorColors.Brass else ChessTutorColors.Line,
                            RoundedCornerShape(11.dp)
                        )
                        .bouncyClickable { viewModel.nextDrill() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSolved) "Next drill" else "Skip",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.008).sp,
                        color = if (isSolved) ChessTutorColors.BrassInk else ChessTutorColors.TextSecondary
                    )
                }
            }
        }
    }

    // ==================== CHOOSE A DRILL SHEET ====================
    if (isDrillSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isDrillSheetOpen = false },
            sheetState = drillSheetState,
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
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Choose a drill",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.015).sp,
                    color = ChessTutorColors.TextPrimary
                )
                Text(
                    text = "Your accuracy over the last 20 attempts",
                    fontSize = 12.5.sp,
                    color = ChessTutorColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    itemsIndexed(drills) { index, drill ->
                        val isSelected = index == state.currentDrillIndex
                        // Simulated accuracy percentages matching mockup
                        val acc = when (index % 5) {
                            0 -> 82
                            1 -> 64
                            2 -> 71
                            3 -> 48
                            else -> 77
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ChessTutorColors.Surface2)
                                .border(
                                    1.5.dp,
                                    if (isSelected) ChessTutorColors.Brass else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .bouncyClickable {
                                    viewModel.selectDrill(index)
                                    isDrillSheetOpen = false
                                }
                                .padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = drill.title,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = (-0.01).sp,
                                    color = ChessTutorColors.TextPrimary
                                )
                                Text(
                                    text = "${drill.prompt} · 12 positions",
                                    fontSize = 12.sp,
                                    color = ChessTutorColors.TextSecondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            Text(
                                text = "$acc%",
                                fontSize = 12.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = if (acc >= 75) ChessTutorColors.Sage else if (acc < 60) ChessTutorColors.Coral else ChessTutorColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
