package com.chesstutor.app.feature.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardHost
import com.chesstutor.app.board.BoardMoveList
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.board.moveListOf
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.SanFormatter
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtBackBar
import com.chesstutor.app.design.CtBoardScreen
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtConfirmDialog
import com.chesstutor.app.design.CtControl
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtMetalIcon
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSegmented
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTextField
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.opening.MyLine
import com.chesstutor.app.domain.opening.MyLineStore
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.feature.learn.BoardThumbnail
import com.chesstutor.app.feature.play.PlaySession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** A line being built: both sides' moves on the board, the side the player will play, and the book's name for it. */
data class LineDraft(val session: PlaySession, val side: PieceColor = PieceColor.WHITE, val bookName: String? = null, val version: Int = 0) {
    val moves: List<String> get() = session.moves.map { it.uci }
}

/** Your openings (Phase 12): lines the player builds, keeps, drills and plays out against Stockfish. */
class MyLinesViewModel(
    private val store: MyLineStore,
    private val loadTree: suspend () -> OpeningTree,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    val lines: StateFlow<List<MyLine>> = store.lines()

    private var tree: OpeningTree = OpeningTree.EMPTY
    private val mutableDraft = MutableStateFlow<LineDraft?>(null)
    val draft: StateFlow<LineDraft?> = mutableDraft.asStateFlow()

    init {
        viewModelScope.launch { tree = runCatching { loadTree() }.getOrDefault(OpeningTree.EMPTY) }
    }

    fun newLine(side: PieceColor = PieceColor.WHITE) {
        val session = PlaySession(Position.initial(), PieceColor.entries.toSet()) { refresh() }
        session.open = true
        mutableDraft.value = LineDraft(session, side)
    }

    fun setSide(side: PieceColor) {
        mutableDraft.value = mutableDraft.value?.copy(side = side)
    }

    fun undo() {
        val draft = mutableDraft.value ?: return
        draft.session.undo(1)
        refresh()
    }

    /** Keeps the line under [name] (made unique) and returns it; null with no moves yet. */
    fun save(name: String): MyLine? {
        val draft = mutableDraft.value ?: return null
        if (draft.moves.isEmpty()) return null
        val line = MyLine(newId(), MyLine.freeName(name, lines.value.map { it.name }), draft.side, draft.moves)
        store.save(line)
        mutableDraft.value = null
        return line
    }

    fun discard() {
        mutableDraft.value = null
    }

    fun delete(id: String) = store.delete(id)

    private fun refresh() {
        val draft = mutableDraft.value ?: return
        val name = if (tree.isEmpty || draft.moves.isEmpty()) null else tree.match(draft.moves).opening?.name
        mutableDraft.value = draft.copy(bookName = name, version = draft.version + 1)
    }

    class Factory(private val store: MyLineStore, private val loadTree: suspend () -> OpeningTree) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MyLinesViewModel(store, loadTree) as T
    }
}

/** "1.e4 e5 2.Nf3 Nc6" for a line from the start. */
fun sanLine(moves: List<String>): String {
    var position = Position.initial()
    val out = StringBuilder()
    for ((i, uci) in moves.withIndex()) {
        val move = LegalMoveGenerator.generateLegalMoves(position).firstOrNull { it.uci == uci } ?: break
        if (i % 2 == 0) out.append(if (i == 0) "" else " ").append("${i / 2 + 1}.") else out.append(' ')
        out.append(SanFormatter.format(position, move))
        position = LegalMoveGenerator.makeMove(position, move)
    }
    return out.toString()
}

private fun endFen(moves: List<String>): String {
    var position = Position.initial()
    for (uci in moves) position = LegalMoveGenerator.generateLegalMoves(position).firstOrNull { it.uci == uci }?.let { LegalMoveGenerator.makeMove(position, it) } ?: break
    return position.toFen()
}

private fun sideLabel(side: PieceColor) = if (side == PieceColor.WHITE) "As White" else "As Black"

/** The player's lines, a new one, and the book's openings in Opening Battle. */
@Composable
fun MyLinesScreen(
    lines: List<MyLine>,
    theme: BoardTheme,
    onNew: () -> Unit,
    onOpen: (MyLine) -> Unit,
    onAnyOpening: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtBackBar(onBack = onBack)
            Column(Modifier.padding(horizontal = CtSpacing.gutter), verticalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                Text("Your openings", style = CtType.Display)
                if (lines.isEmpty()) {
                    Text("Build a line move by move, drill it, then play it out against Stockfish.", style = CtType.Body.copy(color = CtColors.Text2))
                }
                Column {
                    lines.forEach { line ->
                        val fen = remember(line.moves) { endFen(line.moves) }
                        Row(
                            Modifier.fillMaxWidth().defaultMinSize(minHeight = 68.dp).ctPressable(onClickLabel = "Open ${line.name}") { onOpen(line) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            BoardThumbnail(fen, theme, flipped = line.side == PieceColor.BLACK)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(line.name, style = CtType.TitleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${sideLabel(line.side)} · ${(line.moves.size + 1) / 2} moves", style = CtType.Caption, maxLines = 1)
                            }
                        }
                    }
                }
                CtButton(text = "New line", onClick = onNew, modifier = Modifier.fillMaxWidth())
                CtListRow(
                    title = "Any opening from the book",
                    icon = null,
                    onClick = onAnyOpening,
                    leading = { CtMetalIcon(Icons.Rounded.AutoStories, size = 36.dp) },
                )
                Spacer(Modifier.height(CtSpacing.xl))
            }
        }
    }
}

