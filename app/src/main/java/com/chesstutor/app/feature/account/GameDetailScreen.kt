package com.chesstutor.app.feature.account

import android.content.ClipData
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardMarks
import com.chesstutor.app.board.BoardView
import com.chesstutor.app.board.PieceArt
import com.chesstutor.app.board.moveListOf
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtBoardScreen
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtConfirmDialog
import com.chesstutor.app.design.CtDivider
import com.chesstutor.app.design.CtIconButton
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.PgnFormatter
import com.chesstutor.app.domain.account.GameOutcome
import com.chesstutor.app.domain.account.GameReviewer
import com.chesstutor.app.domain.account.GameSummary
import kotlinx.coroutines.launch

/** What the game page can do. */
data class GameDetailActions(
    /** Opens the game review (analysing the game first if it has not been). */
    val onReview: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onBack: () -> Unit = {},
    /** Opens the Learn course that teaches the game's opening (D44). */
    val onOpenCourse: (courseId: String) -> Unit = {},
)

/**
 * One saved game (plan section 6.4, mock-up round 15): the result, the game on a fixed board
 * to step through with a move strip that follows it, the opening (a tap opens its course),
 * and Review. Accuracy lives on the review; Copy PGN and Delete sit in the menu.
 */
@Composable
fun GameDetailScreen(
    game: GameSummary?,
    settings: AppSettings,
    actions: GameDetailActions,
    modifier: Modifier = Modifier,
    /** The course for the game's opening family, if one teaches it. */
    courseId: String? = null,
    now: Long = System.currentTimeMillis(),
) {
    if (game == null) {
        CtScreenBackground(modifier) {
            Column(Modifier.fillMaxSize()) { CtTopBar(title = "Game", subtitle = "This game is no longer saved", onBack = actions.onBack) }
        }
        return
    }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val copyScope = rememberCoroutineScope()
    val startFen = game.startFen ?: Position.STARTING_FEN
    val positions = remember(game.id) { GameReviewer.positions(startFen, game.moves) }
    val entries = remember(game.id) {
        moveListOf(Position.tryFromFen(startFen).getOrElse { Position.initial() }, game.moves.mapNotNull { runCatching { Move.fromUci(it) }.getOrNull() })
    }
    // The game opens on its final position.
    var ply by rememberSaveable(game.id) { mutableIntStateOf(positions.lastIndex.coerceAtLeast(0)) }
    val position = positions.getOrNull(ply) ?: Position.fromFen(startFen)
    val last = game.moves.getOrNull(ply - 1)?.let { runCatching { Move.fromUci(it) }.getOrNull() }
    val check = if (LegalMoveGenerator.isKingInCheck(position)) position.findKing(position.sideToMove) else null

    CtBoardScreen(
        title = "vs ${game.opponent}",
        onBack = actions.onBack,
        modifier = modifier,
        actions = {
            GameMenu(
                onCopy = {
                    val pgn = PgnFormatter.reconstructPgnFromGameRecord(game.record)
                    copyScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("PGN", pgn))) }
                },
                onDelete = { confirmDelete = true },
            )
        },
        status = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ResultSquare(game.outcome, size = 40.dp)
                Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.xxs)) {
                    Text(resultTitle(game), style = CtType.Title)
                    Text(resultLine(game, now), style = CtType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        board = {
            BoardView(
                position = position,
                theme = settings.boardTheme,
                whiteAtBottom = game.userColor == PieceColor.WHITE,
                showCoordinates = settings.showCoordinates,
                showLegalDots = false,
                marks = BoardMarks(lastMove = last?.let { it.from to it.to }, check = check),
            )
        },
        controls = {
            CtIconButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous move", onClick = { ply -= 1 }, enabled = ply > 0)
            CtIconButton(icon = Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Next move", onClick = { ply += 1 }, enabled = ply < positions.lastIndex)
            CtButton(text = "Review", onClick = actions.onReview, enabled = game.moves.isNotEmpty(), modifier = Modifier.weight(1f))
        },
        overlay = {
            if (confirmDelete) {
                CtConfirmDialog(
                    title = "Delete this game?",
                    text = "A game from Chess.com or Lichess comes back on the next sync.",
                    confirmLabel = "Delete",
                    onConfirm = {
                        confirmDelete = false
                        actions.onDelete()
                    },
                    onDismiss = { confirmDelete = false },
                )
            }
        },
    ) {
        if (entries.isNotEmpty()) {
            Box(Modifier.padding(vertical = CtSpacing.sm)) {
                MoveStrip(entries, review = null, current = ply, onSelect = { ply = it })
            }
        }
        OpeningRow(game, courseId, settings, onOpen = { courseId?.let(actions.onOpenCourse) })
        if (game.record.incompleteParse) {
            Text("Some moves could not be read from this game.", style = CtType.Caption.copy(color = CtColors.Error))
        }
    }
}

/** Copy PGN and Delete game, out of the way in the top bar's menu. */
@Composable
private fun GameMenu(onCopy: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(CtSpacing.touchTarget)
                .clip(CtShapes.Pill)
                .ctPressable(onClickLabel = "More options") { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = CtColors.Text)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = CtColors.Raised) {
            DropdownMenuItem(
                text = { Text("Copy PGN", style = CtType.Body.copy(color = CtColors.Text)) },
                onClick = {
                    open = false
                    onCopy()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete game", style = CtType.Body.copy(color = CtColors.Error)) },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}

/** The opening with the player's own knight; a tap opens its course when one teaches it. */
@Composable
private fun OpeningRow(game: GameSummary, courseId: String?, settings: AppSettings, onOpen: () -> Unit) {
    val title = game.openingFamily ?: if (game.startFen != null) "Set-up position" else "Opening not named"
    val detail = openingDetail(game)
    Column {
        CtDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 60.dp)
                .then(if (courseId != null) Modifier.ctPressable(onClickLabel = "Learn the $title", onClick = onOpen) else Modifier)
                .semantics(mergeDescendants = true) {}
                .padding(vertical = CtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(40.dp).clip(CtShapes.Pill).background(CtColors.Surface), contentAlignment = Alignment.Center) {
                Image(painterResource(PieceArt.drawable(settings.boardTheme.pieceSet, Piece(PieceType.KNIGHT, PieceColor.WHITE))), contentDescription = null, modifier = Modifier.size(32.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CtSpacing.xxs)) {
                Text(title, style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail != null) Text(detail, style = CtType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (courseId != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = CtColors.Text2, modifier = Modifier.size(20.dp))
        }
    }
}

internal fun resultTitle(game: GameSummary): String = when (game.outcome) {
    GameOutcome.WIN -> "You won"
    GameOutcome.LOSS -> "You lost"
    GameOutcome.DRAW -> "Draw"
    GameOutcome.UNKNOWN -> "No result"
}

/** "Checkmate, 7 moves, today". */
internal fun resultLine(game: GameSummary, now: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String = buildList {
    game.record.termination.trim().takeIf { it.isNotEmpty() }?.let { add(it.replaceFirstChar(Char::uppercaseChar)) }
    add("${game.moveNumber} move${if (game.moveNumber == 1) "" else "s"}")
    add(relativeDay(game.dateMillis, now, zone))
}.joinToString(", ")

/** "Blackburne Shilling Gambit, out of book on move 4": the variation, then where the book ended. */
internal fun openingDetail(game: GameSummary): String? {
    if (game.startFen != null) return null
    val variation = game.openingName?.substringAfter(':', "")?.trim()?.ifEmpty { null }
    val book = game.leftBookAtMove?.let { "out of book on move $it" }
    return listOfNotNull(variation, book).joinToString(", ").ifEmpty { null }?.replaceFirstChar(Char::uppercaseChar)
}
