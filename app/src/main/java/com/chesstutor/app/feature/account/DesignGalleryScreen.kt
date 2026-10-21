package com.chesstutor.app.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtBadge
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtCoachBubble
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtComingSoon
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtGradients
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtStatTile
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.Mascot

/** Every design-system piece on one page, to judge the look on a real phone (batch 0B). */
@Composable
fun DesignGalleryScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var progress by remember { mutableFloatStateOf(0.35f) }
    var selectedChip by remember { mutableIntStateOf(0) }

    CtScreenBackground(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            CtTopBar(title = "Design system", subtitle = "Version 2: tokens and components", onBack = onBack)
            Column(
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
            ) {
                CtSectionHeader(title = "Colours")
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    listOf(CtColors.Gold, CtColors.Teal, CtColors.Lime, CtColors.Violet, CtColors.Coral).forEach { Swatch(it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    listOf(CtColors.BgDeep, CtColors.Bg, CtColors.Surface, CtColors.SurfaceRaised, CtColors.SurfaceHigh).forEach { Swatch(it) }
                }

                CtSectionHeader(title = "Type")
                Text("Display 32", style = CtType.Display)
                Text("Headline 26", style = CtType.Headline)
                Text("Title 20", style = CtType.Title)
                Text("Title small 17", style = CtType.TitleSmall)
                Text("Body 16: the quick brown fox jumps over the lazy dog.", style = CtType.Body)
                Text("Body small 14: secondary information.", style = CtType.BodySmall)
                Text("Label 14", style = CtType.Label)
                Text("Caption 12", style = CtType.Caption)

                CtSectionHeader(title = "Buttons")
                CtButton(text = "Solve puzzles", icon = Icons.Rounded.PlayArrow, onClick = {}, modifier = Modifier.fillMaxWidth())
                CtButton(text = "Secondary", style = CtButtonStyle.Secondary, onClick = {}, modifier = Modifier.fillMaxWidth())
                CtButton(text = "Ghost", style = CtButtonStyle.Ghost, onClick = {}, modifier = Modifier.fillMaxWidth())
                CtButton(text = "Disabled", enabled = false, onClick = {}, modifier = Modifier.fillMaxWidth())

                CtSectionHeader(title = "Chips and badges")
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    listOf("All", "White", "Black").forEachIndexed { index, label ->
                        CtChip(text = label, selected = selectedChip == index, onClick = { selectedChip = index })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    CtBadge(text = "Lite engine", icon = Icons.Rounded.Warning)
                    CtBadge(text = "+12 XP", color = CtColors.Lime, contentColor = CtColors.OnLime)
                    CtComingSoon(2)
                }

                CtSectionHeader(title = "Stats and progress")
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                    CtStatTile("3,420", "XP", Icons.Rounded.Star, CtColors.Violet, Modifier.weight(1f))
                    CtStatTile("5", "Day streak", Icons.Rounded.LocalFireDepartment, CtColors.Gold, Modifier.weight(1f))
                }
                CtProgressBar(progress = progress, brush = CtGradients.Gold)
                CtButton(
                    text = "Fill progress",
                    style = CtButtonStyle.Secondary,
                    onClick = { progress = if (progress >= 1f) 0.1f else (progress + 0.25f).coerceAtMost(1f) },
                )

                CtSectionHeader(title = "Cards and rows")
                CtCard(background = CtGradients.Teal, modifier = Modifier.fillMaxWidth(), onClick = {}) {
                    Text("Teal card", style = CtType.Title)
                    Text("Learning and progress", style = CtType.BodySmall)
                }
                CtCard(background = CtGradients.Violet, modifier = Modifier.fillMaxWidth()) {
                    Text("Violet card", style = CtType.Title)
                    Text("XP, levels and rewards", style = CtType.BodySmall)
                }
                CtListRow(title = "List row", subtitle = "Opens a page", icon = Icons.Rounded.Extension, onClick = {})
                CtEmptyState(
                    icon = Icons.Rounded.EmojiEvents,
                    title = "No games yet",
                    body = "Play a bot or import your games to see them here.",
                    actionLabel = "Play a bot",
                    onAction = {},
                )

                CtSectionHeader(title = "Coaches")
                Mascot.entries.forEach { mascot ->
                    CtCoachBubble(mascot = mascot, text = mascot.tagline + ".", modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CtShapes.CardSmall)
            .background(color)
            .border(1.dp, CtColors.Line, CtShapes.CardSmall),
    )
}
