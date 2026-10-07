package com.chesstutor.app.ui.arena

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.engine.GameAnalysisResult
import com.chesstutor.app.engine.GameAnalysisStatus
import com.chesstutor.app.engine.GameMoveAnalysis
import com.chesstutor.app.engine.MoveClassification
import com.chesstutor.app.ui.components.ChessBoard
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.bouncyClickable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameHistorySheet(
    games: List<GameRecord>,
    analysis: GameAnalysisResult?,
    analyzingGameId: String? = null,
    isAnalyzingGame: Boolean = analyzingGameId != null,
    onSelectGame: (GameRecord) -> Unit,
    onAnalyzeGame: (GameRecord) -> Unit,
    onClearAnalysis: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val analyzedGame = remember(analysis?.gameId, games) {
        games.firstOrNull { it.id == analysis?.gameId }
    }
    var selectedPly by remember(analysis?.gameId) {
        mutableStateOf(analysis?.defaultSelectedMove?.ply)
    }
    var showAfterMovePosition by remember(analysis?.gameId, selectedPly) {
        mutableStateOf(false)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
                .padding(horizontal = 16.dp)
                .testTag("game_history_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ChessTutorColors.Surface2)
                            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = ChessTutorColors.Brass,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Game History",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.TextPrimary
                        )
                        Text(
                            text = "${games.size} saved arena matches",
                            fontSize = 12.sp,
                            color = ChessTutorColors.TextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("game_history_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = ChessTutorColors.TextSecondary
                    )
                }
            }

            if (games.isEmpty() && analysis == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(ChessTutorColors.Surface2),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = ChessTutorColors.Brass,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "No Games Recorded Yet",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ChessTutorColors.TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Play a full game against any bot to store and review it here.",
                        fontSize = 12.5.sp,
                        color = ChessTutorColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    analysis?.let { result ->
                        item(key = "analysis_summary_${result.gameId}") {
                            GameAnalysisSummaryCard(
                                result = result,
                                analyzedGame = analyzedGame,
                                onClearAnalysis = onClearAnalysis
                            )
                        }

                        val userMoves = result.userMoves
                        val activeMove = userMoves.firstOrNull { it.ply == selectedPly }
                            ?: result.defaultSelectedMove

                        if (activeMove != null) {
                            item(key = "analysis_detail_${result.gameId}_${activeMove.ply}") {
                                MoveAnalysisDetailCard(
                                    move = activeMove,
                                    userIsBlack = analyzedGame?.userColor?.equals("black", ignoreCase = true) == true,
                                    showAfterMove = showAfterMovePosition,
                                    onTogglePositionView = { showAfterMovePosition = it }
                                )
                            }
                        }

                        if (userMoves.isNotEmpty()) {
                            item(key = "analysis_moves_header_${result.gameId}") {
                                Text(
                                    text = "Moves (${userMoves.size} analyzed — tap a move to inspect)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ChessTutorColors.TextSecondary,
                                    modifier = Modifier
                                        .padding(top = 6.dp, bottom = 2.dp)
                                        .testTag("analysis_moves_header")
                                )
                            }

                            items(
                                items = userMoves,
                                key = { move -> "analysis_move_${result.gameId}_${move.ply}" }
                            ) { move ->
                                AnalyzedMoveListItem(
                                    move = move,
                                    isSelected = activeMove?.ply == move.ply,
                                    onClick = {
                                        selectedPly = move.ply
                                        showAfterMovePosition = false
                                    }
                                )
                            }

                            item(key = "saved_games_divider") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Saved Games",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ChessTutorColors.TextSecondary,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }
                    }

                    items(games, key = { it.id }) { game ->
                        val isThisGameAnalyzing = if (analyzingGameId != null) {
                            analyzingGameId == game.id
                        } else {
                            isAnalyzingGame && analysis?.gameId == game.id
                        }
                        GameHistoryRow(
                            game = game,
                            onClick = { onSelectGame(game) },
                            isAnalyzing = isThisGameAnalyzing,
                            analyzeEnabled = !isAnalyzingGame && analyzingGameId == null,
                            isSelectedAnalysis = analysis?.gameId == game.id,
                            onAnalyze = { onAnalyzeGame(game) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameAnalysisSummaryCard(
    result: GameAnalysisResult,
    analyzedGame: GameRecord?,
    onClearAnalysis: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ChessTutorColors.Surface2)
            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(12.dp))
            .padding(14.dp)
            .testTag("game_analysis_summary_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (analyzedGame != null) "Game Analysis · vs. ${analyzedGame.botName}" else "Game Analysis",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChessTutorColors.TextPrimary
                )
                val statusLabel = when (result.status) {
                    GameAnalysisStatus.COMPLETE -> "Analysis complete"
                    GameAnalysisStatus.PARTIALLY_COMPLETE ->
                        "Analysis partially complete · ${result.skippedMoves} move(s) could not be analyzed"
                    GameAnalysisStatus.LEGACY_UNAVAILABLE ->
                        "Analysis unavailable"
                    GameAnalysisStatus.UNAVAILABLE ->
                        "Analysis unavailable"
                }
                val statusColor = when (result.status) {
                    GameAnalysisStatus.COMPLETE -> ChessTutorColors.Sage
                    GameAnalysisStatus.PARTIALLY_COMPLETE -> ChessTutorColors.Amber
                    GameAnalysisStatus.LEGACY_UNAVAILABLE, GameAnalysisStatus.UNAVAILABLE -> ChessTutorColors.Coral
                }
                Text(
                    text = statusLabel,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = statusColor,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .testTag("game_analysis_status_text")
                )
            }

            TextButton(
                onClick = onClearAnalysis,
                modifier = Modifier.testTag("game_analysis_close_button")
            ) {
                Text(
                    text = "Hide",
                    fontSize = 12.sp,
                    color = ChessTutorColors.TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            result.status == GameAnalysisStatus.LEGACY_UNAVAILABLE ||
                (result.analyzedMoves.isEmpty() && analyzedGame != null && analyzedGame.uciMoves.isBlank()) -> {
                Text(
                    text = "This game was saved before move replay was available. Analysis cannot be generated for this game.",
                    fontSize = 12.5.sp,
                    color = ChessTutorColors.TextSecondary,
                    modifier = Modifier.testTag("game_analysis_legacy_message")
                )
            }
            result.status == GameAnalysisStatus.UNAVAILABLE || result.analyzedMoves.isEmpty() -> {
                Text(
                    text = if (result.skippedMoves > 0) {
                        "Analysis unavailable. ${result.skippedMoves} move(s) could not be analyzed."
                    } else {
                        "No replayable moves are available for this game."
                    },
                    fontSize = 12.5.sp,
                    color = ChessTutorColors.TextSecondary,
                    modifier = Modifier.testTag("game_analysis_unavailable_message")
                )
            }
            else -> {
                Text(
                    text = "${result.userBlunders.size} blunders · ${result.missedForcedMates} missed mates · ${result.centipawnLoss} cp loss",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = ChessTutorColors.TextPrimary,
                    modifier = Modifier.testTag("game_analysis_overall_metrics")
                )
                if (result.userMistakes.isNotEmpty() || result.userInaccuracies.isNotEmpty()) {
                    Text(
                        text = "${result.userMistakes.size} mistakes · ${result.userInaccuracies.size} inaccuracies across ${result.userMoveCount} user moves",
                        fontSize = 11.5.sp,
                        color = ChessTutorColors.TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MoveAnalysisDetailCard(
    move: GameMoveAnalysis,
    userIsBlack: Boolean,
    showAfterMove: Boolean,
    onTogglePositionView: (Boolean) -> Unit
) {
    val verdictColor = classificationColor(move.classification)
    val bestArrow = if (!showAfterMove && move.bestMoveUci.length >= 4) {
        Pair(move.bestMoveUci.take(2), move.bestMoveUci.substring(2, 4))
    } else {
        null
    }
    val playedLastMove = if (move.uci.length >= 4) {
        Pair(move.uci.take(2), move.uci.substring(2, 4))
    } else {
        null
    }
    val badSquare = if (!showAfterMove && move.classification.isSuboptimal && move.uci.length >= 4) {
        move.uci.substring(2, 4)
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ChessTutorColors.Surface2)
            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(12.dp))
            .padding(14.dp)
            .testTag("move_analysis_detail_card")
    ) {
        // Header: Move number + Verdict badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Move ${move.moveNumber}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = ChessTutorColors.TextPrimary,
                modifier = Modifier.testTag("move_analysis_number")
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(verdictColor.copy(alpha = 0.16f))
                    .border(1.dp, verdictColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .testTag("move_analysis_verdict_badge")
            ) {
                Text(
                    text = move.classification.label.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = verdictColor
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Board visualization (Position before move / After move toggle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PositionToggleChip(
                text = "Before Move (Best: ${move.bestMoveSan})",
                selected = !showAfterMove,
                onClick = { onTogglePositionView(false) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("analysis_board_before_toggle")
            )
            PositionToggleChip(
                text = "After ${move.annotatedSan}",
                selected = showAfterMove,
                onClick = { onTogglePositionView(true) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("analysis_board_after_toggle")
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(10.dp))
                .testTag("analysis_board_container")
        ) {
            ChessBoard(
                fen = if (showAfterMove) move.afterFen else move.beforeFen,
                flipped = userIsBlack,
                lastMove = playedLastMove,
                badSquare = badSquare,
                recommendedArrow = bestArrow,
                onSquareTapped = {}
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Structured Move Comparison Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MoveMetricBox(
                label = "You played",
                value = move.annotatedSan,
                valueColor = if (move.classification.isSuboptimal) verdictColor else ChessTutorColors.TextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("move_analysis_played")
            )
            MoveMetricBox(
                label = "Best move",
                value = move.bestMoveSan,
                valueColor = ChessTutorColors.Sage,
                modifier = Modifier
                    .weight(1f)
                    .testTag("move_analysis_best")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MoveMetricBox(
                label = "Evaluation",
                value = "${move.formattedEvalBefore} → ${move.formattedEvalAfter}",
                valueColor = ChessTutorColors.TextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("move_analysis_evaluation")
            )
            MoveMetricBox(
                label = "Loss",
                value = "${move.centipawnLoss} cp",
                valueColor = if (move.centipawnLoss >= 100) verdictColor else ChessTutorColors.TextSecondary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("move_analysis_loss")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Consequence / Coaching Explanation
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ChessTutorColors.Background)
                .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(8.dp))
                .padding(10.dp)
                .testTag("move_analysis_consequence_box")
        ) {
            Text(
                text = "Consequence",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChessTutorColors.TextTertiary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = move.consequence,
                fontSize = 12.5.sp,
                color = ChessTutorColors.TextPrimary,
                modifier = Modifier.testTag("move_analysis_consequence_text")
            )
        }
    }
}

@Composable
private fun PositionToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) ChessTutorColors.Brass.copy(alpha = 0.2f) else ChessTutorColors.Background)
            .border(
                width = 1.dp,
                color = if (selected) ChessTutorColors.Brass else ChessTutorColors.Line,
                shape = RoundedCornerShape(8.dp)
            )
            .bouncyClickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) ChessTutorColors.Brass else ChessTutorColors.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MoveMetricBox(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ChessTutorColors.Background)
            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = ChessTutorColors.TextTertiary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
private fun AnalyzedMoveListItem(
    move: GameMoveAnalysis,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val badgeColor = classificationColor(move.classification)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) ChessTutorColors.Surface3 else ChessTutorColors.Surface2)
            .border(
                width = 1.dp,
                color = if (isSelected) ChessTutorColors.Brass else ChessTutorColors.Line,
                shape = RoundedCornerShape(10.dp)
            )
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("analysis_move_item_${move.ply}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Move ${move.moveNumber} — ${move.annotatedSan}",
                    fontSize = 13.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = ChessTutorColors.TextPrimary
                )
                if (move.classification.isSuboptimal) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Best: ${move.bestMoveSan}",
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = ChessTutorColors.Sage
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${move.formattedEvalBefore} → ${move.formattedEvalAfter} · ${move.centipawnLoss} cp loss",
                fontSize = 11.sp,
                color = ChessTutorColors.TextSecondary
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(badgeColor.copy(alpha = 0.16f))
                .border(1.dp, badgeColor, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = move.classification.label.uppercase(),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor
            )
        }
    }
}

private fun classificationColor(classification: MoveClassification): Color = when (classification) {
    MoveClassification.BLUNDER,
    MoveClassification.MISSED_FORCED_MATE,
    MoveClassification.WALKED_INTO_FORCED_MATE -> ChessTutorColors.Coral
    MoveClassification.MISTAKE -> ChessTutorColors.Amber
    MoveClassification.INACCURACY -> ChessTutorColors.Brass
    MoveClassification.NORMAL -> ChessTutorColors.Sage
}

@Composable
private fun GameHistoryRow(
    game: GameRecord,
    onClick: () -> Unit,
    isAnalyzing: Boolean,
    analyzeEnabled: Boolean = !isAnalyzing,
    isSelectedAnalysis: Boolean = false,
    onAnalyze: () -> Unit
) {
    val formattedDate = remember(game.dateMillis) {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        sdf.format(Date(game.dateMillis))
    }

    val resultBadgeColor = when {
        game.result.startsWith("1-0") && game.userColor == "white" -> ChessTutorColors.Sage
        game.result.startsWith("0-1") && game.userColor == "black" -> ChessTutorColors.Sage
        game.result.startsWith("1/2") -> ChessTutorColors.Amber
        else -> ChessTutorColors.Coral
    }

    val resultText = when {
        game.result.startsWith("1-0") && game.userColor == "white" -> "WIN"
        game.result.startsWith("0-1") && game.userColor == "black" -> "WIN"
        game.result.startsWith("1/2") -> "DRAW"
        else -> "LOSS"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ChessTutorColors.Surface2)
            .border(
                width = 1.dp,
                color = if (isSelectedAnalysis) ChessTutorColors.Brass else ChessTutorColors.Line,
                shape = RoundedCornerShape(12.dp)
            )
            .bouncyClickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Result Badge
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(resultBadgeColor.copy(alpha = 0.15f))
                .border(1.dp, resultBadgeColor, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = resultText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = resultBadgeColor
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "vs. ${game.botName}",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChessTutorColors.TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "(${game.botRating})",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = ChessTutorColors.TextTertiary
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "$formattedDate · ${game.moveCount} moves · ${game.userColor.replaceFirstChar { it.uppercase() }}",
                fontSize = 11.5.sp,
                color = ChessTutorColors.TextSecondary
            )
        }

        TextButton(
            onClick = onAnalyze,
            enabled = analyzeEnabled,
            modifier = Modifier.testTag("game_analyze_button_${game.id}")
        ) {
            if (isAnalyzing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(16.dp)
                        .testTag("game_analyzing_indicator_${game.id}"),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = if (isSelectedAnalysis) "Re-analyze" else "Analyze",
                    fontSize = 12.sp,
                    color = ChessTutorColors.Brass
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "View PGN",
            tint = ChessTutorColors.TextTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

