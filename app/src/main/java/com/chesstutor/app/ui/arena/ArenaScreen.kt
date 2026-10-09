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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.GameSource
import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.domain.OpeningBook
import com.chesstutor.app.domain.PgnFormatter
import com.chesstutor.app.navigation.OpeningMode
import com.chesstutor.app.ui.components.ChessBoard
import com.chesstutor.app.ui.components.HorizontalEvalBar
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.TabularTextStyle
import com.chesstutor.app.ui.theme.bouncyClickable
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel
import com.chesstutor.app.viewmodel.CoachingLevel
import com.example.chess.engine.BotStrength
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

private enum class ArenaConfirmRequest {
    RESIGN,
    NEW_GAME,
    ABANDON_FOR_SETUP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArenaScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val moveScrollState = rememberScrollState()
    var isBoardFlipped by remember(state.arenaPlayerSide) { mutableStateOf(state.arenaPlayerSide == 'b') }
    var isBotSheetOpen by remember { mutableStateOf(false) }
    val botSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customEloSlider by remember { mutableFloatStateOf(state.customBotElo.toFloat()) }

    // S8: Confirmation dialog state for destructive mid-game actions
    var confirmRequest by remember { mutableStateOf<ArenaConfirmRequest?>(null) }
    var pendingSetupAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val activeFen = state.arenaFen.ifBlank { state.fen }
    val activeLastMove = state.arenaLastMove ?: state.lastMove
    val activeMessage = state.arenaMessage.ifBlank { state.message }
    val currentPosition = remember(activeFen) { runCatching { ChessPosition(activeFen) }.getOrNull() }
    val isGameEnded = (currentPosition?.isOver == true) ||
        state.arenaStatusText.contains("Game Over", ignoreCase = true) ||
        state.arenaStatusText.contains("resigned", ignoreCase = true) ||
        state.arenaStatusText.contains("Draw", ignoreCase = true)
    val isGameInProgress = state.moveHistory.isNotEmpty() && !isGameEnded

    fun runOrConfirmSetupChange(action: () -> Unit) {
        if (isGameInProgress) {
            pendingSetupAction = action
            confirmRequest = ArenaConfirmRequest.ABANDON_FOR_SETUP
        } else {
            action()
        }
    }

    val currentBot = MOCKUP_BOTS.firstOrNull { it.name.equals(state.arenaBotName, ignoreCase = true) }
        ?: MOCKUP_BOTS[1]

    // Auto-scroll moves
    LaunchedEffect(state.moveHistory.size) {
        moveScrollState.animateScrollTo(moveScrollState.maxValue)
    }

    // S2: Compute captured pieces strictly from the game's move history so custom/drill positions never show fake captures
    val capturedData = remember(state.arenaUciHistory) {
        calculateCapturesFromMoveHistory(state.arenaUciHistory)
    }

    val isGuidedOpening = state.selectedOpeningMode == OpeningMode.LEARN_LINE ||
        state.selectedOpeningMode == OpeningMode.SURPRISE_ME
    val targetOpeningLine = remember(state.selectedOpeningLineId) {
        state.selectedOpeningLineId?.let { OpeningBook.byId(it) }
    }

