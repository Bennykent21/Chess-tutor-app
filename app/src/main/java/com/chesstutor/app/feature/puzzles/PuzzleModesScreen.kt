package com.chesstutor.app.feature.puzzles

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.PieceArt
import com.chesstutor.app.board.PieceSet
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSegmented
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSheet
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtToggleRow
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.puzzle.Difficulty
import com.chesstutor.app.domain.puzzle.PuzzleGoal
import com.chesstutor.app.domain.puzzle.PuzzleThemes
import com.chesstutor.app.domain.puzzle.RatingBand

/** What the modes page can start. Each call opens the puzzle player. */
class PuzzleModeActions(
    val onRush: (minutes: Int?) -> Unit,
    val onThemed: (PuzzleGoal, String?, Difficulty) -> Unit,
    val onCustom: (RatingBand, String?, count: Int, timed: Boolean) -> Unit,
    val onMistakes: () -> Unit,
)

/**
 * The puzzle modes (plan section 6.3.2, mock-up round 13): three rush tiles, the four goals
 * (plan section 6.3.6) that each open a sheet for pattern and difficulty, a custom run in a
 * sheet, and the puzzles the player missed. The daily puzzle has its tile on Puzzles home.
 */
@Composable
fun PuzzleModesScreen(
    patternsFor: (PuzzleGoal) -> List<String>,
    actions: PuzzleModeActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pieceSet: PieceSet = PieceSet.DEFAULT,
) {
    var goalName by rememberSaveable { mutableStateOf<String?>(null) }
    var customOpen by rememberSaveable { mutableStateOf(false) }
    CtScreenBackground(modifier) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                CtTopBar(title = "Puzzle modes", onBack = onBack)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = CtSpacing.gutter, end = CtSpacing.gutter, bottom = CtSpacing.xl),
                ) {
                    CtSectionHeader(title = "Rush", modifier = Modifier.padding(bottom = CtSpacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RushTile("3-minute rush", "minutes", Modifier.weight(1f), onStart = { actions.onRush(3) }) { Text("3", style = CtType.Display) }
                        RushTile("5-minute rush", "minutes", Modifier.weight(1f), onStart = { actions.onRush(5) }) { Text("5", style = CtType.Display) }
                        RushTile("Survival, three strikes", "Survival", Modifier.weight(1f), onStart = { actions.onRush(null) }) { Strikes() }
                    }

                    CtSectionHeader(title = "Themes")
                    PuzzleGoal.entries.forEachIndexed { i, goal ->
                        CtListRow(
                            title = goal.title,
                            icon = null,
                            onClick = { goalName = goal.name },
                            divider = i < PuzzleGoal.entries.lastIndex,
                            leading = { GoalPiece(goal, pieceSet) },
                        )
                    }

                    CtSectionHeader(title = "More")
                    CtListRow(title = "Custom run", icon = Icons.Rounded.Tune, onClick = { customOpen = true })
                    CtListRow(title = "Puzzles you missed", icon = Icons.Rounded.History, onClick = actions.onMistakes, divider = false)
                }
            }
            val goal = goalName?.let(PuzzleGoal::valueOf)
            GoalSheet(goal, patternsFor, onDismiss = { goalName = null }) { g, pattern, difficulty ->
                goalName = null
                actions.onThemed(g, pattern, difficulty)
            }
            CustomSheet(customOpen, patternsFor, onDismiss = { customOpen = false }) { band, pattern, count, timed ->
                customOpen = false
                actions.onCustom(band, pattern, count, timed)
            }
        }
    }
}

/** A big number (or the three strikes) above a short label; one tap starts the rush. */
@Composable
private fun RushTile(spoken: String, label: String, modifier: Modifier, onStart: () -> Unit, top: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .height(96.dp)
            .clip(CtShapes.Card)
            .background(CtColors.Surface)
            .ctPressable(onClickLabel = "Start", onClick = onStart)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = "Start") { onStart(); true }
            }
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = CtSpacing.md),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.height(36.dp), contentAlignment = Alignment.CenterStart) { top() }
        Text(label, style = CtType.Caption)
    }
}

/** Three red dots: three wrong moves end a survival run. */
@Composable
private fun Strikes() {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { Box(Modifier.size(10.dp).clip(CtShapes.Pill).background(CtColors.Error)) }
    }
}

