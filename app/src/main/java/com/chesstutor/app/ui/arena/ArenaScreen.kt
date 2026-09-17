package com.chesstutor.app.ui.arena

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.chesstutor.app.ui.components.ChessBoard
import com.chesstutor.app.ui.components.EvalBar
import com.chesstutor.app.ui.settings.SettingsSheet
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.bouncyClickable
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel

data class BotProfile(
    val name: String,
    val elo: Int,
    val tier: String,
    val quote: String,
    val avatarBg: Color
)

val CHESS_BOTS = listOf(
    BotProfile("Martin", 250, "Beginner", "I'm still learning the rules!", Color(0xFF4A7C59)),
    BotProfile("Wayne", 600, "Casual", "A relaxed, friendly game.", Color(0xFF4682B4)),
    BotProfile("Nelson", 1300, "Intermediate", "I bring my Queen out early! Can you defend?", Color(0xFFD97706)),
    BotProfile("Elena", 2000, "Advanced", "Positional mastery and deep calculation.", Color(0xFF7C3AED)),
    BotProfile("Stockfish 16", 3200, "Grandmaster", "Cloud Stockfish engine at full grandmaster depth.", Color(0xFF81B64C))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArenaScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val moveScrollState = rememberScrollState()
    val opponentSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isBoardFlipped by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isResignConfirmOpen by remember { mutableStateOf(false) }

    val currentBot = CHESS_BOTS.firstOrNull { it.tier.equals(state.arenaDifficulty, ignoreCase = true) }
        ?: CHESS_BOTS.last()

    // Auto-scroll move history to the right when a new move is appended (§5.3)
    LaunchedEffect(state.moveHistory.size) {
        moveScrollState.animateScrollTo(moveScrollState.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChessTutorColors.Background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Top Header: vs. Opponent + Elo, ⚙ (Settings), ⇅ (Flip) (§5.2)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tappable Opponent Name & Rating (opens bot picker)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .bouncyClickable { viewModel.setArenaOpponentSheetVisible(true) }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "vs. ${currentBot.name} (${state.effectiveBotElo})",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChessTutorColors.TextPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Change Bot",
                    tint = ChessTutorColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Quick actions (Flip, Settings)
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

        Spacer(modifier = Modifier.height(4.dp))

        // 2. Opponent Player Bar (§5.2)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ChessTutorColors.Surface)
                .border(1.dp, ChessTutorColors.Border, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(currentBot.avatarBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = currentBot.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ChessTutorColors.TextPrimary
                    )
                    Text(
                        text = "${state.effectiveBotElo} Elo · ${currentBot.tier}",
                        fontSize = 12.sp,
                        color = ChessTutorColors.TextSecondary
                    )
                }
            }

            if (state.opponentThinking) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = ChessTutorColors.Accent
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Thinking...",
                        fontSize = 12.sp,
                        color = ChessTutorColors.Accent
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Center: Chess Board with Left Eval Bar (The Hero, ~55% space)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Eval Bar
            EvalBar(
                centipawns = state.evaluationCp,
                mateIn = state.mateIn,
                modifier = Modifier
                    .width(28.dp)
                    .fillMaxHeight()
                    .padding(end = 8.dp)
            )

            // Chess Board
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

        Spacer(modifier = Modifier.height(6.dp))

        // 4. Player Bar ("You") (§5.2)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ChessTutorColors.Surface)
                .border(1.dp, ChessTutorColors.Border, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ChessTutorColors.SurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = ChessTutorColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "You",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ChessTutorColors.TextPrimary
                    )
                    Text(
                        text = if (state.useLinkedRatingForBot && state.linkedProfile?.activeRating != null) {
                            "${state.linkedProfile!!.activeRating} Elo (${state.linkedProfile!!.platform.displayName})"
                        } else "White",
                        fontSize = 12.sp,
                        color = ChessTutorColors.TextSecondary
                    )
                }
            }

            if (!state.opponentThinking && !state.busy) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ChessTutorColors.Accent)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 5. Move Notation Strip (§5.3: Single horizontal row, horizontally scrollable)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ChessTutorColors.Surface)
                .border(1.dp, ChessTutorColors.Border, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (state.moveHistory.isEmpty()) {
                Text(
                    text = "Moves will appear here as played",
                    fontSize = 13.sp,
                    color = ChessTutorColors.TextTertiary,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(moveScrollState),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val moves = state.moveHistory
                    for (i in moves.indices step 2) {
                        val moveNumber = (i / 2) + 1
                        val whiteMove = moves[i]
                        val blackMove = moves.getOrNull(i + 1)

                        // Move Number (muted color)
                        Text(
                            text = "$moveNumber. ",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = ChessTutorColors.TextTertiary,
                            fontFamily = FontFamily.Monospace
                        )
                        // White move
                        Text(
                            text = "$whiteMove ",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = ChessTutorColors.TextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                        // Black move if available
                        if (blackMove != null) {
                            Text(
                                text = "$blackMove  ",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = ChessTutorColors.TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 6. Bottom Actions: [ 🏳 Resign ] and [ ⟳ New Game ] (§5.2, §5.3)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { isResignConfirmOpen = true },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ChessTutorColors.TextSecondary
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(ChessTutorColors.Border)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Resign",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChessTutorColors.TextSecondary
                )
            }

            Button(
                onClick = { viewModel.resetArenaGame() },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ChessTutorColors.Accent,
                    contentColor = ChessTutorColors.Background
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "New Game",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // 7. Bot Picker Modal Bottom Sheet (§5.2)
    if (state.isArenaOpponentSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setArenaOpponentSheetVisible(false) },
            sheetState = opponentSheetState,
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
                            text = "Select Bot Opponent",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.TextPrimary
                        )
                        Text(
                            text = "Calibrated playing strength (250 – 3200 Elo)",
                            fontSize = 13.sp,
                            color = ChessTutorColors.TextSecondary
                        )
                    }

                    IconButton(
                        onClick = { viewModel.setArenaOpponentSheetVisible(false) },
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(CHESS_BOTS) { bot ->
                        val isSelected = bot.tier.equals(state.arenaDifficulty, ignoreCase = true)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) ChessTutorColors.SurfaceElevated
                                    else Color.Transparent
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) ChessTutorColors.Accent else ChessTutorColors.Border,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .bouncyClickable {
                                    viewModel.setArenaDifficulty(bot.tier)
                                    viewModel.resetArenaGame()
                                    viewModel.setArenaOpponentSheetVisible(false)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(bot.avatarBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = bot.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) ChessTutorColors.Accent else ChessTutorColors.TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "(${bot.elo})",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ChessTutorColors.TextSecondary
                                    )
                                }
                                Text(
                                    text = bot.quote,
                                    fontSize = 12.sp,
                                    color = ChessTutorColors.TextTertiary,
                                    maxLines = 1
                                )
                            }

                            Text(
                                text = bot.tier,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isSelected) ChessTutorColors.Accent else ChessTutorColors.TextSecondary
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

    // 8. Resign Confirmation
    if (isResignConfirmOpen) {
        ModalBottomSheet(
            onDismissRequest = { isResignConfirmOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = ChessTutorColors.Surface,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Resign Game?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChessTutorColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Are you sure you want to concede this match?",
                    fontSize = 14.sp,
                    color = ChessTutorColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { isResignConfirmOpen = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            isResignConfirmOpen = false
                            viewModel.resetArenaGame()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChessTutorColors.Mistake,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Resign")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    // 9. Settings Dialog
    if (isSettingsOpen) {
        SettingsSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = { isSettingsOpen = false }
        )
    }
}
