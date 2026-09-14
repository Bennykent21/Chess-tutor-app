package com.chesstutor.app.ui.arena

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.chesstutor.app.ui.components.AcademyCard
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.ChessTutorTypography
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel

@Composable
fun RatingLinkCard(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    var selectedPlatform by remember { mutableStateOf(RatingPlatform.CHESS_COM) }
    var inputUsername by remember { mutableStateOf("") }
    var selectedTimeControl by remember { mutableStateOf(RatingTimeControl.RAPID) }

    AcademyCard(
        sectionLabel = "Bot Rating & Tuning",
        modifier = modifier
    ) {
        // Active Bot Tuning Description Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ChessTutorColors.SurfaceElevated)
                .border(1.dp, ChessTutorColors.Border, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = ChessTutorColors.Primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = state.botTuningDescription,
                        style = ChessTutorTypography.titleSmall,
                        color = ChessTutorColors.Primary
                    )
                }
                Text(
                    text = "Stockfish strength is tuned using UCI_Elo / Skill Level. UCI_Elo is calibrated against the CCRL 40/4 scale, which approximates but diverges from server rating pools.",
                    style = ChessTutorTypography.bodySmall,
                    color = ChessTutorColors.TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Linked Profile State vs Link Form
        val profile = state.linkedProfile
        if (profile != null) {
            // Already Linked View
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Linked Profile",
                            style = ChessTutorTypography.labelSmall,
                            color = ChessTutorColors.TextSecondary
                        )
                        Text(
                            text = "${profile.platform.displayName}: @${profile.username}",
                            style = ChessTutorTypography.titleMedium,
                            color = ChessTutorColors.TextPrimary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (state.useLinkedRatingForBot) "Linked" else "Preset",
                            style = ChessTutorTypography.bodySmall,
                            color = ChessTutorColors.TextSecondary,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Switch(
                            checked = state.useLinkedRatingForBot,
                            onCheckedChange = { viewModel.setUseLinkedRatingForBot(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ChessTutorColors.Background,
                                checkedTrackColor = ChessTutorColors.Primary
                            )
                        )
                    }
                }

                Text(
                    text = "SELECT FORMAT TO TUNE BOT",
                    style = ChessTutorTypography.labelSmall,
                    color = ChessTutorColors.TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RatingTimeControl.values().forEach { tc ->
                        val ratingValue = when (tc) {
                            RatingTimeControl.RAPID -> profile.rapidRating
                            RatingTimeControl.BLITZ -> profile.blitzRating
                            RatingTimeControl.BULLET -> profile.bulletRating
                        }
                        val isSelected = profile.selectedTimeControl == tc
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.setRatingTimeControl(tc)
                                viewModel.setUseLinkedRatingForBot(true)
                            },
                            label = {
                                Text("${tc.displayName}: ${ratingValue ?: "—"}")
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ChessTutorColors.Primary,
                                selectedLabelColor = ChessTutorColors.Background,
                                containerColor = ChessTutorColors.SurfaceElevated,
                                labelColor = ChessTutorColors.TextPrimary
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.refreshLinkedRating() },
                        enabled = !state.isLinkingLoading,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (state.isLinkingLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = ChessTutorColors.Primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Refresh Rating")
                    }

                    OutlinedButton(
                        onClick = { viewModel.unlinkRatingAccount() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ChessTutorColors.Blunder)
                    ) {
                        Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Unlink")
                    }
                }
            }
        } else {
            // Not Linked Form
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Link your public Chess.com or Lichess profile to automatically tune Stockfish against your rating. Read-only lookup — no password or login required.",
                    style = ChessTutorTypography.bodySmall,
                    color = ChessTutorColors.TextSecondary
                )

                // Platform selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RatingPlatform.values().forEach { platform ->
                        FilterChip(
                            selected = selectedPlatform == platform,
                            onClick = { selectedPlatform = platform },
                            label = { Text(platform.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ChessTutorColors.Primary,
                                selectedLabelColor = ChessTutorColors.Background,
                                containerColor = ChessTutorColors.SurfaceElevated,
                                labelColor = ChessTutorColors.TextPrimary
                            )
                        )
                    }
                }

                // Username input
                OutlinedTextField(
                    value = inputUsername,
                    onValueChange = { inputUsername = it },
                    label = { Text("Public Username") },
                    placeholder = { Text("e.g. magnuscarlsen or erik") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ChessTutorColors.Primary,
                        unfocusedBorderColor = ChessTutorColors.Border,
                        focusedTextColor = ChessTutorColors.TextPrimary,
                        unfocusedTextColor = ChessTutorColors.TextPrimary
                    )
                )

                // Time Control Choice
                Text(
                    text = "Preferred Format",
                    style = ChessTutorTypography.labelSmall,
                    color = ChessTutorColors.TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RatingTimeControl.values().forEach { tc ->
                        FilterChip(
                            selected = selectedTimeControl == tc,
                            onClick = { selectedTimeControl = tc },
                            label = { Text(tc.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ChessTutorColors.Primary,
                                selectedLabelColor = ChessTutorColors.Background,
                                containerColor = ChessTutorColors.SurfaceElevated,
                                labelColor = ChessTutorColors.TextPrimary
                            )
                        )
                    }
                }

                Button(
                    onClick = {
                        viewModel.linkRatingAccount(selectedPlatform, inputUsername, selectedTimeControl)
                    },
                    enabled = inputUsername.isNotBlank() && !state.isLinkingLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChessTutorColors.Primary,
                        contentColor = ChessTutorColors.Background
                    )
                ) {
                    if (state.isLinkingLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = ChessTutorColors.Background
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(if (state.isLinkingLoading) "Fetching Stats..." else "Link ${selectedPlatform.displayName} Rating")
                }
            }
        }

        // Error message
        AnimatedVisibility(visible = state.linkingError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x22EF4444))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = ChessTutorColors.Blunder, modifier = Modifier.size(16.dp))
                Text(
                    text = state.linkingError ?: "",
                    style = ChessTutorTypography.bodySmall,
                    color = ChessTutorColors.Blunder
                )
            }
        }

        // Success message
        AnimatedVisibility(visible = state.linkingSuccessMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x2210B981))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                Text(
                    text = state.linkingSuccessMessage ?: "",
                    style = ChessTutorTypography.bodySmall,
                    color = Color(0xFF10B981)
                )
            }
        }
    }
}
