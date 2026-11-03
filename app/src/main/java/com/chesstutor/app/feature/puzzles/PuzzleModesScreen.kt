package com.chesstutor.app.feature.puzzles

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.R
import com.chesstutor.app.board.BoardPicture
import com.chesstutor.app.board.BoardTheme
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
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.puzzle.Difficulty
import com.chesstutor.app.domain.puzzle.PuzzleGoal
import com.chesstutor.app.domain.puzzle.PuzzleThemes
import com.chesstutor.app.domain.puzzle.RushKind

/** What the modes page can start. Each call opens the puzzle player. */
class PuzzleModeActions(
    val onRush: (minutes: Int?) -> Unit,
    val onThemed: (PuzzleGoal, String?, Difficulty) -> Unit,
    /** Opens Custom run, a screen of its own (D64). */
    val onOpenCustom: () -> Unit,
    val onMistakes: () -> Unit,
)

/**
 * The puzzle modes (plan section 6.3.2, D67): three rush tiles, each with its metal icon and best
 * score; the four goals (plan section 6.3.6) as tiles showing a real position of their idea, each
 * opening a sheet for pattern and difficulty; Custom run (its own screen) and the puzzles the
 * player missed. The daily puzzle has its tile on Puzzles home.
 */
@Composable
fun PuzzleModesScreen(
    patternsFor: (PuzzleGoal) -> List<String>,
    actions: PuzzleModeActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    boardTheme: BoardTheme = BoardTheme(),
    rushBest: Map<RushKind, Int> = emptyMap(),
    missed: Int = 0,
) {
    var goalName by rememberSaveable { mutableStateOf<String?>(null) }
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
                        RUSHES.forEach { rush ->
                            RushTile(rush, rushBest[rush.kind], Modifier.weight(1f), onStart = { actions.onRush(rush.kind.minutes) })
                        }
                    }

                    CtSectionHeader(title = "Themes", modifier = Modifier.padding(bottom = CtSpacing.sm))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PuzzleGoal.entries.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { goal -> GoalTile(goal, boardTheme, Modifier.weight(1f), onClick = { goalName = goal.name }) }
                            }
                        }
                    }

                    CtSectionHeader(title = "More")
                    CtListRow(title = "Custom run", icon = Icons.Rounded.Tune, onClick = actions.onOpenCustom)
                    CtListRow(
                        title = "Puzzles you missed",
                        icon = Icons.Rounded.History,
                        onClick = actions.onMistakes,
                        trailing = if (missed > 0) { { Text("$missed", style = CtType.Label.copy(color = CtColors.Text2)) } } else null,
                    )
                }
            }
            val goal = goalName?.let(PuzzleGoal::valueOf)
            GoalSheet(goal, patternsFor, onDismiss = { goalName = null }) { g, pattern, difficulty ->
                goalName = null
                actions.onThemed(g, pattern, difficulty)
            }
        }
    }
}

/** A rush tile's look: its metal icon, its name, and what is under the name before a best exists. */
private class Rush(val kind: RushKind, @param:DrawableRes val icon: Int, val name: String, val spoken: String, val fresh: String)

private val RUSHES = listOf(
    Rush(RushKind.THREE, R.drawable.icon_bolt, "3 min", "3-minute rush", "Rush"),
    Rush(RushKind.FIVE, R.drawable.icon_clock_rapid, "5 min", "5-minute rush", "Rush"),
    Rush(RushKind.SURVIVAL, R.drawable.icon_favorite, "Survival", "Survival, three strikes", "3 lives"),
)

/** The metal icon, then the name and best score just under it; one tap starts the rush. */
@Composable
private fun RushTile(rush: Rush, best: Int?, modifier: Modifier, onStart: () -> Unit) {
    Column(
        modifier = modifier
            .clip(CtShapes.Card)
            .background(CtColors.Surface)
            .ctPressable(onClickLabel = "Start", onClick = onStart)
            .clearAndSetSemantics {
                contentDescription = rush.spoken + (best?.let { ", best $it" } ?: "")
                role = Role.Button
                onClick(label = "Start") { onStart(); true }
            }
            .padding(horizontal = 14.dp, vertical = CtSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CtSpacing.sm),
    ) {
        Image(painterResource(rush.icon), contentDescription = null, modifier = Modifier.size(40.dp))
        Column {
            Text(rush.name, style = CtType.TitleSmall, maxLines = 1)
            Text(best?.let { "Best $it" } ?: rush.fresh, style = CtType.Caption, maxLines = 1)
        }
    }
}

/** A real position of each goal's idea, for its tile. */
internal fun goalExample(goal: PuzzleGoal): String = when (goal) {
    // Rd8 mates on the back rank.
    PuzzleGoal.CHECKMATE -> "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1"
    // Nc7+ forks the king and the rook.
    PuzzleGoal.WIN_MATERIAL -> "r3k3/8/8/3N4/8/8/8/4K3 w - - 0 1"
    // Re1 is mate unless White makes room for the king.
    PuzzleGoal.DEFEND -> "4r1k1/8/8/8/8/8/5PPP/6K1 w - - 0 1"
    // King and pawn against king.
    PuzzleGoal.ENDGAME -> "8/8/8/4k3/8/4K3/4P3/8 w - - 0 1"
}

/** The goal's example on the player's own board, its name under it. */
@Composable
private fun GoalTile(goal: PuzzleGoal, boardTheme: BoardTheme, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(CtShapes.Card)
            .background(CtColors.Surface)
            .ctPressable(onClickLabel = "Choose a pattern", onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) { BoardPicture(goalExample(goal), boardTheme, zoom = 1f) }
        Text(goal.title, style = CtType.TitleSmall, maxLines = 1, modifier = Modifier.padding(horizontal = 14.dp, vertical = CtSpacing.md))
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

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        Text(title, style = CtType.Label.copy(color = CtColors.Text2))
        content()
    }
}
