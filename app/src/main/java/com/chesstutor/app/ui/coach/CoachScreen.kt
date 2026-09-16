package com.chesstutor.app.ui.coach

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.SmartToy
import com.chesstutor.app.domain.VerifiedConsequence
import com.chesstutor.app.ui.components.ChessBoard
import com.chesstutor.app.ui.components.ChessPlayerBar
import com.chesstutor.app.ui.components.CoachSpeechBubble
import com.chesstutor.app.ui.components.EvalBar
import com.chesstutor.app.ui.components.MoveClassification
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.bouncyClickable
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel

@Composable
fun CoachScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var isBoardFlipped by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChessTutorColors.Background)
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Sleek Header Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.activeCoachTitle,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = state.activeCoachSubtitle,
                    color = ChessTutorColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Auto Opponent Toggle Pill
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
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (state.isAutoOpponentEnabled) "Auto Engine" else "Manual",
                        fontSize = 11.sp,
                        color = if (state.isAutoOpponentEnabled) Color.White else ChessTutorColors.TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }

                IconButton(
                    onClick = { isBoardFlipped = !isBoardFlipped },
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChessTutorColors.SurfaceElevated)
                        .bouncyClickable { isBoardFlipped = !isBoardFlipped }
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Flip Board",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 2. Opponent Player Bar (Stockfish 16 / Coach)
        ChessPlayerBar(
            name = "Stockfish 16 Engine",
            rating = "3200",
            isBot = true,
            isThinking = state.opponentThinking,
            statusBadge = if (state.opponentThinking) null else state.activeCoachCategory
        )

        // 3. The Chess Board with Integrated Side Eval Bar
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

        // 5. Coach Speech Bubble (Chess.com Style)
        val classification = when {
            state.mistakeDetected -> MoveClassification.BLUNDER
            state.assessment?.mateInMovesAfter == 0 -> MoveClassification.EXCELLENT
            state.assessment?.verifiedConsequences?.isEmpty() == true -> MoveClassification.BEST
            state.hintLevel > 0 -> MoveClassification.COACH
            else -> MoveClassification.COACH
        }

        val speechTitle = when {
            state.mistakeDetected -> "VERIFIED MISTAKE DETECTED"
            state.assessment?.mateInMovesAfter == 0 -> "CHECKMATE DELIVERED!"
            state.hintLevel > 0 -> "HINT LADDER: LEVEL ${state.hintLevel} OF 4"
            else -> "COACH'S INSIGHT"
        }

        val speechText = when {
            state.mistakeDetected -> state.assessment?.coachingLabel
                ?: "You missed a decisive continuation. Review the position and find the fact-based win!"
            state.hintLevel > 0 -> state.hintText
            state.message.isNotBlank() -> state.message
            else -> "Study the position carefully. Search for undefended pieces, king exposure, or forced mating nets."
        }

        CoachSpeechBubble(
            message = speechText,
            classification = classification,
            title = speechTitle
        )

        // 6. Inline Guided Retry Banner if mistake committed
        AnimatedVisibility(
            visible = state.mistakeDetected && state.canRetryMistake,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF312E2B))
                    .border(1.dp, ChessTutorColors.Blunder.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Position Queued for Spaced Repetition",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Reset the board to retry without penalty.",
                        color = ChessTutorColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Button(
                    onClick = { viewModel.retryMistake() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChessTutorColors.Primary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 7. Compact Action Bar (Chess.com Bottom HUD)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Hint Ladder
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
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = ChessTutorColors.Primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (state.hintLevel == 0) "Hint" else "Hint (${state.hintLevel}/4)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Demonstrate Move (if available)
            if (state.activeCoachRecommendedMove != null) {
                Button(
                    onClick = { viewModel.playActivePrincipleMove() },
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChessTutorColors.Primary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Play ${state.activeCoachRecommendedMove}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Next Tactical Puzzle / Drill
                Button(
                    onClick = { viewModel.loadSampleMistakeForReview() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF312E2B),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3D3A34))
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Next Drill", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 8. Fact-Based Tactical Truth Breakdown
        if (state.assessment != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2B2926))
                    .border(1.dp, Color(0xFF3D3A34), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "VERIFIABLE ENGINE TRUTH",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ChessTutorColors.TextSecondary,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Search Depth: ${state.assessment.confidence.depth} ply",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Text(
                        text = if (state.assessment.evaluationBeforeCp != null) {
                            "Eval: %+.1f".format(state.assessment.evaluationBeforeCp / 100.0)
                        } else "Mating Net",
                        color = ChessTutorColors.Primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (state.assessment.verifiedConsequences.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    state.assessment.verifiedConsequences.forEach { consequence ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = ChessTutorColors.Blunder,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (consequence) {
                                    VerifiedConsequence.MISSED_FORCED_MATE -> "Mathematically verified missed forced checkmate."
                                    VerifiedConsequence.WALKED_INTO_FORCED_MATE -> "Move allows opponent an unavoidable mating sequence."
                                    VerifiedConsequence.MATERIAL_LOST_BY_FORCE -> "Loss of significant material by force."
                                    VerifiedConsequence.HANGING_PIECE -> "A piece was left unprotected and tactically undefended."
                                    VerifiedConsequence.FORK_OPPORTUNITY_MISSED -> "Missed a tactical fork winning material."
                                    VerifiedConsequence.ALLOWED_ENEMY_FORK -> "Allowed the opponent an immediate fork attack."
                                },
                                color = ChessTutorColors.TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
