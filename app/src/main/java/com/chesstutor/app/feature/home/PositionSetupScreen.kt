package com.chesstutor.app.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
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
import com.chesstutor.app.design.CtBoardScreen
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtGroupLabel
import com.chesstutor.app.design.CtMenuItem
import com.chesstutor.app.design.CtOverflowMenu
import com.chesstutor.app.design.CtSegmented
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSheet
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTextField
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.bot.RosterBot
import com.chesstutor.app.domain.game.SetupBoard
import com.chesstutor.app.domain.game.SideChoice

/**
 * "From a position" (plan 6.1, mock-up round 17): set up any position on the fixed board,
 * choose a side and an opponent, and play it out against that bot.
 */
@Composable
fun PositionSetupScreen(
    draft: PositionDraft,
    theme: BoardTheme,
    opponents: List<RosterBot>,
    onChange: ((PositionDraft) -> PositionDraft) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val opponent = opponents.firstOrNull { it.id == draft.opponentId }
    SetupBoardScreen(
        title = "From a position",
        board = draft.board,
        brush = draft.brush,
        theme = theme,
        onBoard = { next -> onChange { it.copy(board = next) } },
        onBrush = { brush -> onChange { it.copy(brush = brush) } },
        onBack = onBack,
        startLabel = opponent?.let { "Play ${it.name}" } ?: "Play",
        onStart = onStart,
    ) {
        CtGroupLabel("You play")
        CtSegmented(SideChoice.entries, draft.side, { it.label }, { choice -> onChange { it.copy(side = choice) } })
        CtGroupLabel("Opponent")
        BotPickerRow(opponents, draft.opponentId) { id -> onChange { it.copy(opponentId = id) } }
    }
}

/**
 * A board screen for setting up a position (From a position and Explore): the board stays
 * fixed under who moves first, the pieces to place sit below it with the page's own [options],
 * and the start button is fixed at the bottom. Start position, kings only and a FEN are in the
 * menu. [SetupBoard.problem] keeps the button off until the position can be played.
 */
@Composable
internal fun SetupBoardScreen(
    title: String,
    board: SetupBoard,
    brush: Piece?,
    theme: BoardTheme,
    onBoard: (SetupBoard) -> Unit,
    onBrush: (Piece?) -> Unit,
    onBack: () -> Unit,
    startLabel: String,
    onStart: () -> Unit,
    options: @Composable ColumnScope.() -> Unit,
) {
    val problem = remember(board) { board.problem() }
    var fenOpen by remember { mutableStateOf(false) }
    CtBoardScreen(
        title = title,
        onBack = onBack,
        actions = {
            CtOverflowMenu(
                listOf(
                    CtMenuItem("Start position") { onBoard(SetupBoard.START) },
                    CtMenuItem("Kings only") { onBoard(SetupBoard.KINGS_ONLY.withSideToMove(board.sideToMove)) },
                    CtMenuItem("Enter a FEN") { fenOpen = true },
                ),
            )
        },
        status = {
            CtSegmented(
                options = PieceColor.entries,
                selected = board.sideToMove,
                label = { if (it == PieceColor.WHITE) "White to move" else "Black to move" },
                onSelect = { onBoard(board.withSideToMove(it)) },
            )
        },
        board = { EditorBoard(board, theme) { square -> onBoard(board.place(square, brush)) } },
        controls = { CtButton(text = startLabel, enabled = problem == null, onClick = onStart, modifier = Modifier.weight(1f)) },
        overlay = { FenSheet(fenOpen, board, onBoard, onDismiss = { fenOpen = false }) },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (problem != null) Text(problem, style = CtType.BodySmall.copy(color = CtColors.Error), modifier = Modifier.padding(bottom = CtSpacing.sm))
            Palette(theme, brush, onBrush)
            options()
        }
    }
}

/** Paste a position as a FEN; the board follows as soon as it reads. */
@Composable
private fun FenSheet(visible: Boolean, board: SetupBoard, onBoard: (SetupBoard) -> Unit, onDismiss: () -> Unit) {
    var fenText by remember { mutableStateOf(board.toFen()) }
    var fenError by remember { mutableStateOf(false) }
    val boardFen = board.toFen()
    LaunchedEffect(boardFen) {
        fenText = boardFen
        fenError = false
    }
    CtSheet(visible = visible, onDismiss = onDismiss) {
        CtTextField(
            value = fenText,
            onValueChange = { text ->
                fenText = text
                val parsed = SetupBoard.fromFen(text)
                fenError = parsed == null
                if (parsed != null) onBoard(parsed)
            },
            label = "FEN",
            placeholder = Position.STARTING_FEN,
        )
        if (fenError) Text("That FEN cannot be read.", style = CtType.BodySmall.copy(color = CtColors.Error))
        CtButton(text = "Done", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun EditorBoard(board: SetupBoard, theme: BoardTheme, onTap: (com.chesstutor.app.core.Square) -> Unit) {
    // The editor may hold positions the rules engine rejects (no kings yet); draw what is there.
    val position = remember(board) { Position(board.squares.toTypedArray(), board.sideToMove) }
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

/** The pieces to place, a row a colour, and an eraser; the chosen one is filled. */
@Composable
private fun Palette(theme: BoardTheme, brush: Piece?, onPick: (Piece?) -> Unit) {
    val types = listOf(PieceType.KING, PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN)
    Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.xs)) {
        PieceColor.entries.forEach { color ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                types.forEach { type ->
                    val piece = Piece(type, color)
                    PaletteCell(chosen = brush == piece, label = "${color.name.lowercase()} ${type.name.lowercase()}", onClick = { onPick(piece) }) {
                        Image(painterResource(PieceArt.drawable(theme.pieceSet, piece)), contentDescription = null, modifier = Modifier.fillMaxSize().padding(3.dp))
                    }
                }
                if (color == PieceColor.WHITE) {
                    PaletteCell(chosen = brush == null, label = "eraser", onClick = { onPick(null) }) {
                        Icon(Icons.Rounded.Clear, contentDescription = null, tint = CtColors.Error, modifier = Modifier.fillMaxSize().padding(9.dp))
                    }
                } else {
                    Box(Modifier.size(44.dp))
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
            .background(if (chosen) CtColors.Raised else CtColors.Surface)
            .then(if (chosen) Modifier.border(2.dp, CtColors.Accent, CtShapes.Chip) else Modifier)
            .ctPressable(onClickLabel = "Place $label", onClick = onClick)
            .semantics {
                role = Role.RadioButton
                selected = chosen
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}
