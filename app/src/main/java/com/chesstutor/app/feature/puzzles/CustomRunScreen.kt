package com.chesstutor.app.feature.puzzles

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
 * Custom run (D64, from Puzzle modes): every pattern on one page, grouped under its goal's piece
 * and wrapped so nothing scrolls sideways; the rating as one track of five steps with the player's
 * own marked; how many, the timer and Start fixed at the bottom.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomRunScreen(
    patternsFor: (PuzzleGoal) -> List<String>,
    puzzleRating: Int,
    onStart: (RatingBand, pattern: String?, count: Int, timed: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pieceSet: PieceSet = PieceSet.DEFAULT,
) {
    val own = RatingBand.entries.firstOrNull { puzzleRating in it.range } ?: RatingBand.CASUAL
    var bandName by rememberSaveable { mutableStateOf(own.name) }
    var pattern by rememberSaveable { mutableStateOf<String?>(null) }
    var count by rememberSaveable { mutableIntStateOf(10) }
    var timed by rememberSaveable { mutableStateOf(false) }
    val band = RatingBand.valueOf(bandName)
    // Not remembered: the patterns arrive once the pack has loaded, and grouping a few dozen is cheap.
    val groups = patternGroups(patternsFor)
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize()) {
            CtTopBar(title = "Custom run", onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = CtSpacing.gutter, end = CtSpacing.gutter, bottom = CtSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.xl),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                    Text(band.label, style = CtType.Headline)
                    BandTrack(band, own, puzzleRating, onPick = { bandName = it.name })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    CtChip("Any pattern", selected = pattern == null, onClick = { pattern = null })
                }
                groups.forEach { (goal, tags) ->
                    Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                            Image(painterResource(PieceArt.drawable(pieceSet, Piece(goalPiece(goal), PieceColor.WHITE))), contentDescription = null, modifier = Modifier.size(28.dp))
                            Text(goal.title, style = CtType.TitleSmall)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                            tags.forEach { tag -> CtChip(PuzzleThemes.displayName(tag), selected = tag == pattern, onClick = { pattern = tag }) }
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().background(CtColors.Bg).padding(horizontal = CtSpacing.gutter, vertical = CtSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                    CtSegmented(options = COUNTS, selected = count, label = { "$it" }, onSelect = { count = it }, modifier = Modifier.weight(1f))
                    CtChip("Timer", icon = Icons.Rounded.Timer, selected = timed, onClick = { timed = !timed })
                }
                CtButton("Start $count puzzles", onClick = { onStart(band, pattern, count, timed) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** The five rating bands as one track, the chosen one filled, "You" over the player's own rating. */
@Composable
private fun BandTrack(selected: RatingBand, own: RatingBand, puzzleRating: Int, onPick: (RatingBand) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val step = maxWidth / RatingBand.entries.size
        val within = ((puzzleRating - own.range.first).toFloat() / (own.range.last + 1 - own.range.first)).coerceIn(0.1f, 0.9f)
        Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.xs)) {
            Box(Modifier.fillMaxWidth().height(18.dp)) {
                Text(
                    "You",
                    style = CtType.Caption.copy(color = CtColors.Accent),
                    modifier = Modifier.offset(x = step * (own.ordinal + within) - 12.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                RatingBand.entries.forEach { option ->
                    val on = option == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(CtShapes.Chip)
                            .background(if (on) CtColors.Primary else CtColors.Surface)
                            .ctPressable(role = Role.RadioButton, onClickLabel = "Choose ${option.label}") { onPick(option) }
                            .semantics { this.selected = on },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (option == RatingBand.EXPERT) "${option.range.first}+" else "${option.range.first}",
                            style = CtType.Label.copy(color = if (on) CtColors.OnPrimary else CtColors.Text2),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
