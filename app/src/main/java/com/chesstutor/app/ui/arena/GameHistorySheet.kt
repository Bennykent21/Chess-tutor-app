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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.bouncyClickable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameHistorySheet(
    games: List<GameRecord>,
    onSelectGame: (GameRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                    .padding(bottom = 16.dp),
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

            if (games.isEmpty()) {
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
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(games, key = { it.id }) { game ->
                        GameHistoryRow(
                            game = game,
                            onClick = { onSelectGame(game) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameHistoryRow(
    game: GameRecord,
    onClick: () -> Unit
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
            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(12.dp))
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

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "View PGN",
            tint = ChessTutorColors.TextTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}