    // S8: Confirmation Dialog
    if (confirmRequest != null) {
        val (title, body, confirmText) = when (confirmRequest!!) {
            ArenaConfirmRequest.RESIGN -> Triple(
                "Resign this game?",
                "Are you sure you want to resign the current game against ${currentBot.name}?",
                "Resign"
            )
            ArenaConfirmRequest.NEW_GAME -> Triple(
                "Start a new game?",
                "Your current game is still in progress. Starting a new game will replace it.",
                "New Game"
            )
            ArenaConfirmRequest.ABANDON_FOR_SETUP -> Triple(
                "Abandon current game?",
                "Changing opponent, colour, or opening mode will restart your current game.",
                "Restart Game"
            )
        }
        AlertDialog(
            onDismissRequest = {
                confirmRequest = null
                pendingSetupAction = null
            },
            containerColor = ChessTutorColors.SurfaceRaised,
            titleContentColor = ChessTutorColors.TextPrimary,
            textContentColor = ChessTutorColors.TextSecondary,
            title = {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = body,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val req = confirmRequest
                        val setupAct = pendingSetupAction
                        confirmRequest = null
                        pendingSetupAction = null
                        when (req) {
                            ArenaConfirmRequest.RESIGN -> viewModel.resignArenaGame()
                            ArenaConfirmRequest.NEW_GAME -> viewModel.startNewArenaGame()
                            ArenaConfirmRequest.ABANDON_FOR_SETUP -> setupAct?.invoke()
                            null -> Unit
                        }
                    },
                    modifier = Modifier.testTag("arena_confirm_dialog_accept")
                ) {
                    Text(
                        text = confirmText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (confirmRequest == ArenaConfirmRequest.RESIGN) ChessTutorColors.Coral else ChessTutorColors.Brass
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmRequest = null
                        pendingSetupAction = null
                    },
                    modifier = Modifier.testTag("arena_confirm_dialog_cancel")
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 14.sp,
                        color = ChessTutorColors.TextSecondary
                    )
                }
            }
        )
    }

    // S3: Responsive layout with BoxWithConstraints and anchored Bottom Action Bar
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ChessTutorColors.Background)
    ) {
        val reservedHeight = if (isGuidedOpening) 335.dp else 290.dp
        val maxBoardFromHeight = (maxHeight - reservedHeight).coerceAtLeast(200.dp)
        val maxBoardFromWidth = (maxWidth - 28.dp).coerceAtLeast(200.dp)
        val boardSize = min(maxBoardFromWidth, maxBoardFromHeight)

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Scrollable upper content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ==================== HEAD ====================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Play",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.TextPrimary
                        )
                        Text(
                            text = if (isGuidedOpening && targetOpeningLine != null) {
                                "Opening Practice · ${targetOpeningLine.name}"
                            } else {
                                "Every move checked as you go"
                            },
                            fontSize = 13.sp,
                            color = ChessTutorColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ChessTutorColors.Surface)
                                .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(12.dp))
                                .testTag("arena_history_button")
                                .bouncyClickable { viewModel.selectTab(3) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Game History",
                                tint = ChessTutorColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ChessTutorColors.Surface)
                                .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(12.dp))
                                .testTag("arena_flip_board_button")
                                .bouncyClickable { isBoardFlipped = !isBoardFlipped },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Flip Board",
                                tint = ChessTutorColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ChessTutorColors.Surface)
                                .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(12.dp))
                                .testTag("arena_settings_button")
                                .bouncyClickable { viewModel.setSettingsVisible(true) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = ChessTutorColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Opponent strip (.player #opp-strip)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ChessTutorColors.Surface)
                        .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(10.dp))
                        .testTag("arena_bot_selector")
                        .bouncyClickable { isBotSheetOpen = true }
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(ChessTutorColors.Surface2)
                            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "Opponent",
                            tint = ChessTutorColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentBot.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ChessTutorColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "· ${state.effectiveBotElo}",
                                fontSize = 13.sp,
                                style = TabularTextStyle,
                                color = ChessTutorColors.TextSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Setup ▾",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ChessTutorColors.Brass
                            )
                        }

                        if (capturedData.whiteCapturedPieces.isNotEmpty() || capturedData.opponentAdvantage > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = capturedData.whiteCapturedPieces,
                                    fontSize = 13.sp,
                                    color = ChessTutorColors.TextSecondary
                                )
                                if (capturedData.opponentAdvantage > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "+${capturedData.opponentAdvantage}",
                                        fontSize = 12.sp,
                                        style = TabularTextStyle,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ChessTutorColors.Sage
                                    )
                                }
                            }
                        }
                    }

                    if (state.opponentThinking) {
                        BlinkingDotsIndicator()
                    }
                }

                // S3 / §2.4: Slim Horizontal Eval Bar above the board
                if (state.coachingLevel == CoachingLevel.LIVE_EVAL || state.coachingLevel == CoachingLevel.FULL_COACH) {
                    HorizontalEvalBar(
                        centipawns = state.evaluationCp,
                        mateIn = state.mateIn,
                        isWhiteOnBottom = !isBoardFlipped,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // S7: Opening Practice Coach Card with next-move guidance and deviation options
                if (isGuidedOpening) {
                    OpeningPracticeCoachCard(
                        state = state,
                        openingLine = targetOpeningLine,
                        openingName = targetOpeningLine?.name ?: state.liveOpeningName ?: "Opening Line",
                        openingEco = targetOpeningLine?.eco ?: state.liveOpeningEco,
                        totalBookPlies = targetOpeningLine?.uciMoves?.size ?: 0,
                        onShowArrow = { viewModel.showOpeningBookMoveArrow() },
                        onRetryBookMove = { viewModel.retryOpeningBookMove() },
                        onContinueFromHere = { viewModel.continueOpeningFromDeviation() },
                        onSwitchToFreePlay = { viewModel.switchOpeningToFreePlay() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp)
                    )
                }

                // Square Board sized dynamically from available width & height
                Box(
                    modifier = Modifier
                        .size(boardSize)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, ChessTutorColors.LineStrong, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    ChessBoard(
                        fen = activeFen,
                        selectedSquare = state.selectedSquare,
                        legalTargets = state.legalTargets,
                        lastMove = activeLastMove,
                        recommendedArrow = state.arenaRecommendedArrow ?: state.recommendedArrow,
                        flipped = isBoardFlipped,
                        showCoordinates = state.showCoordinates,
                        showLegalDots = state.showLegalDots,
                        onSquareTapped = { square ->
                            viewModel.onSquareTapped(square)
                        }
                    )
                }

                // Live Opening Name + ECO & Out-of-Theory Indicator (when in Free Play or From Line)
                if (!isGuidedOpening && (state.liveOpeningName != null || state.outOfTheoryPly != null)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = buildString {
                                if (state.liveOpeningEco != null) append("${state.liveOpeningEco} · ")
                                append(state.liveOpeningName ?: "Custom Line")
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.Brass,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        val theoryBadge = when {
                            state.outOfTheoryPly != null && state.bookContinuationHint != null ->
                                "Left theory (book: ${state.bookContinuationHint})"
                            state.outOfTheoryPly != null ->
                                "Out of theory at ply ${state.outOfTheoryPly}"
                            else -> "In book"
                        }
                        Text(
                            text = theoryBadge,
                            fontSize = 12.sp,
                            style = TabularTextStyle,
                            color = if (state.outOfTheoryPly != null) ChessTutorColors.Coral else ChessTutorColors.Sage,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                // Move Notation Strip — never duplicates state.message
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .horizontalScroll(moveScrollState)
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.moveHistory.isEmpty()) {
                        Text(
                            text = "1. Make a move on the board to begin",
                            fontSize = 13.sp,
                            color = ChessTutorColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
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
                                    fontSize = 13.sp,
                                    style = TabularTextStyle,
                                    color = when {
                                        isMoveNumber -> ChessTutorColors.TextSecondary
                                        isLast -> ChessTutorColors.TextPrimary
                                        else -> ChessTutorColors.TextSecondary
                                    }
                                )
                            }
                        }
                    }
                }

                // Verified Coaching Strip in Play
                val coachingLine = state.assessment?.coachingLabel ?: activeMessage
                if (!isGuidedOpening && state.coachingLevel == CoachingLevel.FULL_COACH && coachingLine.isNotBlank() && !state.mistakeDetected) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(ChessTutorColors.Sage)
                        )
                        Text(
                            text = coachingLine,
                            fontSize = 14.sp,
                            color = ChessTutorColors.TextSecondary,
                            maxLines = 2
                        )
                    }
                }

                // Player Strip ("You")
                val userDisplayRating = state.effectiveUserRating
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(ChessTutorColors.Surface2)
                            .border(1.dp, ChessTutorColors.BrassDim, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "You",
                            tint = ChessTutorColors.Brass,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = state.linkedProfile?.username ?: "You",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ChessTutorColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "· $userDisplayRating",
                                fontSize = 13.sp,
                                style = TabularTextStyle,
                                color = ChessTutorColors.TextSecondary
                            )
                        }

                        if (capturedData.blackCapturedPieces.isNotEmpty() || capturedData.userAdvantage > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = capturedData.blackCapturedPieces,
                                    fontSize = 13.sp,
                                    color = ChessTutorColors.TextSecondary
                                )
                                if (capturedData.userAdvantage > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "+${capturedData.userAdvantage}",
                                        fontSize = 12.sp,
                                        style = TabularTextStyle,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ChessTutorColors.Sage
                                    )
                                }
                            }
                        }
                    }
                }

                // Status Text / Game Over Banner
                if (state.arenaStatusText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ChessTutorColors.Surface2)
                            .border(1.dp, ChessTutorColors.BrassDim, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.arenaStatusText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ChessTutorColors.Brass,
                                modifier = Modifier.weight(1f)
                            )
                            if (state.moveHistory.isNotEmpty()) {
                                Text(
                                    text = "View PGN",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ChessTutorColors.Sage,
                                    modifier = Modifier
                                        .bouncyClickable {
                                            val currentRecord = state.recentGames.firstOrNull { it.source == GameSource.ARENA }
                                                ?: GameRecord(
                                                    id = "current",
                                                    dateMillis = System.currentTimeMillis(),
                                                    botName = state.arenaBotName,
                                                    botRating = state.effectiveBotElo,
                                                    result = if (state.arenaStatusText.contains("Checkmate")) "1-0" else "1/2-1/2",
                                                    pgn = PgnFormatter.formatPgn(
                                                        moves = state.moveHistory,
                                                        whitePlayer = "You",
                                                        blackPlayer = state.arenaBotName,
                                                        blackElo = state.effectiveBotElo
                                                    ),
                                                    moveCount = state.moveHistory.size,
                                                    userColor = if (state.arenaPlayerSide == 'b') "black" else "white",
                                                    finalFen = activeFen,
                                                    source = GameSource.ARENA
                                                )
                                            viewModel.selectGameForPgn(currentRecord)
                                        }
                                        .padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Flag / Missed Tactic Card
                if (state.coachingLevel != CoachingLevel.OFF && (state.mistakeDetected || state.analysis?.tacticalIssue != null)) {
                    val title = state.analysis?.tacticalIssue
                        ?: if (state.mateIn != null && state.mateIn > 0) "You missed mate in one" else "Tactical mistake detected"
                    val subtitle = state.analysis?.explanation
                        ?: state.assessment?.coachingLabel
                        ?: "Tap to see the best move or take back."

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1ADC7466))
                            .border(1.dp, Color(0x4DDC7466), RoundedCornerShape(12.dp))
                            .bouncyClickable {
                                val arrow = state.analysis?.bestAlternativeMove ?: state.recommendedArrow
                                if (arrow != null) {
                                    viewModel.showAnalysisArrow(arrow.first, arrow.second)
                                }
                            }
                            .padding(12.dp, 11.dp),
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
                                color = Color(0xFFF0A196)
                            )
                            Text(
                                text = subtitle,
                                fontSize = 13.sp,
                                color = ChessTutorColors.TextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Text(
                            text = "Retry",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.Brass,
                            modifier = Modifier
                                .bouncyClickable { viewModel.takebackArenaMove() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // S3 & S8: Anchored Bottom Action Bar — always visible above the bottom navigation bar
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(ChessTutorColors.Brass)
                            .testTag("arena_new_game_button")
                            .bouncyClickable {
                                if (isGameInProgress) {
                                    confirmRequest = ArenaConfirmRequest.NEW_GAME
                                } else {
                                    viewModel.startNewArenaGame()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "New game",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.BrassInk
                        )
                    }

                    if (state.moveHistory.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .height(48.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(ChessTutorColors.Surface2)
                                .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(11.dp))
                                .testTag("arena_takeback_button")
                                .bouncyClickable { viewModel.takebackArenaMove() }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Undo",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ChessTutorColors.TextPrimary
                            )
                        }

                        if (state.arenaStatusText.isBlank()) {
                            Box(
                                modifier = Modifier
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(ChessTutorColors.Surface2)
                                    .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(11.dp))
                                    .testTag("arena_resign_button")
                                    .bouncyClickable {
                                        confirmRequest = ArenaConfirmRequest.RESIGN
                                    }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Resign",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ChessTutorColors.Coral
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .height(48.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(ChessTutorColors.Surface2)
                                .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(11.dp))
                                .testTag("arena_view_pgn_button")
                                .bouncyClickable {
                                    val userWhite = state.arenaPlayerSide != 'b'
                                    val currentRecord = state.recentGames.firstOrNull { it.source == GameSource.ARENA }
                                        ?: GameRecord(
                                            id = "current",
                                            dateMillis = System.currentTimeMillis(),
                                            botName = state.arenaBotName,
                                            botRating = state.effectiveBotElo,
                                            result = "*",
                                            pgn = PgnFormatter.formatPgn(
                                                moves = state.moveHistory,
                                                whitePlayer = if (userWhite) "You" else state.arenaBotName,
                                                blackPlayer = if (userWhite) state.arenaBotName else "You",
                                                whiteElo = if (userWhite) null else state.effectiveBotElo,
                                                blackElo = if (userWhite) state.effectiveBotElo else null
                                            ),
                                            moveCount = state.moveHistory.size,
                                            userColor = if (userWhite) "white" else "black",
                                            finalFen = activeFen,
                                            uciMoves = state.arenaUciHistory.joinToString(" "),
                                            source = GameSource.ARENA
                                        )
                                    viewModel.selectGameForPgn(currentRecord)
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "PGN",
                                    tint = ChessTutorColors.TextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "PGN",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ChessTutorColors.TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================== CHOOSE AN OPPONENT SHEET (#sheet-bot) ====================
    if (isBotSheetOpen) {
        androidx.activity.compose.BackHandler { isBotSheetOpen = false }
        ModalBottomSheet(
            onDismissRequest = { isBotSheetOpen = false },
            sheetState = botSheetState,
            containerColor = ChessTutorColors.Surface,
            contentColor = ChessTutorColors.TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Play Setup & Opponent",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChessTutorColors.TextPrimary
                )
                Text(
                    text = "Choose colour, opening practice mode, coaching level, and bot strength",
                    fontSize = 14.sp,
                    color = ChessTutorColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Colour Selector: White / Black / Random
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf('w' to "White", 'b' to "Black", 'r' to "Random").forEach { (sideChar, label) ->
                        val selected = state.arenaPlayerSide == sideChar
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) ChessTutorColors.Brass else ChessTutorColors.Surface2)
                                .border(
                                    1.dp,
                                    if (selected) ChessTutorColors.Brass else ChessTutorColors.Line,
                                    RoundedCornerShape(10.dp)
                                )
                                .bouncyClickable {
                                    isBotSheetOpen = false
                                    runOrConfirmSetupChange {
                                        viewModel.setArenaPlayerSide(sideChar)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) ChessTutorColors.BrassInk else ChessTutorColors.TextPrimary
                            )
                        }
                    }
                }

                // Opening Practice Mode Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        OpeningMode.FREE to "Free",
                        OpeningMode.LEARN_LINE to "Learn Line",
                        OpeningMode.START_FROM_LINE to "From Line",
                        OpeningMode.SURPRISE_ME to "Surprise"
                    ).forEach { (mode, label) ->
                        val selected = state.selectedOpeningMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) ChessTutorColors.Surface3 else ChessTutorColors.Surface2)
                                .border(
                                    1.dp,
                                    if (selected) ChessTutorColors.Brass else ChessTutorColors.Line,
                                    RoundedCornerShape(8.dp)
                                )
                                .bouncyClickable {
                                    isBotSheetOpen = false
                                    runOrConfirmSetupChange {
                                        viewModel.setOpeningPractice(
                                            mode,
                                            state.selectedOpeningLineId ?: OpeningBook.lines.first().id
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) ChessTutorColors.Brass else ChessTutorColors.TextSecondary
                            )
                        }
                    }
                }

                // Coaching Level Selector (safe mid-game change, no game restart required)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CoachingLevel.entries.forEach { level ->
                        val selected = state.coachingLevel == level
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) ChessTutorColors.Surface3 else ChessTutorColors.Surface2)
                                .border(
                                    1.dp,
                                    if (selected) ChessTutorColors.Brass else ChessTutorColors.Line,
                                    RoundedCornerShape(8.dp)
                                )
                                .bouncyClickable { viewModel.setCoachingLevel(level) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = level.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (selected) ChessTutorColors.Brass else ChessTutorColors.TextSecondary
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(MOCKUP_BOTS) { bot ->
                        val isSelected = bot.name.equals(currentBot.name, ignoreCase = true)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) Color(0x0FD6A24C) else ChessTutorColors.Surface2
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) ChessTutorColors.Brass else ChessTutorColors.Line,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .bouncyClickable {
                                    isBotSheetOpen = false
                                    runOrConfirmSetupChange {
                                        viewModel.setArenaBot(bot.tierKey, bot.name, bot.elo)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ChessTutorColors.Surface3)
                                    .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = bot.name,
                                    tint = ChessTutorColors.Brass,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = bot.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ChessTutorColors.TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${bot.elo}",
                                        fontSize = 13.sp,
                                        style = TabularTextStyle,
                                        color = ChessTutorColors.Brass
                                    )
                                }
                                Text(
                                    text = bot.description,
                                    fontSize = 13.sp,
                                    color = ChessTutorColors.TextSecondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Select",
                                tint = ChessTutorColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 20.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ChessTutorColors.Surface2)
                                .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Custom Rating",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ChessTutorColors.TextPrimary
                                )
                                Text(
                                    text = "${customEloSlider.roundToInt()} ELO",
                                    fontSize = 13.sp,
                                    style = TabularTextStyle,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ChessTutorColors.Brass
                                )
                            }

                            Slider(
                                value = customEloSlider,
                                onValueChange = { customEloSlider = it },
                                onValueChangeFinished = {
                                    isBotSheetOpen = false
                                    runOrConfirmSetupChange {
                                        viewModel.setArenaBot("Custom", "Custom Bot", customEloSlider.roundToInt())
                                    }
                                },
                                valueRange = 400f..2600f,
                                colors = SliderDefaults.colors(
                                    thumbColor = ChessTutorColors.Brass,
                                    activeTrackColor = ChessTutorColors.Brass,
                                    inactiveTrackColor = ChessTutorColors.Surface3
                                ),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpeningPracticeCoachCard(
    state: AppUiState,
    openingLine: com.chesstutor.app.domain.OpeningLine?,
    openingName: String,
    openingEco: String?,
    totalBookPlies: Int,
    onShowArrow: () -> Unit,
    onRetryBookMove: () -> Unit,
    onContinueFromHere: () -> Unit,
    onSwitchToFreePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deviated = state.arenaDeviatedUserMoveSan != null && state.arenaDeviatedBookMoveSan != null
    val nextMoveSan = state.arenaNextBookMoveSan
    val nextMoveNote = remember(openingLine, nextMoveSan) {
        OpeningBook.coachingNoteForMove(openingLine, nextMoveSan)
    }
    val currentPly = state.arenaUciHistory.size

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (deviated) ChessTutorColors.Danger.copy(alpha = 0.12f) else ChessTutorColors.Surface
            )
            .border(
                width = 1.dp,
                color = if (deviated) ChessTutorColors.Danger.copy(alpha = 0.55f) else ChessTutorColors.BrassDim,
                shape = RoundedCornerShape(12.dp)
            )
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
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (deviated) Icons.Default.Warning else Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = if (deviated) ChessTutorColors.Coral else ChessTutorColors.Brass,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = buildString {
                        if (!openingEco.isNullOrBlank()) append("$openingEco · ")
                        append(openingName)
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (deviated) ChessTutorColors.Coral else ChessTutorColors.Brass,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (totalBookPlies > 0) {
                Text(
                    text = if (deviated) "Deviation" else "Book ${currentPly.coerceAtMost(totalBookPlies)}/$totalBookPlies",
                    fontSize = 12.sp,
                    style = TabularTextStyle,
                    fontWeight = FontWeight.SemiBold,
                    color = if (deviated) ChessTutorColors.Coral else ChessTutorColors.Sage
                )
            }
        }

        if (deviated) {
            val userMove = state.arenaDeviatedUserMoveSan ?: "Your move"
            val bookMove = state.arenaDeviatedBookMoveSan ?: "book move"
            val bookNote = OpeningBook.coachingNoteForMove(openingLine, bookMove)
            Text(
                text = "$userMove is playable, but the $openingName book move is $bookMove — $bookNote",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = ChessTutorColors.TextPrimary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChessTutorColors.Brass)
                        .testTag("opening_retry_button")
                        .bouncyClickable(onClick = onRetryBookMove)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Try again",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ChessTutorColors.BrassInk
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChessTutorColors.SurfaceRaised)
                        .border(1.dp, ChessTutorColors.LineStrong, RoundedCornerShape(8.dp))
                        .testTag("opening_continue_button")
                        .bouncyClickable(onClick = onContinueFromHere)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Continue from here",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = ChessTutorColors.TextPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChessTutorColors.SurfaceRaised)
                        .border(1.dp, ChessTutorColors.LineStrong, RoundedCornerShape(8.dp))
                        .testTag("opening_free_play_button")
                        .bouncyClickable(onClick = onSwitchToFreePlay)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Free play",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = ChessTutorColors.TextSecondary
                    )
                }
            }
        } else if (nextMoveSan != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your move: $nextMoveSan — $nextMoveNote",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = ChessTutorColors.TextPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChessTutorColors.BrassFaint)
                        .border(1.dp, ChessTutorColors.Brass.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .testTag("opening_show_me_button")
                        .bouncyClickable(onClick = onShowArrow)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Show me",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ChessTutorColors.Brass
                    )
                }
            }
        } else {
            Text(
                text = "Opening book line completed! Continue playing out the middlegame.",
                fontSize = 14.sp,
                color = ChessTutorColors.Sage
            )
        }
    }
}

