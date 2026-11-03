package com.chesstutor.app.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtArt
import com.chesstutor.app.design.CtMetalIcon
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtScene
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSheet
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.account.RatingRank
import com.chesstutor.app.domain.account.TimeClass
import com.chesstutor.app.domain.puzzle.PuzzleStats
import com.chesstutor.app.feature.home.PlayerAvatar
import com.chesstutor.app.viewmodel.AppUiState
import java.util.Locale

private fun RatingRank.scene(): CtScene = when (this) {
    RatingRank.PAWN -> CtScene.RANK_PAWN
    RatingRank.KNIGHT -> CtScene.RANK_KNIGHT
    RatingRank.BISHOP -> CtScene.RANK_BISHOP
    RatingRank.ROOK -> CtScene.RANK_ROOK
    RatingRank.QUEEN -> CtScene.RANK_QUEEN
    RatingRank.KING -> CtScene.RANK_KING
}

/** The player's earned piece, lit on the page (D56), with who they are at its foot; a tap shows every piece. */
@Composable
internal fun AccountHero(state: AppUiState, rank: RatingRank, onOpenRanks: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(300.dp)
            .ctPressable(onClickLabel = "See the pieces you can earn", onClick = onOpenRanks)
            .semantics { contentDescription = "Your piece: ${rank.label}" },
    ) {
        CtArt(rank.scene(), Modifier.matchParentSize(), fadeFrom = 0.55f, alive = true)
        PlayerRow(state, Modifier.align(Alignment.BottomStart).padding(horizontal = CtSpacing.gutter, vertical = CtSpacing.xs))
    }
}

/** The player's picture and "@name", with the site the ratings come from. */
@Composable
private fun PlayerRow(state: AppUiState, modifier: Modifier = Modifier) {
    val linked = state.linkedProfile
    val name = state.appSettings.playerName.trim()
    val title = name.ifEmpty { linked?.let { "@${it.username}" } ?: "You" }
    val detail = linked?.let { if (name.isEmpty()) it.platform.displayName else "@${it.username} · ${it.platform.displayName}" }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
        PlayerAvatar(state.appSettings.picture, name.ifEmpty { linked?.username.orEmpty() }, size = 48.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CtSpacing.xxs)) {
            Text(title, style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = CtType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The way to Settings: a gear beside the page's title, as players expect. */
@Composable
internal fun SettingsButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(CtSpacing.touchTarget)
            .clip(CtShapes.Pill)
            .background(CtColors.Surface)
            .ctPressable(onClickLabel = "Open settings", onClick = onClick)
            .semantics { contentDescription = "Settings" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Settings, contentDescription = null, tint = CtColors.Text, modifier = Modifier.size(20.dp))
    }
}

/** One rating in the row under the player: a game clock, or the puzzles when [clock] is null. */
internal data class RatingCell(val value: String, val label: String, val clock: TimeClass?)

/** The linked account's three clocks and the puzzle rating, or the app's own rating and the puzzle rating. */
internal fun ratingCells(state: AppUiState, puzzleRating: Int): List<RatingCell> {
    val linked = state.linkedProfile
    val games = if (linked == null) {
        listOf(RatingCell("${state.effectiveUserRating}", "Rating", TimeClass.RAPID))
    } else {
        listOf(
            TimeClass.RAPID to linked.rapidRating,
            TimeClass.BLITZ to linked.blitzRating,
            TimeClass.BULLET to linked.bulletRating,
        ).map { (clock, rating) -> RatingCell(rating?.toString() ?: "–", clock.label, clock) }
    }
    return games + RatingCell("$puzzleRating", "Puzzles", clock = null)
}

/** Each rating under its clock's icon, kept apart by a thin rule, then the level. A rating opens its stats. */
@Composable
internal fun Numbers(
    state: AppUiState,
    stats: PuzzleStats,
    onOpenClock: (TimeClass) -> Unit,
    onOpenPuzzles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ratingCells(state, stats.rating).forEachIndexed { i, cell ->
                if (i > 0) Box(Modifier.width(1.dp).height(40.dp).background(CtColors.Line))
                Figure(cell, onClick = { cell.clock?.let(onOpenClock) ?: onOpenPuzzles() }, modifier = Modifier.weight(1f))
            }
        }
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
        ) {
            Text("Level ${stats.playerLevel.level}", style = CtType.Label)
            CtProgressBar(progress = stats.playerLevel.progress, height = 3.dp, modifier = Modifier.weight(1f))
            Text("${String.format(Locale.UK, "%,d", stats.totalXp)} XP", style = CtType.Caption)
        }
    }
}

@Composable
private fun Figure(cell: RatingCell, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .ctPressable(onClickLabel = if (cell.clock == null) "Open training" else "Open ${cell.label} stats", onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = CtSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (cell.clock != null) ClockIcon(cell.clock, size = 22.dp) else CtMetalIcon(Icons.Rounded.Extension, size = 22.dp)
        Text(cell.value, style = CtType.Number.copy(color = CtColors.Text), maxLines = 1)
        Text(cell.label, style = CtType.Caption, maxLines = 1)
    }
}

/** Every piece to earn, the player's own marked, the ones above it dimmed. */
@Composable
internal fun RankSheet(visible: Boolean, rank: RatingRank, onDismiss: () -> Unit) {
    CtSheet(visible = visible, onDismiss = onDismiss) {
        CtSectionHeader(title = "Your piece")
        Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
            RatingRank.entries.reversed().forEach { piece ->
                val earned = piece <= rank
                Row(
                    modifier = Modifier.fillMaxWidth().alpha(if (earned) 1f else 0.5f).semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
                ) {
                    CtArt(piece.scene(), Modifier.size(52.dp).clip(CtShapes.Chip), stage = CtColors.Raised, fadeFrom = 1f)
                    Text(piece.label, style = if (piece == rank) CtType.TitleSmall else CtType.Body.copy(color = CtColors.Text), modifier = Modifier.weight(1f))
                    Text(if (piece.from == 0) "Start" else "${piece.from}", style = CtType.Caption)
                }
            }
        }
    }
}
