package com.chesstutor.app.feature.puzzles

import androidx.compose.ui.Alignment
import com.chesstutor.app.domain.puzzle.GoalStats
import com.chesstutor.app.design.CtToggleRow
import com.chesstutor.app.design.CtGroupLabel
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.chesstutor.app.design.CtFullPage
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
    /** A goal's run: pattern (null for any), difficulty, how many, and whether a clock runs (D95). */
    val onThemed: (PuzzleGoal, String?, Difficulty, Int, Boolean) -> Unit,
    /** Opens Custom run, a screen of its own (D64). */
    val onOpenCustom: () -> Unit,
    val onMistakes: () -> Unit,
)

/**
 * The puzzle modes (plan section 6.3.2, D67): three rush tiles, each with its metal icon and best
 * score; the four goals (plan section 6.3.6) as tiles showing a real position of their idea, each
 * opening a page for pattern and difficulty; Custom run (its own screen) and the puzzles the
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
    goalStats: Map<PuzzleGoal, GoalStats> = emptyMap(),
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
                    // One tile for every mode (D95): its picture or metal object on top, the name under it.
                    Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                        CtGroupLabel("Rush")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            RUSHES.forEach { rush ->
                                ModeTile(
                                    title = rush.name,
                                    subtitle = rushBest[rush.kind]?.let { "Best $it" } ?: rush.fresh,
                                    spoken = rush.spoken + (rushBest[rush.kind]?.let { ", best $it" } ?: ""),
                                    modifier = Modifier.weight(1f),
                                    onClick = { actions.onRush(rush.kind.minutes) },
                                ) { MetalArt(rush.icon) }
                            }
                        }
                        CtGroupLabel("Themes")
                        PuzzleGoal.entries.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { goal ->
                                    ModeTile(
                                        title = goal.title,
                                        subtitle = goalStats[goal]?.firstTryPercent?.let { "$it% first try" } ?: "Choose a pattern",
                                        spoken = goal.title,
                                        modifier = Modifier.weight(1f),
                                        onClick = { goalName = goal.name },
                                    ) { BoardPicture(goalExample(goal), boardTheme, zoom = 1f) }
                                }
                            }
                        }
                        CtGroupLabel("Your own")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ModeTile(title = "Custom run", subtitle = "Rating, patterns, clock", spoken = "Custom run", modifier = Modifier.weight(1f), onClick = actions.onOpenCustom) {
                                MetalArt(R.drawable.icon_tune)
                            }
                            ModeTile(
                                title = "Missed",
                                subtitle = if (missed > 0) "$missed to try again" else "None waiting",
                                spoken = "Puzzles you missed, $missed",
                                modifier = Modifier.weight(1f),
                                onClick = actions.onMistakes,
                            ) { MetalArt(R.drawable.icon_replay) }
                        }
                    }
                }
            }
            val goal = goalName?.let(PuzzleGoal::valueOf)
            GoalPage(goal, patternsFor, goal?.let { goalStats[it] }, onDismiss = { goalName = null }) { g, pattern, difficulty, count, timed ->
                goalName = null
                actions.onThemed(g, pattern, difficulty, count, timed)
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

/** The one tile of Puzzle modes (D95): a picture over a soft glow, then the name and a short line. */
@Composable
private fun ModeTile(title: String, subtitle: String, spoken: String, modifier: Modifier, onClick: () -> Unit, art: @Composable BoxScope.() -> Unit) {
    Column(
        modifier = modifier
            .clip(CtShapes.Card)
            .background(CtColors.Surface)
            .ctPressable(onClickLabel = "Open", onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = "Open") { onClick(); true }
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.4f)
                .clip(CtShapes.Card)
                .background(Brush.radialGradient(listOf(CtColors.Accent.copy(alpha = 0.22f), Color.Transparent))),
            contentAlignment = Alignment.Center,
            content = art,
        )
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title, style = CtType.TitleSmall, maxLines = 1)
            Text(subtitle, style = CtType.Caption.copy(color = CtColors.Text2), maxLines = 1)
        }
    }
}

/** A metal object in the middle of a tile's picture. */
@Composable
private fun MetalArt(@DrawableRes icon: Int) {
    Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(52.dp))
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

internal fun goalPiece(goal: PuzzleGoal): PieceType = when (goal) {
    PuzzleGoal.CHECKMATE -> PieceType.KING
    PuzzleGoal.WIN_MATERIAL -> PieceType.QUEEN
    PuzzleGoal.DEFEND -> PieceType.ROOK
    PuzzleGoal.ENDGAME -> PieceType.PAWN
}

/**
 * A goal's own page (D95): how the player does at it, then pattern, difficulty, how many and
 * whether a clock runs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalPage(
    goal: PuzzleGoal?,
    patternsFor: (PuzzleGoal) -> List<String>,
    stats: GoalStats?,
    onDismiss: () -> Unit,
    onStart: (PuzzleGoal, String?, Difficulty, Int, Boolean) -> Unit,
) {
    // Keep the last goal on the page while it slides away.
    var shown by remember { mutableStateOf(goal) }
    if (goal != null && goal != shown) shown = goal
    var pattern by remember(shown) { mutableStateOf<String?>(null) }
    var difficulty by remember(shown) { mutableStateOf(Difficulty.MATCHED) }
    var count by remember(shown) { mutableIntStateOf(10) }
    var timed by remember(shown) { mutableStateOf(false) }
    val g = shown ?: return
    CtFullPage(
        visible = goal != null,
        title = g.title,
        onBack = onDismiss,
        bottom = { CtButton("Start $count puzzles", onClick = { onStart(g, pattern, difficulty, count, timed) }, modifier = Modifier.weight(1f)) },
    ) {
        if (stats != null && stats.played > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("${stats.played}", "Played", Modifier.weight(1f))
                StatBox("${stats.firstTryPercent}%", "First try", Modifier.weight(1f))
            }
        }
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
        Group("How many") {
            CtSegmented(options = RUN_SIZES, selected = count, label = { "$it" }, onSelect = { count = it })
        }
        CtToggleRow(title = "Against the clock", checked = timed, onCheckedChange = { timed = it })
    }
}

/** One number and what it counts. */
@Composable
private fun StatBox(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(CtShapes.Control).background(CtColors.Surface).padding(vertical = CtSpacing.md).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = CtType.Title)
        Text(label, style = CtType.Caption.copy(color = CtColors.Text2))
    }
}

/** How many puzzles a goal's run can have. */
private val RUN_SIZES = listOf(5, 10, 20)

private const val MAX_PATTERNS = 12

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        Text(title, style = CtType.Label.copy(color = CtColors.Text2))
        content()
    }
}
