package com.chesstutor.app.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.bouncyClickable
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    state: AppUiState,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    var moveSoundEnabled by remember { mutableStateOf(true) }
    var moveDotsEnabled by remember { mutableStateOf(true) }
    var allowSwitchOpponent by remember { mutableStateOf(true) }
    var isDiagOpen by remember { mutableStateOf(false) }

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
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Settings",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.015).sp,
                color = ChessTutorColors.TextPrimary
            )
            Text(
                text = "Account, play preferences and board options",
                fontSize = 12.5.sp,
                color = ChessTutorColors.TextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
            )

            // GROUP: ACCOUNT
            Text(
                text = "ACCOUNT",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.06.sp,
                color = ChessTutorColors.TextTertiary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val isConnected = state.linkedProfile != null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ChessTutorColors.Surface2)
                    .padding(14.dp, 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ChessTutorColors.Surface3),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isConnected) "♜" else "♞",
                        fontSize = 17.sp,
                        color = ChessTutorColors.TextSecondary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isConnected) "${state.linkedProfile!!.platform.displayName} · ${state.linkedProfile.activeRating}" else "Not connected",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.01).sp,
                        color = ChessTutorColors.TextPrimary
                    )
                    Text(
                        text = if (isConnected) "Opponent strength now matches your rating" else "Link Chess.com or Lichess to match opponent strength to your rating",
                        fontSize = 12.sp,
                        color = ChessTutorColors.TextSecondary,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isConnected) ChessTutorColors.Surface3 else ChessTutorColors.Brass)
                        .border(
                            1.dp,
                            if (isConnected) ChessTutorColors.Sage else Color.Transparent,
                            RoundedCornerShape(9.dp)
                        )
                        .bouncyClickable {
                            if (!isConnected) {
                                viewModel.linkRatingAccount(com.chesstutor.app.data.model.RatingPlatform.CHESS_COM, "hikaru")
                            }
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isConnected) "Connected" else "Connect",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isConnected) ChessTutorColors.Sage else ChessTutorColors.BrassInk
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // GROUP: PLAY
            Text(
                text = "PLAY",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.06.sp,
                color = ChessTutorColors.TextTertiary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ChessTutorColors.Surface2)
                    .padding(12.dp, 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = ChessTutorColors.TextSecondary,
                    modifier = Modifier.size(19.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Switch opponent mid-game",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.008).sp,
                        color = ChessTutorColors.TextPrimary
                    )
                    Text(
                        text = "Allow changing strength without starting over",
                        fontSize = 12.sp,
                        color = ChessTutorColors.TextSecondary,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }

                Switch(
                    checked = allowSwitchOpponent,
                    onCheckedChange = { allowSwitchOpponent = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ChessTutorColors.BrassInk,
                        checkedTrackColor = ChessTutorColors.Brass,
                        uncheckedThumbColor = ChessTutorColors.TextSecondary,
                        uncheckedTrackColor = ChessTutorColors.Surface3
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // GROUP: BOARD & SOUND
            Text(
                text = "BOARD & SOUND",
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
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp, 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = ChessTutorColors.TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Move sound",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.008).sp,
                            color = ChessTutorColors.TextPrimary
                        )
                        Text(
                            text = "Play a sound on legal drops",
                            fontSize = 12.sp,
                            color = ChessTutorColors.TextSecondary,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }

                    Switch(
                        checked = moveSoundEnabled,
                        onCheckedChange = { moveSoundEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ChessTutorColors.BrassInk,
                            checkedTrackColor = ChessTutorColors.Brass,
                            uncheckedThumbColor = ChessTutorColors.TextSecondary,
                            uncheckedTrackColor = ChessTutorColors.Surface3
                        )
                    )
                }

                Spacer(modifier = Modifier.height(1.dp).background(ChessTutorColors.Line))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp, 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = ChessTutorColors.TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show legal move dots",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.008).sp,
                            color = ChessTutorColors.TextPrimary
                        )
                        Text(
                            text = "Highlight destinations when a piece is selected",
                            fontSize = 12.sp,
                            color = ChessTutorColors.TextSecondary,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }

                    Switch(
                        checked = moveDotsEnabled,
                        onCheckedChange = { moveDotsEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ChessTutorColors.BrassInk,
                            checkedTrackColor = ChessTutorColors.Brass,
                            uncheckedThumbColor = ChessTutorColors.TextSecondary,
                            uncheckedTrackColor = ChessTutorColors.Surface3
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // GROUP: ABOUT
            Text(
                text = "ABOUT",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.06.sp,
                color = ChessTutorColors.TextTertiary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ChessTutorColors.Surface2)
                    .bouncyClickable { isDiagOpen = !isDiagOpen }
                    .padding(12.dp, 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = ChessTutorColors.TextSecondary,
                    modifier = Modifier.size(19.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Developer & diagnostics",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.008).sp,
                        color = ChessTutorColors.TextPrimary
                    )
                    Text(
                        text = "Engine status, version 2.4.0",
                        fontSize = 12.sp,
                        color = ChessTutorColors.TextSecondary,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Details",
                    tint = ChessTutorColors.TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (isDiagOpen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChessTutorColors.Surface3)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Stockfish UCI: Online · Depth 12\nLocal SM-2 Scheduler: Active\nUI Version: Redesign Mockup 1:1",
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = ChessTutorColors.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
