package com.chesstutor.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtBadge
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtCoachBubble
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtComingSoon
import com.chesstutor.app.design.CtGradients
import com.chesstutor.app.design.CtIconBubble
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctEnter
import com.chesstutor.app.viewmodel.AppUiState

/**
 * Home: the play hub (plan section 6.1). In Phase 0 only "Quick match" works; the rest is
 * shown with the phase that builds it, so the layout can be judged on the phone now.
 */
@Composable
fun HomeScreen(
    state: AppUiState,
    onQuickMatch: () -> Unit,
    onContinueLearning: () -> Unit,
    onOpenEngineHealth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = state.linkedProfile?.username
    CtScreenBackground(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CtSpacing.gutter, vertical = CtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.ctEnter(0)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (name != null) "Hi, $name" else "Ready to play?",
                        style = CtType.Display,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(text = "Choose what to play today.", style = CtType.Body)
                }
                CtChip(text = "${state.effectiveUserRating}", color = CtColors.Gold, icon = Icons.Rounded.EmojiEvents)
            }

            CtCoachBubble(
                mascot = state.appSettings.mascot,
                text = "Fancy a game? I'll be cheering you on.",
                modifier = Modifier.fillMaxWidth().ctEnter(1),
            )

            if (state.engineStatus.isDegraded) {
                CtCard(
                    onClick = onOpenEngineHealth,
                    onClickLabel = "Open engine health",
                    modifier = Modifier.fillMaxWidth().ctEnter(1),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CtBadge(text = "Lite engine", icon = Icons.Rounded.Warning)
                        Spacer(Modifier.width(CtSpacing.sm))
                        Text(
                            text = "Stockfish is not running, so bots and analysis are weaker. Tap for details.",
                            style = CtType.BodySmall,
                        )
                    }
                }
            }

            // Continue card
            CtCard(
                background = CtGradients.Teal,
                onClick = onContinueLearning,
                onClickLabel = "Continue learning",
                modifier = Modifier.fillMaxWidth().ctEnter(2),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CtIconBubble(icon = Icons.Rounded.Lightbulb, tint = CtColors.Teal)
                    Spacer(Modifier.width(CtSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Continue", style = CtType.Caption.copy(color = CtColors.Teal))
                        Text(
                            text = state.trainingRecommendation.ifBlank { "Pick up your next lesson" },
                            style = CtType.Title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.trainingRecommendationReason.isNotBlank()) {
                            Text(
                                text = state.trainingRecommendationReason,
                                style = CtType.BodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            CtSectionHeader(title = "Play", modifier = Modifier.ctEnter(3))
            CtCard(background = CtGradients.Card, modifier = Modifier.fillMaxWidth().ctEnter(4)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CtIconBubble(icon = Icons.Rounded.SportsEsports, tint = CtColors.Gold)
                    Spacer(Modifier.width(CtSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Quick match", style = CtType.Title)
                        Text(
                            text = "Play ${state.arenaBotName} at about ${state.effectiveBotElo}.",
                            style = CtType.BodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(CtSpacing.md))
                CtButton(
                    text = "Play",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = onQuickMatch,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md), modifier = Modifier.ctEnter(5)) {
                PlannedTile("Custom bot", "Build your own opponent", Icons.Rounded.Tune, 3, Modifier.weight(1f))
                PlannedTile("Opening battle", "Play or drill one opening", Icons.Rounded.Flag, 3, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md), modifier = Modifier.ctEnter(6)) {
                PlannedTile("Daily challenge", "A new position every day", Icons.Rounded.Bolt, 3, Modifier.weight(1f))
                PlannedTile("From a position", "Set up any position", Icons.Rounded.SportsEsports, 3, Modifier.weight(1f))
            }
            Spacer(Modifier.height(CtSpacing.lg))
        }
    }
}

@Composable
private fun PlannedTile(title: String, subtitle: String, icon: ImageVector, phase: Int, modifier: Modifier) {
    CtCard(modifier = modifier) {
        CtIconBubble(icon = icon, tint = CtColors.Violet, size = 36.dp)
        Spacer(Modifier.height(CtSpacing.sm))
        Text(text = title, style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = subtitle, style = CtType.Caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(CtSpacing.sm))
        CtComingSoon(phase)
    }
}

