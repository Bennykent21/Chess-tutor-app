package com.chesstutor.app.feature.puzzles

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
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtCoachBubble
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtComingSoon
import com.chesstutor.app.design.CtGradients
import com.chesstutor.app.design.CtIconBubble
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctEnter
import com.chesstutor.app.domain.Mascot

/**
 * Puzzles tab (plan section 6.3). Phase 2 replaces this with the level path; until then
 * the existing trainer is one tap away and the planned modes are listed.
 */
@Composable
fun PuzzlesHomeScreen(
    mascot: Mascot,
    dueReviewCount: Int,
    onSolvePuzzles: () -> Unit,
    onReviewMistakes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CtScreenBackground(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            CtTopBar(title = "Puzzles", subtitle = "Sharpen your tactics every day")
            Column(
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
            ) {
                CtCoachBubble(
                    mascot = mascot,
                    text = "Ready to sharpen your tactics?",
                    modifier = Modifier.fillMaxWidth().ctEnter(0),
                )
                CtCard(background = CtGradients.Violet, modifier = Modifier.fillMaxWidth().ctEnter(0)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CtIconBubble(icon = Icons.Rounded.Extension, tint = CtColors.Gold, size = 56.dp)
                        Spacer(Modifier.width(CtSpacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Puzzle trainer", style = CtType.Headline)
                            Text(
                                text = "Rated puzzles that match your level.",
                                style = CtType.BodySmall.copy(color = CtColors.Text),
                            )
                        }
                    }
                    Spacer(Modifier.height(CtSpacing.lg))
                    CtButton(text = "Solve puzzles", onClick = onSolvePuzzles, modifier = Modifier.fillMaxWidth())
                }

                CtListRow(
                    title = "Mistakes review",
                    subtitle = if (dueReviewCount > 0) {
                        "$dueReviewCount position${if (dueReviewCount == 1) "" else "s"} from your games are due"
                    } else {
                        "Positions from your own games, spaced out over time"
                    },
                    icon = Icons.Rounded.History,
                    iconTint = CtColors.Coral,
                    onClick = onReviewMistakes,
                    modifier = Modifier.ctEnter(1),
                )

                CtSectionHeader(title = "Coming next", modifier = Modifier.ctEnter(2))
                PlannedMode("Level path", "Worlds of levels that get harder as you go", Icons.Rounded.Map, 2, 3)
                PlannedMode("Timed rush", "3 or 5 minutes, or three strikes", Icons.Rounded.Timer, 2, 4)
                PlannedMode("Daily puzzle", "The same puzzle for everyone each day", Icons.Rounded.CalendarToday, 2, 5)
                PlannedMode("By theme and custom", "Pick a goal, a pattern and a level", Icons.Rounded.Tune, 2, 6)
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}

@Composable
private fun PlannedMode(title: String, subtitle: String, icon: ImageVector, phase: Int, index: Int) {
    CtCard(modifier = Modifier.fillMaxWidth().ctEnter(index)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CtIconBubble(icon = icon, tint = CtColors.Violet, size = 40.dp)
            Spacer(Modifier.width(CtSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = subtitle, style = CtType.Caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(CtSpacing.sm))
            CtComingSoon(phase)
        }
    }
}