/** One of the player's lines: its moves, Drill it, Play it out, and Delete. */
@Composable
fun MyLineScreen(
    line: MyLine,
    theme: BoardTheme,
    onDrill: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleting by rememberSaveable { mutableStateOf(false) }
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtBackBar(onBack = onBack)
            Column(Modifier.padding(horizontal = CtSpacing.gutter), verticalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(line.name, style = CtType.Display)
                    Text(sideLabel(line.side), style = CtType.Caption.copy(color = CtColors.Text2))
                }
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    BoardThumbnail(remember(line.moves) { endFen(line.moves) }, theme, flipped = line.side == PieceColor.BLACK, size = 220.dp)
                }
                Text(remember(line.moves) { sanLine(line.moves) }, style = CtType.Body)
                CtButton(text = "Drill it", onClick = onDrill, modifier = Modifier.fillMaxWidth())
                CtButton(text = "Play it out vs Stockfish", style = CtButtonStyle.Secondary, onClick = onPlay, modifier = Modifier.fillMaxWidth())
                CtButton(text = "Delete", style = CtButtonStyle.Ghost, onClick = { deleting = true }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(CtSpacing.xl))
            }
        }
    }
    if (deleting) {
        CtConfirmDialog(
            title = "Delete ${line.name}?",
            text = "The line and how well you know it are gone for good.",
            confirmLabel = "Delete",
            onConfirm = {
                deleting = false
                onDelete()
            },
            onDismiss = { deleting = false },
        )
    }
}

/**
 * A new line on the board: the player makes both sides' moves, chooses the side they will play,
 * and saves it under the book's name or their own. The board never moves (D39).
 */
@Composable
fun LineBuilderScreen(
    draft: LineDraft?,
    settings: AppSettings,
    onSide: (PieceColor) -> Unit,
    onUndo: () -> Unit,
    onSave: (String) -> Unit,
    onFeedback: (BoardFeedback) -> Unit,
    onBack: () -> Unit,
) {
    if (draft == null) return
    val board by draft.session.state.collectAsState()
    var naming by rememberSaveable { mutableStateOf(false) }
    CtBoardScreen(
        title = "New line",
        onBack = onBack,
        zone = {
            CtSegmented(listOf(PieceColor.WHITE, PieceColor.BLACK), draft.side, ::sideLabel, onSide)
            Text(
                draft.bookName ?: if (draft.moves.isEmpty()) "Play the moves of both sides." else "Your own line",
                style = CtType.Caption.copy(color = CtColors.Text2),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        board = {
            BoardHost(
                session = draft.session,
                theme = settings.boardTheme,
                whiteAtBottom = draft.side == PieceColor.WHITE,
                showCoordinates = settings.showCoordinates,
                showLegalDots = settings.showLegalDots,
                autoQueen = settings.autoQueen,
                onFeedback = onFeedback,
                soundsOtherMoves = true,
            )
        },
        under = {
            BoardMoveList(entries = remember(board.version) { moveListOf(board.start, board.moves) }, currentPly = board.moves.size)
        },
        controls = {
            CtControl(Icons.AutoMirrored.Rounded.Undo, "Undo", enabled = draft.moves.isNotEmpty(), onClick = onUndo)
            CtButton(text = "Save", enabled = draft.moves.isNotEmpty(), onClick = { naming = true }, modifier = Modifier.weight(1f))
        },
    )
    if (naming) {
        var name by rememberSaveable { mutableStateOf(draft.bookName?.let { it.substringAfter(": ", it) } ?: "") }
        AlertDialog(
            onDismissRequest = { naming = false },
            containerColor = CtColors.Raised,
            title = { Text("Name this line", style = CtType.Headline) },
            text = { CtTextField(value = name, onValueChange = { name = it.take(MAX_NAME) }, label = "Name", placeholder = "My line") },
            confirmButton = {
                TextButton(onClick = {
                    naming = false
                    onSave(name)
                }) { Text("Save", style = CtType.Button.copy(color = CtColors.Text)) }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel", style = CtType.Button.copy(color = CtColors.Text)) } },
        )
    }
}

private const val MAX_NAME = 40
