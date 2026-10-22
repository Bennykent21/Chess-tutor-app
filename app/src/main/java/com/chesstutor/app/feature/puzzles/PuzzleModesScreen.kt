package com.chesstutor.app.feature.puzzles

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtIconBubble
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtToggleRow
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.puzzle.Difficulty
import com.chesstutor.app.domain.puzzle.PuzzleGoal
import com.chesstutor.app.domain.puzzle.PuzzleThemes
import com.chesstutor.app.domain.puzzle.RatingBand

/** What the modes page can start. Each call opens the puzzle player. */
class PuzzleModeActions(
    val onRush: (minutes: Int?) -> Unit,
    val onDaily: () -> Unit,
    val onThemed: (PuzzleGoal, String?, Difficulty) -> Unit,
    val onCustom: (RatingBand, String?, count: Int, timed: Boolean) -> Unit,
    val onMistakes: () -> Unit,
)

/**
 * The puzzle modes (plan section 6.3.2): timed rush, the daily puzzle, goal-first themes
 * (plan section 6.3.6), a custom run and the review of puzzles you missed.
 */
@Composable
fun PuzzleModesScreen(
    patternsFor: (PuzzleGoal) -> List<String>,
    actions: PuzzleModeActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtTopBar(title = "Puzzle modes", subtitle = "Pick how you want to train", onBack = onBack)
            Column(
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
            ) {
                ModeCard(Icons.Rounded.Bolt, CtColors.Gold, "Timed rush", "Puzzles get harder as you go. A wrong move costs the puzzle.") {
                    Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                        CtButton("3 min", onClick = { actions.onRush(3) }, modifier = Modifier.weight(1f))
                        CtButton("5 min", onClick = { actions.onRush(5) }, style = CtButtonStyle.Secondary, modifier = Modifier.weight(1f))
                    }
                    CtButton("Survival: three strikes", onClick = { actions.onRush(null) }, style = CtButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
                }
                ModeCard(Icons.Rounded.CalendarToday, CtColors.Teal, "Daily puzzle", "The same puzzle for everyone today.") {
                    CtButton("Solve today's puzzle", onClick = actions.onDaily, modifier = Modifier.fillMaxWidth())
                }
                ThemeCard(patternsFor, actions.onThemed)
                CustomCard(patternsFor, actions.onCustom)
                ModeCard(Icons.Rounded.History, CtColors.Coral, "Mistakes review", "Puzzles you missed or needed help with, newest first.") {
                    CtButton("Review my mistakes", onClick = actions.onMistakes, style = CtButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}

@Composable
private fun ModeCard(icon: ImageVector, accent: Color, title: String, subtitle: String, content: @Composable () -> Unit) {
    CtCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CtIconBubble(icon = icon, tint = accent, size = 44.dp)
            Spacer(Modifier.width(CtSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = CtType.Title)
                Text(subtitle, style = CtType.BodySmall)
            }
        }
        Spacer(Modifier.height(CtSpacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) { content() }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) { content() }
}

@Composable
private fun ThemeCard(patternsFor: (PuzzleGoal) -> List<String>, onStart: (PuzzleGoal, String?, Difficulty) -> Unit) {
    var goal by remember { mutableStateOf(PuzzleGoal.WIN_MATERIAL) }
    var pattern by remember { mutableStateOf<String?>(null) }
    var difficulty by remember { mutableStateOf(Difficulty.MATCHED) }
    ModeCard(Icons.Rounded.Category, CtColors.Violet, "By theme", "Choose a goal first, then a pattern if you like.") {
        Text("Goal", style = CtType.Label)
        ChipRow {
            PuzzleGoal.entries.forEach { option ->
                CtChip(option.title, color = CtColors.Violet, selected = option == goal, onClick = { goal = option; pattern = null })
            }
        }
        Text("Pattern", style = CtType.Label)
        ChipRow {
            CtChip("Any", selected = pattern == null, onClick = { pattern = null })
            patternsFor(goal).take(12).forEach { tag ->
                CtChip(PuzzleThemes.displayName(tag), selected = tag == pattern, onClick = { pattern = tag })
            }
        }
        Text("Difficulty", style = CtType.Label)
        ChipRow {
            Difficulty.entries.forEach { option ->
                CtChip(option.label, color = CtColors.Gold, selected = option == difficulty, onClick = { difficulty = option })
            }
        }
        CtButton("Start 10 puzzles", onClick = { onStart(goal, pattern, difficulty) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CustomCard(patternsFor: (PuzzleGoal) -> List<String>, onStart: (RatingBand, String?, Int, Boolean) -> Unit) {
    var band by remember { mutableStateOf(RatingBand.CASUAL) }
    var pattern by remember { mutableStateOf<String?>(null) }
    var count by remember { mutableIntStateOf(10) }
    var timed by remember { mutableStateOf(false) }
    val allPatterns = PuzzleGoal.entries.flatMap(patternsFor).distinct().sortedBy(PuzzleThemes::order)
    ModeCard(Icons.Rounded.Tune, CtColors.Lime, "Custom", "Your rating range, pattern and number of puzzles.") {
        Text("Rating", style = CtType.Label)
        ChipRow {
            RatingBand.entries.forEach { option ->
                CtChip(option.label, color = CtColors.Lime, selected = option == band, onClick = { band = option })
            }
        }
        Text("Pattern", style = CtType.Label)
        ChipRow {
            CtChip("Any", selected = pattern == null, onClick = { pattern = null })
            allPatterns.forEach { tag -> CtChip(PuzzleThemes.displayName(tag), selected = tag == pattern, onClick = { pattern = tag }) }
        }
        Text("Puzzles", style = CtType.Label)
        ChipRow {
            listOf(5, 10, 20).forEach { n -> CtChip("$n", color = CtColors.Gold, selected = n == count, onClick = { count = n }) }
        }
        CtToggleRow("Timer", timed, { timed = it }, subtitle = "A minute a puzzle for the whole run")
        CtButton("Start", onClick = { onStart(band, pattern, count, timed) }, modifier = Modifier.fillMaxWidth())
    }
}