/** The goal's piece in the player's own set, as on the Learn hub. */
@Composable
private fun GoalPiece(goal: PuzzleGoal, pieceSet: PieceSet) {
    Box(Modifier.size(40.dp).clip(CtShapes.Pill).background(CtColors.Surface), contentAlignment = Alignment.Center) {
        Image(painterResource(PieceArt.drawable(pieceSet, Piece(goalPiece(goal), PieceColor.WHITE))), contentDescription = null, modifier = Modifier.size(32.dp))
    }
}

internal fun goalPiece(goal: PuzzleGoal): PieceType = when (goal) {
    PuzzleGoal.CHECKMATE -> PieceType.KING
    PuzzleGoal.WIN_MATERIAL -> PieceType.QUEEN
    PuzzleGoal.DEFEND -> PieceType.ROOK
    PuzzleGoal.ENDGAME -> PieceType.PAWN
}

/** A goal's pattern and difficulty, then ten puzzles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalSheet(
    goal: PuzzleGoal?,
    patternsFor: (PuzzleGoal) -> List<String>,
    onDismiss: () -> Unit,
    onStart: (PuzzleGoal, String?, Difficulty) -> Unit,
) {
    // Keep the last goal on screen while the sheet slides away.
    var shown by remember { mutableStateOf(goal) }
    if (goal != null && goal != shown) shown = goal
    var pattern by remember(shown) { mutableStateOf<String?>(null) }
    var difficulty by remember(shown) { mutableStateOf(Difficulty.MATCHED) }
    CtSheet(visible = goal != null, onDismiss = onDismiss) {
        val g = shown ?: return@CtSheet
        Text(g.title, style = CtType.Headline)
        Group("Pattern") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                CtChip("Any", selected = pattern == null, onClick = { pattern = null })
                patternsFor(g).take(MAX_PATTERNS).forEach { tag ->
                    CtChip(PuzzleThemes.displayName(tag), selected = tag == pattern, onClick = { pattern = tag })
                }
            }
        }
        Group("Difficulty") {
            CtSegmented(options = Difficulty.entries, selected = difficulty, label = { it.label }, onSelect = { difficulty = it })
        }
        CtButton("Start 10 puzzles", onClick = { onStart(g, pattern, difficulty) }, modifier = Modifier.fillMaxWidth())
    }
}

private const val MAX_PATTERNS = 12
private val COUNTS = listOf(5, 10, 20)

/** A rating range, a pattern, how many puzzles and an optional timer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomSheet(
    visible: Boolean,
    patternsFor: (PuzzleGoal) -> List<String>,
    onDismiss: () -> Unit,
    onStart: (RatingBand, String?, Int, Boolean) -> Unit,
) {
    var band by remember { mutableStateOf(RatingBand.CASUAL) }
    var pattern by remember { mutableStateOf<String?>(null) }
    var count by remember { mutableIntStateOf(10) }
    var timed by remember { mutableStateOf(false) }
    CtSheet(visible = visible, onDismiss = onDismiss) {
        val allPatterns = remember(visible) { PuzzleGoal.entries.flatMap(patternsFor).distinct().sortedBy(PuzzleThemes::order) }
        Text("Custom run", style = CtType.Headline)
        Group("Rating") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                RatingBand.entries.forEach { option -> CtChip(option.label, selected = option == band, onClick = { band = option }) }
            }
        }
        Group("Pattern") {
            ChipRow {
                CtChip("Any", selected = pattern == null, onClick = { pattern = null })
                allPatterns.forEach { tag -> CtChip(PuzzleThemes.displayName(tag), selected = tag == pattern, onClick = { pattern = tag }) }
            }
        }
        Group("Puzzles") {
            CtSegmented(options = COUNTS, selected = count, label = { "$it" }, onSelect = { count = it })
        }
        CtToggleRow("Timer", timed, { timed = it }, divider = false)
        CtButton("Start $count puzzles", onClick = { onStart(band, pattern, count, timed) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        Text(title, style = CtType.Label.copy(color = CtColors.Text2))
        content()
    }
}

/** Chips that scroll sideways from one edge of the sheet to the other, past its margins. */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .layout { measurable, constraints ->
                val bleed = CtSpacing.gutter.roundToPx()
                val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + 2 * bleed, minWidth = 0))
                layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
            }
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = CtSpacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
    ) { content() }
}
