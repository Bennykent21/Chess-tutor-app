package com.chesstutor.app.ui.arena

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.ui.components.ChessBoard
import com.chesstutor.app.ui.components.EvalBar
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.bouncyClickable
import com.example.chess.engine.BotStrength
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel
import kotlin.math.roundToInt

data class MockupBot(
    val name: String,
    val elo: Int,
    val description: String,
    val tierKey: String
)

val MOCKUP_BOTS = BotStrength.presets.map {
    MockupBot(it.name, it.rating, it.description, it.key)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArenaScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val moveScrollState = rememberScrollState()
    var isBoardFlipped by remember { mutableStateOf(false) }
    var isBotSheetOpen by remember { mutableStateOf(false) }
    val botSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var customEloSlider by remember { mutableFloatStateOf(1700f) }

    val currentBot = MOCKUP_BOTS.firstOrNull { it.name.equals(state.arenaBotName, ignoreCase = true) }
        ?: MOCKUP_BOTS[1] // Default Wayne (600)

    // Auto-scroll moves
    LaunchedEffect(state.moveHistory.size) {
        moveScrollState.animateScrollTo(moveScrollState.maxValue)
    }

    // Material counting for captured pieces tray
    val capturedData = remember(state.fen) {
        calculateCaptures(state.fen)
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
                    text = "Play",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.015).sp,
                    color = ChessTutorColors.TextPrimary
                )
                Text(
                    text = "Every move checked as you go",
                    fontSize = 12.5.sp,
                    letterSpacing = (-0.005).sp,
                    color = ChessTutorColors.TextSecondary,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .bouncyClickable { viewModel.setSettingsVisible(true) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
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
                // Opponent strip (.player #opp-strip) - inset by 16dp.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .bouncyClickable { isBotSheetOpen = true }
                        .padding(vertical = 9.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Robot Avatar (32x32, radius 9dp, surface-2, line border)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(ChessTutorColors.Surface2)
                            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "Opponent",
                            tint = ChessTutorColors.TextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentBot.name,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.01).sp,
                                color = ChessTutorColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "· ${state.effectiveBotElo}",
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = ChessTutorColors.TextTertiary
                            )
                        }

                        // Captured tray for Opponent
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = capturedData.whiteCapturedPieces,
                                fontSize = 11.sp,
                                color = ChessTutorColors.TextSecondary
                            )
                            if (capturedData.opponentAdvantage > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "+${capturedData.opponentAdvantage}",
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ChessTutorColors.Sage
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 3-dot thinking indicator
                    if (state.opponentThinking) {
                        BlinkingDotsIndicator()
                    }
                }

                // Board Row: Eval Bar (22dp) + Board, spanning the FULL
                // screen width edge-to-edge - no horizontal padding here.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp)
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

                // Move Notation Strip (.moves #mv-play) - inset by 16dp.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .horizontalScroll(moveScrollState)
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.moveHistory.isEmpty()) {
                        Text(
                            text = "Game in progress...",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = ChessTutorColors.TextTertiary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    } else {
                        state.moveHistory.forEachIndexed { i, moveText ->
                            val isLast = i == state.moveHistory.size - 1
                            val isMoveNumber = moveText.endsWith(".")

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isLast && !isMoveNumber) ChessTutorColors.Surface2 else Color.Transparent)
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = moveText,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = when {
                                        isMoveNumber -> ChessTutorColors.TextTertiary
                                        isLast -> ChessTutorColors.TextPrimary
                                        else -> ChessTutorColors.TextSecondary
                                    }
                                )
                            }
                        }
                    }
                }

                // Player Strip ("You") - inset by 16dp.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(ChessTutorColors.Surface2)
                            .border(1.dp, ChessTutorColors.BrassDim, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "You",
                            tint = ChessTutorColors.Brass,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "You",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.01).sp,
                                color = ChessTutorColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "· ${state.linkedProfile?.activeRating ?: 1500}",
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = ChessTutorColors.TextTertiary
                            )
                        }

                        // Captured tray for You
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = capturedData.blackCapturedPieces,
                                fontSize = 11.sp,
                                color = ChessTutorColors.TextSecondary
                            )
                            if (capturedData.userAdvantage > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "+${capturedData.userAdvantage}",
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ChessTutorColors.Sage
                                )
                            }
                        }
                    }
                }

                // Flag / Missed Tactic Card (.flag #flag-play) - inset by 16dp.
                if (state.mistakeDetected || state.analysis?.tacticalIssue != null) {
                    val issue = state.analysis?.tacticalIssue
                    val title = if (state.mateIn != null && state.mateIn > 0) "You missed mate in one" else "Tactical mistake detected"
                    val subtitle = state.analysis?.explanation ?: "Tap to see the best move."

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1ADC7466))
                            .border(1.dp, Color(0x4DDC7466), RoundedCornerShape(12.dp))
                            .bouncyClickable {
                                state.analysis?.bestAlternativeMove?.let { bestMove ->
                                    viewModel.showAnalysisArrow(bestMove.first, bestMove.second)
                                }
                            }
                            .padding(12.dp, 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Alert",
                            tint = ChessTutorColors.Coral,
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.01).sp,
                                color = Color(0xFFF0A196)
                            )
                            Text(
                                text = subtitle,
                                fontSize = 12.sp,
                                color = ChessTutorColors.TextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Inspect",
                            tint = ChessTutorColors.TextTertiary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // Actions: [ New Game ] Primary Brass Button - inset by 16dp.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp, top = 10.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(ChessTutorColors.Brass)
                    .bouncyClickable { viewModel.startNewArenaGame() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "New game",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.008).sp,
                    color = ChessTutorColors.BrassInk
                )
            }
        }
    }

    // ==================== CHOOSE AN OPPONENT SHEET (#sheet-bot) ====================
    if (isBotSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isBotSheetOpen = false },
            sheetState = botSheetState,
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
                    text = "Choose an opponent",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.015).sp,
                    color = ChessTutorColors.TextPrimary
                )
                Text(
                    text = "Strength is matched to your linked rating",
                    fontSize = 12.5.sp,
                    color = ChessTutorColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(MOCKUP_BOTS) { bot ->
                        val isSelected = bot.name.equals(currentBot.name, ignoreCase = true)

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
                                    viewModel.setArenaBot(bot.tierKey, bot.name, bot.elo)
                                    isBotSheetOpen = false
                                }
                                .padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bot.name,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = (-0.01).sp,
                                    color = ChessTutorColors.TextPrimary
                                )
                                Text(
                                    text = bot.description,
                                    fontSize = 12.sp,
                                    color = ChessTutorColors.TextSecondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            Text(
                                text = "${bot.elo}",
                                fontSize = 12.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = ChessTutorColors.TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "OR SET EXACT STRENGTH",
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
                        .padding(13.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Custom Elo",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = ChessTutorColors.TextPrimary
                        )
                        Text(
                            text = "${customEloSlider.roundToInt()}",
                            fontSize = 14.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.Brass
                        )
                    }

                    Slider(
                        value = customEloSlider,
                        onValueChange = { customEloSlider = it },
                        valueRange = 250f..3200f,
                        steps = 25,
                        colors = SliderDefaults.colors(
                            thumbColor = ChessTutorColors.Brass,
                            activeTrackColor = ChessTutorColors.Brass,
                            inactiveTrackColor = ChessTutorColors.Surface3
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(ChessTutorColors.Brass)
                            .bouncyClickable {
                                viewModel.setArenaBot("Custom", "Stockfish", customEloSlider.roundToInt())
                                isBotSheetOpen = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Use this strength",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.BrassInk
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun BlinkingDotsIndicator() {
    val transition = rememberInfiniteTransition(label = "dots")
    val dot1 by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dot2 by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 160, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dot3 by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 320, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(ChessTutorColors.Brass.copy(alpha = dot1)))
        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(ChessTutorColors.Brass.copy(alpha = dot2)))
        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(ChessTutorColors.Brass.copy(alpha = dot3)))
    }
}

