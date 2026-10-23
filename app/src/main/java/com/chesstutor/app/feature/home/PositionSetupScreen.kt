package com.chesstutor.app.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardCanvas
import com.chesstutor.app.board.BoardGeometry
import com.chesstutor.app.board.BoardMarks
import com.chesstutor.app.board.BoardMotion
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.board.PieceArt
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTextField
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.bot.RosterBot
import com.chesstutor.app.domain.game.SetupBoard
import com.chesstutor.app.domain.game.SideChoice

/**
 * "From a position" (plan 6.1): set up any position by hand or paste a FEN, choose a side and
 * an opponent, and play it out against a bot.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PositionSetupScreen(
    draft: PositionDraft,
    theme: BoardTheme,
    opponents: List<RosterBot>,
    onChange: ((PositionDraft) -> PositionDraft) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val board = draft.board
    val problem = remember(board) { board.problem() }
    // The FEN box keeps what was typed until it reads as a board.
    var fenText by remember { mutableStateOf(board.toFen()) }
    var fenError by remember { mutableStateOf(false) }
    val boardFen = board.toFen()
    // A change on the board (or a FEN that read well) rewrites the box.
    LaunchedEffect(boardFen) {
        fenText = boardFen
        fenError = false
    }

    CtScreenBackground {
        Column(Modifier.fillMaxSize()) {
            CtTopBar(title = "From a position", subtitle = "Set up any position and play it", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
            ) {
                EditorBoard(board, theme) { square -> onChange { it.copy(board = it.board.place(square, it.brush)) } }
                Palette(theme, draft.brush) { brush -> onChange { it.copy(brush = brush) } }
                Text("Tap a piece, then tap squares to place it. Tap the same piece again to remove it.", style = CtType.Caption)
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    CtButton(text = "Start position", style = CtButtonStyle.Secondary, onClick = { onChange { it.copy(board = SetupBoard.START) } }, modifier = Modifier.weight(1f))
                    CtButton(text = "Kings only", style = CtButtonStyle.Secondary, onClick = { onChange { it.copy(board = SetupBoard.KINGS_ONLY.withSideToMove(it.board.sideToMove)) } }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    PieceColor.entries.forEach { color ->
                        val label = if (color == PieceColor.WHITE) "White to move" else "Black to move"
                        CtChip(text = label, selected = board.sideToMove == color, color = CtColors.Teal, onClick = { onChange { it.copy(board = it.board.withSideToMove(color)) } })
                    }
                }
                CtTextField(
                    value = fenText,
                    onValueChange = { text ->
                        fenText = text
                        val parsed = SetupBoard.fromFen(text)
                        fenError = parsed == null
                        if (parsed != null) onChange { it.copy(board = parsed) }
                    },
                    label = "FEN",
                    placeholder = Position.STARTING_FEN,
                )
                when {
                    fenError -> Text("That FEN cannot be read.", style = CtType.BodySmall.copy(color = CtColors.Coral))
                    problem != null -> Text(problem, style = CtType.BodySmall.copy(color = CtColors.Coral))
                }

                CtSectionHeader(title = "You play")
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    SideChoice.entries.forEach { choice ->
                        CtChip(text = choice.label, selected = draft.side == choice, color = CtColors.Gold, onClick = { onChange { it.copy(side = choice) } })
                    }
                }
                CtSectionHeader(title = "Opponent")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    opponents.forEach { bot ->
                        CtChip(text = "${bot.name} ${bot.rating}", selected = draft.opponentId == bot.id, color = botAccent(bot.rating), onClick = { onChange { it.copy(opponentId = bot.id) } })
                    }
                }
                Spacer(Modifier.height(CtSpacing.sm))
                CtButton(
                    text = "Play from here",
                    icon = Icons.Rounded.PlayArrow,
                    enabled = problem == null && !fenError,
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}

@Composable
private fun EditorBoard(board: SetupBoard, theme: BoardTheme, onTap: (com.chesstutor.app.core.Square) -> Unit) {
    // The editor may hold positions the rules engine rejects (no kings yet); draw what is there.
    val position = remember(board) { Position(board.squares.toTypedArray(), board.sideToMove) }
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
        BoardCanvas(
            position = position,
            theme = theme,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        BoardGeometry.squareAt(offset.x, offset.y, size.width.toFloat(), whiteAtBottom = true)?.let(onTap)
                    }
                },
            whiteAtBottom = true,
            showCoordinates = true,
            showLegalDots = false,
            marks = BoardMarks(),
            motion = BoardMotion(),
        )
    }
}

/** The pieces to place, both colours, and an eraser. */
@Composable
private fun Palette(theme: BoardTheme, brush: Piece?, onPick: (Piece?) -> Unit) {
    val types = listOf(PieceType.KING, PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN)
    Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.xs)) {
        PieceColor.entries.forEach { color ->
            Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                types.forEach { type ->
                    val piece = Piece(type, color)
                    PaletteCell(chosen = brush == piece, label = "${color.name.lowercase()} ${type.name.lowercase()}", onClick = { onPick(piece) }) {
                        Image(painterResource(PieceArt.drawable(theme.pieceSet, piece)), contentDescription = null, modifier = Modifier.fillMaxSize().padding(2.dp))
                    }
                }
                if (color == PieceColor.WHITE) {
                    PaletteCell(chosen = brush == null, label = "eraser", onClick = { onPick(null) }) {
                        Icon(Icons.Rounded.Clear, contentDescription = null, tint = CtColors.Coral, modifier = Modifier.fillMaxSize().padding(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteCell(chosen: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CtShapes.Chip)
            .background(if (chosen) CtColors.SurfaceHigh else CtColors.Surface)
            .border(2.dp, if (chosen) CtColors.Gold else CtColors.Line, CtShapes.Chip)
            .ctPressable(onClickLabel = "Place $label", onClick = onClick)
            .semantics {
                role = Role.RadioButton
                selected = chosen
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}
