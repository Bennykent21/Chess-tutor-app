package com.chesstutor.app.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.Square

/**
 * Looking back through a game or a puzzle (Play and the puzzle player): the move looked at, or
 * the live board when [shownPly] is null. Nothing is undone.
 */
@Stable
class LookBack internal constructor(
    private val viewed: MutableState<Int?>,
    private val start: Position,
    private val moves: List<Move>,
) {
    private val positions: List<Position> by lazy { moves.runningFold(start, LegalMoveGenerator::makeMove) }

    val latestPly: Int get() = moves.size

    /** The move looked at, or null on the live board. */
    val shownPly: Int? get() = viewed.value?.takeIf { it < latestPly }

    /** The move the strip marks. */
    val ply: Int get() = shownPly ?: latestPly

    val canGoBack: Boolean get() = ply > 0

    /** Shows the position after [target] moves; the latest one is the live board. */
    fun show(target: Int) {
        viewed.value = target.takeIf { it < latestPly }
    }

    fun back() = show(ply - 1)

    fun forward() = show(ply + 1)

    fun live() {
        viewed.value = null
    }

    /** The position looked at, or null on the live board. */
    internal val past: Position? get() = shownPly?.let(positions::getOrNull)

    /** The move that led to [past], marked on the board. */
    internal val pastMove: Pair<Square, Square>? get() = shownPly?.let { moves.getOrNull(it - 1) }?.let { it.from to it.to }
}

/** A [LookBack] over [state]'s moves; it goes back to the live board when [key] changes. */
@Composable
fun rememberLookBack(state: BoardSessionState, key: Any?): LookBack {
    val viewed = rememberSaveable(key) { mutableStateOf<Int?>(null) }
    return remember(viewed, state.start, state.moves) { LookBack(viewed, state.start, state.moves) }
}

/** The position [look] is on, still, and a tap goes back to [live]; [backLabel] says so to screen readers. */
@Composable
fun LookBackBoard(
    look: LookBack,
    theme: BoardTheme,
    whiteAtBottom: Boolean,
    showCoordinates: Boolean,
    backLabel: String,
    modifier: Modifier = Modifier,
    live: @Composable () -> Unit,
) {
    val past = look.past ?: return live()
    BoardView(
        position = past,
        theme = theme,
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClickLabel = backLabel,
        ) { look.live() },
        whiteAtBottom = whiteAtBottom,
        showCoordinates = showCoordinates,
        showLegalDots = false,
        marks = BoardMarks(lastMove = look.pastMove),
    )
}
