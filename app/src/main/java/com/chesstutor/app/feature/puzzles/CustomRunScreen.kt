package com.chesstutor.app.feature.puzzles

import kotlin.math.roundToInt
import androidx.compose.ui.semantics.contentDescription
import com.chesstutor.app.design.CtToggleRow
import com.chesstutor.app.design.CtGroupLabel
import com.chesstutor.app.design.CtActionPage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.PieceArt
import com.chesstutor.app.board.PieceSet
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSegmented
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.puzzle.PuzzleGoal
import com.chesstutor.app.domain.puzzle.PuzzleThemes
import com.chesstutor.app.domain.puzzle.RatingBand

private val COUNTS = listOf(5, 10, 20)

/**
 * Every pattern once, under the goal it belongs to most: the one where it ranks highest among
 * that goal's patterns (a fork turns up in mates too, but it is mostly a way to win material).
 * Within a goal, mates come first, then tactics, then endgames.
 */
internal fun patternGroups(patternsFor: (PuzzleGoal) -> List<String>): List<Pair<PuzzleGoal, List<String>>> {
    val ranked = PuzzleGoal.entries.associateWith(patternsFor)
    val home = ranked.flatMap { (goal, tags) -> tags.mapIndexed { rank, tag -> Triple(tag, goal, rank) } }
        .groupBy { it.first }
        .mapValues { (_, places) -> places.minBy { it.third }.second }
    return PuzzleGoal.entries
        .map { goal -> goal to ranked.getValue(goal).filter { home[it] == goal }.sortedBy(PuzzleThemes::order) }
        .filter { it.second.isNotEmpty() }
}

/**
 * Custom run (D64, D95): a rating range dragged from both ends, every pattern grouped under its
 * goal, how many and whether a clock runs, with Start fixed at the bottom; the same parts as a
 * goal's page.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CustomRunScreen(
    patternsFor: (PuzzleGoal) -> List<String>,
    puzzleRating: Int,
    onStart: (ratings: IntRange, pattern: String?, count: Int, timed: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") pieceSet: PieceSet = PieceSet.DEFAULT,
) {
    val near = (puzzleRating / STEP) * STEP
    var low by rememberSaveable { mutableIntStateOf((near - 200).coerceIn(MIN, MAX - STEP)) }
    var high by rememberSaveable { mutableIntStateOf((near + 200).coerceIn(MIN + STEP, MAX)) }
    var pattern by rememberSaveable { mutableStateOf<String?>(null) }
    var count by rememberSaveable { mutableIntStateOf(10) }
    var timed by rememberSaveable { mutableStateOf(false) }
    // Not remembered: the patterns arrive once the pack has loaded, and grouping a few dozen is cheap.
    val groups = patternGroups(patternsFor)
    CtActionPage(
        title = "Custom run",
        onBack = onBack,
        modifier = modifier,
        bottom = { CtButton("Start $count puzzles", onClick = { onStart(low..high, pattern, count, timed) }, modifier = Modifier.weight(1f)) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.lg)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Puzzle rating", style = CtType.Label.copy(color = CtColors.Text), modifier = Modifier.weight(1f))
                    Text("$low to $high", style = CtType.Label.copy(color = CtColors.Accent))
                }
                RangeSlider(
                    value = low.toFloat()..high.toFloat(),
                    onValueChange = { range ->
                        low = ((range.start / STEP).roundToInt() * STEP).coerceAtMost(high - STEP)
                        high = ((range.endInclusive / STEP).roundToInt() * STEP).coerceAtLeast(low + STEP)
                    },
                    valueRange = MIN.toFloat()..MAX.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = CtColors.Text, activeTrackColor = CtColors.Text, inactiveTrackColor = CtColors.Track),
                    modifier = Modifier.semantics { contentDescription = "Puzzle rating from $low to $high" },
                )
                Text("Yours: $puzzleRating", style = CtType.Caption.copy(color = CtColors.Text2))
            }
            Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                CtGroupLabel("Patterns")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    CtChip("Any pattern", selected = pattern == null, onClick = { pattern = null })
                }
            }
            groups.forEach { (goal, tags) ->
                Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    Text(goal.title, style = CtType.Caption.copy(color = CtColors.Text2))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                        tags.forEach { tag -> CtChip(PuzzleThemes.displayName(tag), selected = tag == pattern, onClick = { pattern = tag }) }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                CtGroupLabel("How many")
                CtSegmented(options = COUNTS, selected = count, label = { "$it" }, onSelect = { count = it })
            }
            CtToggleRow(title = "Against the clock", checked = timed, onCheckedChange = { timed = it })
        }
    }
}

private const val MIN = 400
private const val MAX = 3000
private const val STEP = 50

