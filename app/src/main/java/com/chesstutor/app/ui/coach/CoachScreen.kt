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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesstutor.app.domain.TrainDrillsRepository
import com.chesstutor.app.ui.components.ChessBoard
import com.chesstutor.app.ui.components.EvalBar
import com.chesstutor.app.ui.settings.SettingsSheet
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
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isDrillSheetOpen by remember { mutableStateOf(false) }

    val drills = TrainDrillsRepository.drills
    val drillSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val currentDrill = drills.getOrNull(state.currentDrillIndex) ?: drills[0]

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChessTutorColors.Background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Header: Drill Title (20sp Semibold) + Subtitle (13sp) + Quick Actions (⚙, ⇅)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tappable Drill Title & Counter (§3.2, §3.3)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .bouncyClickable { isDrillSheetOpen = true }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentDrill.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Select Drill",
                            tint = ChessTutorColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Drill ${state.currentDrillIndex + 1} of ${drills.size}",
                        fontSize = 13.sp,
                        color = ChessTutorColors.TextSecondary
                    )
                }
            }

            // Action Icons (Flip, Settings)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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

                IconButton(
                    onClick = { isSettingsOpen = true },
                    modifier = Modifier.size(38.dp)
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

        Spacer(modifier = Modifier.height(8.dp))

        // 2. The Hero: Chess Board with left Eval Bar (~55% vertical height)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Eval Bar
            EvalBar(
                centipawns = state.evaluationCp,
                mateIn = state.mateIn,
                modifier = Modifier
                    .width(28.dp)
                    .fillMaxHeight()
                    .padding(end = 8.dp)
            )

            // Chess Board (fills remaining width with 1:1 aspect ratio)
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

        // 3. Single Task Line (§3.2, §3.3: ONE line. The task. Nothing else.)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (state.mistakeDetected && state.canRetryMistake) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Incorrect move.",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = ChessTutorColors.Mistake
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Retry",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ChessTutorColors.Accent,
                        modifier = Modifier
                            .bouncyClickable { viewModel.retryMistake() }
                            .padding(4.dp)
                    )
                }
            } else {
                Text(
                    text = state.message.ifBlank { currentDrill.prompt },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (state.message.contains("Correct", ignoreCase = true) || state.message.contains("Checkmate", ignoreCase = true)) {
                        ChessTutorColors.Best
                    } else {
                        ChessTutorColors.TextPrimary
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Action Buttons: [ 💡 Hint 1/3 ] and [ Skip → ] / [ Next Drill → ]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val isSolved = state.message.contains("Correct", ignoreCase = true) ||
                    state.message.contains("Checkmate", ignoreCase = true)

            if (isSolved) {
                // When solved: Single prominent "Next Drill →" button
                Button(
                    onClick = { viewModel.nextDrill() },
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
                        text = "Next Drill",
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
                // Two equal weight action buttons: Hint & Skip (§3.2, §3.3)
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
                    onClick = { viewModel.nextDrill() },
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

    // 5. Drill Selection Bottom Sheet (§3.3: listing drill categories with position counts)
    if (isDrillSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isDrillSheetOpen = false },
            sheetState = drillSheetState,
            containerColor = ChessTutorColors.Surface,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Tactical Drills",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.TextPrimary
                        )
                        Text(
                            text = "${drills.size} positions available",
                            fontSize = 13.sp,
                            color = ChessTutorColors.TextSecondary
                        )
                    }

                    IconButton(
                        onClick = { isDrillSheetOpen = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ChessTutorColors.TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(drills) { index, drill ->
                        val isCurrent = index == state.currentDrillIndex

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isCurrent) ChessTutorColors.SurfaceElevated
                                    else Color.Transparent
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isCurrent) ChessTutorColors.Accent else ChessTutorColors.Border,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .bouncyClickable {
                                    viewModel.selectDrill(index)
                                    isDrillSheetOpen = false
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = drill.title,
                                    fontSize = 15.sp,
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isCurrent) ChessTutorColors.Accent else ChessTutorColors.TextPrimary
                                )
                                Text(
                                    text = drill.category,
                                    fontSize = 12.sp,
                                    color = ChessTutorColors.TextTertiary
                                )
                            }

                            Text(
                                text = "Drill ${index + 1}",
                                fontSize = 12.sp,
                                color = ChessTutorColors.TextSecondary
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // 6. Settings Dialog
    if (isSettingsOpen) {
        SettingsSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = { isSettingsOpen = false }
        )
    }
}
