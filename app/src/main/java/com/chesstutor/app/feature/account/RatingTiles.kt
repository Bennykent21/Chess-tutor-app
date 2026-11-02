package com.chesstutor.app.feature.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardPicture
import com.chesstutor.app.board.BoardScenes
import com.chesstutor.app.board.PieceArt
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.design.CtGlassMark
import com.chesstutor.app.design.CtPictureInk
import com.chesstutor.app.design.CtPictureTile
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.puzzle.PuzzleStats
import com.chesstutor.app.viewmodel.AppUiState
import java.util.Locale

/** Rating and puzzle rating on the player's board, each with its recent change, then the level. */
@Composable
internal fun Numbers(
    state: AppUiState,
    stats: PuzzleStats,
    gameChange: Int?,
    puzzleChange: Int?,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val board = state.appSettings.boardTheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
            RatingTile(state.effectiveUserRating, "Rating", gameChange, onOpenStats, Modifier.weight(1f), scene = { BoardPicture(BoardScenes.MIDDLEGAME, board) }) {
                Image(painterResource(PieceArt.drawable(board.pieceSet, Piece(PieceType.KING, PieceColor.WHITE))), contentDescription = null, modifier = Modifier.size(24.dp))
            }
            RatingTile(stats.rating, "Puzzle rating", puzzleChange, onOpenStats, Modifier.weight(1f), scene = { BoardPicture(BoardScenes.ATTACK, board) }) {
                Icon(Icons.Rounded.Extension, contentDescription = null, tint = CtPictureInk, modifier = Modifier.size(18.dp))
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
private fun RatingTile(
    value: Int,
    label: String,
    change: Int?,
    onClick: () -> Unit,
    modifier: Modifier,
    scene: @Composable BoxScope.() -> Unit,
    mark: @Composable BoxScope.() -> Unit,
) {
    CtPictureTile(
        title = "$value",
        subtitle = label,
        modifier = modifier.height(128.dp),
        titleStyle = CtType.Display,
        onClick = onClick,
        onClickLabel = "Open your stats",
        trailing = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(CtSpacing.xs)) {
                CtGlassMark(32.dp, mark)
                if (change != null && change != 0) Text(changeText(change), style = CtType.Caption.copy(color = CtPictureInk))
            }
        },
        picture = scene,
    )
}
