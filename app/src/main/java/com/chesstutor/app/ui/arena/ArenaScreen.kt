package com.chesstutor.app.ui.arena

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
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
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.chesstutor.app.ui.components.ChessBoard
import com.chesstutor.app.ui.components.ChessPlayerBar
import com.chesstutor.app.ui.components.EvalBar
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
    BotProfile("Wayne", 600, "Casual", "A relaxed game after work.", Color(0xFF4682B4)),
    BotProfile("Nelson", 1300, "Intermediate", "I bring my Queen out early! Can you defend?", Color(0xFFD97706)),
    BotProfile("Elena", 2000, "Advanced", "Positional mastery with deep tactical calculation.", Color(0xFF7C3AED)),
    BotProfile("Stockfish 16", 3200, "Grandmaster", "Official cloud Stockfish engine evaluating at full depth.", Color(0xFF81B64C))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArenaScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val opponentSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isBoardFlipped by remember { mutableStateOf(false) }

    val currentBot = CHESS_BOTS.firstOrNull { it.tier.equals(state.arenaDifficulty, ignoreCase = true) }
        ?: CHESS_BOTS.last()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChessTutorColors.Background)
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Top Sub-header: Bot Header & Tuning Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF312E2B))
                    .border(1.dp, Color(0xFF3D3A34), RoundedCornerShape(8.dp))
                    .bouncyClickable { viewModel.setArenaOpponentSheetVisible(true) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = ChessTutorColors.Primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (state.useLinkedRatingForBot && state.linkedProfile?.activeRating != null) {
                        "Bot: Tuned (${state.linkedProfile!!.activeRating} Elo)"
                    } else {
                        "Bot: ${currentBot.name} (${state.effectiveBotElo})"
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Change Bot",
                    tint = ChessTutorColors.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Auto Move Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (state.isAutoOpponentEnabled) Color(0xFF243B1D) else Color(0xFF2C2825))
                        .border(
                            1.dp,
                            if (state.isAutoOpponentEnabled) ChessTutorColors.Primary.copy(alpha = 0.6f) else Color(0xFF3D3A34),
                            RoundedCornerShape(8.dp)
                        )
                        .bouncyClickable { viewModel.toggleAutoOpponent() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (state.isAutoOpponentEnabled) ChessTutorColors.Primary else ChessTutorColors.TextSecondary)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (state.isAutoOpponentEnabled) "Auto Move" else "Manual",
                        fontSize = 11.sp,
                        color = if (state.isAutoOpponentEnabled) Color.White else ChessTutorColors.TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }

                IconButton(
                    onClick = { isBoardFlipped = !isBoardFlipped },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF312E2B))
                        .bouncyClickable { isBoardFlipped = !isBoardFlipped }
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Flip Board",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.resetArenaGame() },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF312E2B))
                        .bouncyClickable { viewModel.resetArenaGame() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "New Game",
                        tint = ChessTutorColors.Primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 2. Opponent Player Bar (Selected Bot)
        val botName = if (state.useLinkedRatingForBot) "Adaptive Sparring Bot" else currentBot.name
        val botElo = "${state.effectiveBotElo}"
        ChessPlayerBar(
            name = botName,
            rating = botElo,
            isBot = true,
            isActiveTurn = state.busy || state.opponentThinking,
            isThinking = state.opponentThinking,
            statusBadge = if (state.opponentThinking) null else currentBot.tier
        )

        // 3. Chess Board with Integrated EvalBar
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

        // 4. User Player Bar
        val userRatingStr = state.linkedProfile?.activeRating?.toString() ?: "1500"
        ChessPlayerBar(
            name = state.linkedProfile?.username ?: "You",
            rating = userRatingStr,
            isBot = false,
            isActiveTurn = !state.busy
        )

        // 5. Real-Time Status / Speech Banner
        if (state.arenaStatusText.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF312E2B))
                    .border(1.dp, Color(0xFF3D3A34), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = ChessTutorColors.Primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = state.arenaStatusText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 6. Blunder Warning Banner (if committed)
        AnimatedVisibility(visible = state.mistakeDetected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF3B2522))
                    .border(1.dp, ChessTutorColors.Blunder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = ChessTutorColors.Blunder,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BLUNDER FLAGGED BY STOCKFISH",
                        color = ChessTutorColors.Blunder,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = state.assessment?.coachingLabel ?: "A significant tactical advantage was surrendered.",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 7. Compact Game Actions Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.setArenaOpponentSheetVisible(true) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF312E2B),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3D3A34))
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Choose Bot", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            if (!state.isAutoOpponentEnabled) {
                Button(
                    onClick = { viewModel.triggerOpponentMoveNow() },
                    enabled = !state.busy && !state.opponentThinking,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChessTutorColors.Primary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Bot Move", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

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
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = ChessTutorColors.Primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Hint", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = { viewModel.resetArenaGame() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isAutoOpponentEnabled) ChessTutorColors.Primary else Color(0xFF312E2B),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                border = if (state.isAutoOpponentEnabled) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3D3A34))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // 8. Bot Selection & Calibration Bottom Sheet (Chess.com Style)
    if (state.isArenaOpponentSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setArenaOpponentSheetVisible(false) },
            sheetState = opponentSheetState,
            containerColor = Color(0xFF262522)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Choose Your Opponent",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { viewModel.setArenaOpponentSheetVisible(false) }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Text(
                    text = "Practice against characters with distinct styles or tune to your exact rating.",
                    color = ChessTutorColors.TextSecondary,
                    fontSize = 13.sp
                )

                // Bot Roster Cards
                CHESS_BOTS.forEach { bot ->
                    val isSelected = !state.useLinkedRatingForBot && bot.tier.equals(state.arenaDifficulty, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF383531) else Color(0xFF2B2926))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) ChessTutorColors.Primary else Color(0xFF3D3A34),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .bouncyClickable {
                                viewModel.setArenaDifficulty(bot.tier)
                                viewModel.setArenaOpponentSheetVisible(false)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(bot.avatarBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = bot.name,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF3D3A34))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${bot.elo}",
                                        color = ChessTutorColors.Primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = bot.quote,
                                color = ChessTutorColors.TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Linked Rating Option if user has connected profile
                if (state.linkedProfile != null) {
                    val isLinkedActive = state.useLinkedRatingForBot
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isLinkedActive) Color(0xFF383531) else Color(0xFF2B2926))
                            .border(
                                width = if (isLinkedActive) 2.dp else 1.dp,
                                color = if (isLinkedActive) ChessTutorColors.Primary else Color(0xFF3D3A34),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .bouncyClickable {
                                viewModel.setUseLinkedRatingForBot(true)
                                viewModel.setArenaOpponentSheetVisible(false)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(ChessTutorColors.Primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Your ${state.linkedProfile!!.platform.displayName} Rating (${state.linkedProfile!!.activeRating ?: 1500})",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Engine difficulty automatically dynamically tuned to your actual skill.",
                                color = ChessTutorColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