@Composable
private fun BlinkingDotsIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { index ->
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 450,
                        delayMillis = index * 120,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )
            Box(
                modifier = Modifier
                    .offset(y = offsetY.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(ChessTutorColors.Brass)
            )
        }
    }
}

data class CaptureSummary(
    val whiteCapturedPieces: String,
    val blackCapturedPieces: String,
    val userAdvantage: Int,
    val opponentAdvantage: Int
)

/**
 * S2: Computes captured pieces strictly from the game's move history rather than static FEN diffs,
 * ensuring drill positions or non-standard setups never display phantom captured trays.
 */
internal fun calculateCapturesFromMoveHistory(uciMoves: List<String>): CaptureSummary {
    if (uciMoves.isEmpty()) {
        return CaptureSummary("", "", 0, 0)
    }
    val pos = runCatching { ChessPosition(ChessPosition.STARTING_FEN) }.getOrNull()
        ?: return CaptureSummary("", "", 0, 0)
    val capturedWhitePieces = mutableListOf<Char>()
    val capturedBlackPieces = mutableListOf<Char>()

    for (rawUci in uciMoves) {
        val uci = rawUci.trim().lowercase()
        val legal = pos.legalMoves.firstOrNull { it.uci.equals(uci, ignoreCase = true) } ?: break
        val moverSide = pos.sideToMove
        val movingChar = pos.pieceAt(legal.from)
        val targetChar = pos.pieceAt(legal.to)
        val isEnPassant = movingChar == 'p' &&
            legal.from.length >= 2 &&
            legal.to.length >= 2 &&
            legal.from[0] != legal.to[0] &&
            targetChar == null

        val capturedChar = targetChar ?: if (isEnPassant) 'p' else null
        if (capturedChar != null) {
            if (moverSide == 'w') {
                capturedBlackPieces.add(capturedChar)
            } else {
                capturedWhitePieces.add(capturedChar)
            }
        }
        if (!pos.play(legal)) break
    }

    fun pieceOrder(c: Char): Int = when (c.lowercaseChar()) {
        'q' -> 0
        'r' -> 1
        'b' -> 2
        'n' -> 3
        'p' -> 4
        else -> 5
    }

    fun pieceValue(c: Char): Int = when (c.lowercaseChar()) {
        'q' -> 9
        'r' -> 5
        'b' -> 3
        'n' -> 3
        'p' -> 1
        else -> 0
    }

    val whiteSymbols = capturedWhitePieces.sortedBy(::pieceOrder).joinToString("") {
        when (it.lowercaseChar()) {
            'q' -> "♕"
            'r' -> "♖"
            'b' -> "♗"
            'n' -> "♘"
            'p' -> "♙"
            else -> ""
        }
    }

    val blackSymbols = capturedBlackPieces.sortedBy(::pieceOrder).joinToString("") {
        when (it.lowercaseChar()) {
            'q' -> "♛"
            'r' -> "♜"
            'b' -> "♝"
            'n' -> "♞"
            'p' -> "♟"
            else -> ""
        }
    }

    val userCapturedPoints = capturedBlackPieces.sumOf(::pieceValue)
    val oppCapturedPoints = capturedWhitePieces.sumOf(::pieceValue)
    val diff = userCapturedPoints - oppCapturedPoints

    return CaptureSummary(
        whiteCapturedPieces = whiteSymbols,
        blackCapturedPieces = blackSymbols,
        userAdvantage = if (diff > 0) diff else 0,
        opponentAdvantage = if (diff < 0) -diff else 0
    )
}