data class CapturedInfo(
    val whiteCapturedPieces: String,
    val blackCapturedPieces: String,
    val userAdvantage: Int,
    val opponentAdvantage: Int
)

private fun calculateCaptures(fen: String): CapturedInfo {
    val boardPart = fen.split(" ").firstOrNull() ?: ""

    var whiteP = 8; var whiteN = 2; var whiteB = 2; var whiteR = 2; var whiteQ = 1
    var blackP = 8; var blackN = 2; var blackB = 2; var blackR = 2; var blackQ = 1

    boardPart.forEach { c ->
        when (c) {
            'P' -> whiteP--
            'N' -> whiteN--
            'B' -> whiteB--
            'R' -> whiteR--
            'Q' -> whiteQ--
            'p' -> blackP--
            'n' -> blackN--
            'b' -> blackB--
            'r' -> blackR--
            'q' -> blackQ--
        }
    }

    // Black pieces captured by You (White)
    val userScore = (blackP.coerceAtLeast(0) * 1) + (blackN.coerceAtLeast(0) * 3) +
            (blackB.coerceAtLeast(0) * 3) + (blackR.coerceAtLeast(0) * 5) + (blackQ.coerceAtLeast(0) * 9)

    // White pieces captured by Opponent (Black)
    val oppScore = (whiteP.coerceAtLeast(0) * 1) + (whiteN.coerceAtLeast(0) * 3) +
            (whiteB.coerceAtLeast(0) * 3) + (whiteR.coerceAtLeast(0) * 5) + (whiteQ.coerceAtLeast(0) * 9)

    val diff = userScore - oppScore

    val userTray = buildString {
        repeat(blackQ.coerceAtLeast(0)) { append("♛") }
        repeat(blackR.coerceAtLeast(0)) { append("♜") }
        repeat(blackB.coerceAtLeast(0)) { append("♝") }
        repeat(blackN.coerceAtLeast(0)) { append("♞") }
        repeat(blackP.coerceAtLeast(0)) { append("♟") }
    }

    val oppTray = buildString {
        repeat(whiteQ.coerceAtLeast(0)) { append("♕") }
        repeat(whiteR.coerceAtLeast(0)) { append("♖") }
        repeat(whiteB.coerceAtLeast(0)) { append("♗") }
        repeat(whiteN.coerceAtLeast(0)) { append("♘") }
        repeat(whiteP.coerceAtLeast(0)) { append("♙") }
    }

    return CapturedInfo(
        whiteCapturedPieces = oppTray,
        blackCapturedPieces = userTray,
        userAdvantage = if (diff > 0) diff else 0,
        opponentAdvantage = if (diff < 0) -diff else 0
    )
}
