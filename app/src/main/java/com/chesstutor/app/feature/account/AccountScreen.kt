package com.chesstutor.app.feature.account

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
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtBadge
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtComingSoon
import com.chesstutor.app.design.CtIconBubble
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtStatTile
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctEnter
import com.chesstutor.app.domain.LearnCurriculumRepository
import com.chesstutor.app.viewmodel.AppUiState

/**
 * Account tab (plan section 6.4). Phase 5 rebuilds games, performance and training here;
 * for now it links to the existing review screen and holds settings and engine health.
 */
@Composable
fun AccountScreen(
    state: AppUiState,
    dueReviewCount: Int,
    onOpenGames: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEngineHealth: () -> Unit,
    onOpenDesignGallery: () -> Unit,
    onOpenBoardLab: () -> Unit,
    onOpenDeveloperMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val linked = state.linkedProfile
    val lessonsDone = state.masteredModules.count { id -> LearnCurriculumRepository.topics.any { it.id == id } }
    val accuracy = if (state.tacticalAttempts > 0) {
        "${(state.tacticalCorrect * 100) / state.tacticalAttempts}%"
    } else {
        "-"
    }

    val engineBadge: (@Composable () -> Unit)? = if (state.engineStatus.isDegraded) {
        { CtBadge(text = "Lite engine", icon = Icons.Rounded.Warning) }
    } else {
        null
    }

    CtScreenBackground(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            CtTopBar(title = "Account")
            Column(
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
            ) {
                CtCard(modifier = Modifier.fillMaxWidth().ctEnter(0)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CtIconBubble(icon = Icons.Rounded.AccountCircle, tint = CtColors.Gold, size = 56.dp)
                        Spacer(Modifier.width(CtSpacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = linked?.let { "@${it.username}" } ?: "Your profile",
                                style = CtType.Title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = linked?.let { "${it.platform.displayName} account linked" }
                                    ?: "Link Chess.com or Lichess in Games and review",
                                style = CtType.BodySmall,
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md), modifier = Modifier.ctEnter(1)) {
                    CtStatTile(
                        value = "${state.effectiveUserRating}",
                        label = "Rating",
                        icon = Icons.Rounded.EmojiEvents,
                        accent = CtColors.Gold,
                        modifier = Modifier.weight(1f),
                    )
                    CtStatTile(
                        value = "$lessonsDone",
                        label = "Lessons done",
                        icon = Icons.Rounded.School,
                        accent = CtColors.Teal,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md), modifier = Modifier.ctEnter(2)) {
                    CtStatTile(
                        value = accuracy,
                        label = "Drill accuracy",
                        icon = Icons.Rounded.CheckCircle,
                        accent = CtColors.Lime,
                        modifier = Modifier.weight(1f),
                    )
                    CtStatTile(
                        value = "$dueReviewCount",
                        label = "Reviews due",
                        icon = Icons.Rounded.History,
                        accent = CtColors.Coral,
                        modifier = Modifier.weight(1f),
                    )
                }

                CtSectionHeader(title = "Your chess", modifier = Modifier.ctEnter(3))
                CtListRow(
                    title = "Games and review",
                    subtitle = "Link accounts, import games, review mistakes",
                    icon = Icons.Rounded.History,
                    onClick = onOpenGames,
                    modifier = Modifier.ctEnter(4),
                )
                CtCard(modifier = Modifier.fillMaxWidth().ctEnter(5)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CtIconBubble(icon = Icons.Rounded.Insights, tint = CtColors.Violet, size = 40.dp)
                        Spacer(Modifier.width(CtSpacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Performance and training", style = CtType.TitleSmall)
                            Text(text = "Results by opening, accuracy, strengths", style = CtType.Caption)
                        }
                        CtComingSoon(5)
                    }
                }

                CtSectionHeader(title = "App", modifier = Modifier.ctEnter(6))
                CtListRow(
                    title = "Settings",
                    subtitle = "Board, sound, haptics, motion",
                    icon = Icons.Rounded.Settings,
                    onClick = onOpenSettings,
                    modifier = Modifier.ctEnter(7),
                )
                CtListRow(
                    title = "Engine health",
                    subtitle = if (state.engineStatus.isDegraded) {
                        "Stockfish is not running: using a weaker engine"
                    } else {
                        "Check that Stockfish is running on this phone"
                    },
                    icon = Icons.Rounded.Memory,
                    iconTint = if (state.engineStatus.isDegraded) CtColors.Coral else CtColors.Lime,
                    onClick = onOpenEngineHealth,
                    modifier = Modifier.ctEnter(8),
                    trailing = engineBadge,
                )

                CtSectionHeader(title = "During development", modifier = Modifier.ctEnter(9))
                CtListRow(
                    title = "Design system",
                    subtitle = "Colours, type and components",
                    icon = Icons.Rounded.Palette,
                    iconTint = CtColors.Violet,
                    onClick = onOpenDesignGallery,
                    modifier = Modifier.ctEnter(10),
                )
                CtListRow(
                    title = "Board lab",
                    subtitle = "Try the new board: moves, sounds and motion",
                    icon = Icons.Rounded.GridView,
                    iconTint = CtColors.Gold,
                    onClick = onOpenBoardLab,
                    modifier = Modifier.ctEnter(11),
                )
                CtListRow(
                    title = "Classic screens",
                    subtitle = "Every screen from before the revamp",
                    icon = Icons.Rounded.Build,
                    iconTint = CtColors.Text2,
                    onClick = onOpenDeveloperMenu,
                    modifier = Modifier.ctEnter(12),
                )
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}
