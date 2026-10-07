package com.chesstutor.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.domain.PgnFormatter
import com.chesstutor.app.ui.theme.ChessTutorColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PgnViewerDialog(
    game: GameRecord,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    val displayPgn = remember(game.id, game.uciMoves, game.pgn) {
        PgnFormatter.reconstructPgnFromGameRecord(game)
    }

    val formattedDate = remember(game.dateMillis) {
        val sdf = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())
        sdf.format(Date(game.dateMillis))
    }

    val resultBadgeColor = when {
        game.result.startsWith("1-0") && game.userColor == "white" -> ChessTutorColors.Sage
        game.result.startsWith("0-1") && game.userColor == "black" -> ChessTutorColors.Sage
        game.result.startsWith("1/2") -> ChessTutorColors.Amber
        else -> ChessTutorColors.Coral
    }

    val resultLabel = when {
        game.result.startsWith("1-0") && game.userColor == "white" -> "Victory (1-0)"
        game.result.startsWith("0-1") && game.userColor == "black" -> "Victory (0-1)"
        game.result.startsWith("1/2") -> "Draw (½-½)"
        game.result.startsWith("1-0") -> "Defeat (1-0)"
        game.result.startsWith("0-1") -> "Defeat (0-1)"
        else -> game.result
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ChessTutorColors.Surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("pgn_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = ChessTutorColors.Brass,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "vs. ${game.botName} (${game.botRating})",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ChessTutorColors.TextPrimary
                            )
                            Text(
                                text = formattedDate,
                                fontSize = 12.sp,
                                color = ChessTutorColors.TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("pgn_dialog_close")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ChessTutorColors.TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Result & Moves Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(resultBadgeColor.copy(alpha = 0.18f))
                            .border(1.dp, resultBadgeColor, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = resultLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = resultBadgeColor
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ChessTutorColors.Surface2)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${game.moveCount} moves",
                            fontSize = 12.sp,
                            color = ChessTutorColors.TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // PGN Text Block
                Text(
                    text = "PGN NOTATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.05.sp,
                    color = ChessTutorColors.TextTertiary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 240.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ChessTutorColors.Background)
                        .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = displayPgn.ifBlank { "No moves recorded for this game." },
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = ChessTutorColors.TextPrimary,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .testTag("pgn_text_content")
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Chess Game PGN", displayPgn)
                            clipboard.setPrimaryClip(clip)
                            isCopied = true
                            scope.launch {
                                delay(2000)
                                isCopied = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCopied) ChessTutorColors.Sage else ChessTutorColors.Brass,
                            contentColor = ChessTutorColors.Background
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pgn_dialog_copy")
                    ) {
                        Icon(
                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isCopied) "Copied!" else "Copy PGN",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    if (onDelete != null) {
                        OutlinedButton(
                            onClick = onDelete,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ChessTutorColors.Coral
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("pgn_dialog_delete")
                        ) {
                            Text(text = "Delete", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
