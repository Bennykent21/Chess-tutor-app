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
import androidx.compose.material.icons.Icons
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
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtScene
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSheet
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtStreak
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.account.RatingRank
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

/** "274 to Queen", or nothing to climb at the top. */
internal fun rankLine(rank: RatingRank, rating: Int): String =
    rank.next?.let { "${it.from - rating} to ${it.label}" } ?: "The top piece"

/** The player's earned piece, lit on the page (D56), with who they are and how far to the next one. */
@Composable
internal fun AccountHero(state: AppUiState, rank: RatingRank, onOpenRanks: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val rating = state.effectiveUserRating
    Box(modifier.fillMaxWidth().height(340.dp).ctPressable(onClickLabel = "See the pieces you can earn", onClick = onOpenRanks)) {
        CtArt(rank.scene(), Modifier.matchParentSize(), fadeFrom = 0.5f, alive = true)
        PlayerRow(state, onOpenSettings, Modifier.align(Alignment.TopStart).padding(horizontal = CtSpacing.gutter, vertical = CtSpacing.sm))
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = CtSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(CtSpacing.xs),
        ) {
            Text(rank.label, style = CtType.Display)
            Text(rankLine(rank, rating), style = CtType.Caption)
            CtProgressBar(progress = rank.progress(rating), height = 3.dp, modifier = Modifier.padding(top = CtSpacing.xs))
        }
    }
}

/** The player's picture, "@name" with where the rating comes from, and the way to Settings. */
@Composable
private fun PlayerRow(state: AppUiState, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val linked = state.linkedProfile
    val name = state.appSettings.playerName.trim()
    val title = name.ifEmpty { linked?.let { "@${it.username}" } ?: "You" }
    val detail = linked?.let {
        val where = "${it.platform.displayName}, ${it.selectedTimeControl.displayName.lowercase(Locale.UK)}"
        if (name.isEmpty()) where else "@${it.username} · $where"
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
        PlayerAvatar(state.appSettings.picture, name.ifEmpty { linked?.username.orEmpty() }, size = 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CtSpacing.xxs)) {
            Text(title, style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = CtType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier
                .size(CtSpacing.touchTarget)
                .clip(CtShapes.Pill)
                .background(CtColors.Surface)
                .ctPressable(onClickLabel = "Open settings", onClick = onOpenSettings)
                .semantics { contentDescription = "Settings" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = null, tint = CtColors.Text, modifier = Modifier.size(20.dp))
        }
    }
}

/** Rating, puzzle rating with their recent change, and the streak; then the level. */
@Composable
internal fun Numbers(state: AppUiState, stats: PuzzleStats, gameChange: Int?, puzzleChange: Int?, onOpenStats: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.ctPressable(onClickLabel = "Open your stats", onClick = onOpenStats), verticalAlignment = Alignment.Top) {
            Figure(state.effectiveUserRating, "Rating", gameChange, Modifier.weight(1f))
            Figure(stats.rating, "Puzzle rating", puzzleChange, Modifier.weight(1f))
            CtStreak(stats.dayStreak, Modifier.padding(top = CtSpacing.xxs))
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
private fun Figure(value: Int, label: String, change: Int?, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(CtSpacing.xs)) {
            Text("$value", style = CtType.Number.copy(color = CtColors.Accent), maxLines = 1)
            if (change != null && change != 0) {
                Text(changeText(change), style = CtType.Caption.copy(color = if (change > 0) CtColors.Success else CtColors.Error), maxLines = 1)
            }
        }
        Text(label, style = CtType.Caption, maxLines = 1)
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
