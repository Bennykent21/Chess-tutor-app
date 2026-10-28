package com.chesstutor.app.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min

/** Fixed sizes of the board screen layout (design language section 6). */
object CtBoardScreenSpec {
    val TopBarHeight = 56.dp
    /** Puzzle status, lesson progress, the opponent's strip or a review verdict. Never grows. */
    val StatusRowHeight = 64.dp
    /** Room always kept below the board on short or wide screens: the player strip and the controls slot. */
    val MinBelow = 160.dp
    /** The bottom slot for a screen's buttons (Undo, Hint, Resign...). Always shown in full. */
    val ControlsHeight = 72.dp
    val MinBoard = 200.dp

    /** The board's side: the full width, unless the screen is too short to leave [MinBelow] under it. */
    fun boardSize(maxWidth: Dp, maxHeight: Dp): Dp =
        max(MinBoard, min(maxWidth, maxHeight - TopBarHeight - StatusRowHeight - MinBelow))
}

/** Test tags, so tests can check where the board sits (D39). */
object CtBoardScreenTags {
    const val STATUS = "ct-board-status"
    const val BOARD = "ct-board"
    const val CONTROLS = "ct-board-controls"
}

/**
 * The one layout of every board screen: games, puzzles, lessons, drills, review and analysis
 * (D39, D41). From the top: a top bar, a fixed 64 dp [status] row, the square [board],
 * everything [below] it (player strip, move list, coach or bot bubble) and the [controls] in a
 * fixed slot at the bottom. Nothing above the board can change height, so the board never moves
 * while it is in use, and it sits at the same height on every board screen. Long content below
 * the board is clipped to its area (make it scroll there) and can never push the controls off
 * the screen. Sheets and passing messages go in [overlay], which covers instead of pushing.
 */
@Composable
fun CtBoardScreen(
    title: String,
    onBack: (() -> Unit)?,
    board: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable (RowScope.() -> Unit)? = null,
    status: @Composable BoxScope.() -> Unit = {},
    controls: (@Composable RowScope.() -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    below: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(CtColors.Bg)) {
        // The same size on every board screen, with or without controls, so all boards line up.
        val side = CtBoardScreenSpec.boardSize(maxWidth, maxHeight)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(CtBoardScreenSpec.TopBarHeight), contentAlignment = Alignment.CenterStart) {
                CtTopBar(title = title, onBack = onBack, trailing = actions)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CtBoardScreenSpec.StatusRowHeight)
                    .padding(horizontal = CtSpacing.gutter)
                    .testTag(CtBoardScreenTags.STATUS),
                contentAlignment = Alignment.CenterStart,
                content = status,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(side)
                    .testTag(CtBoardScreenTags.BOARD),
                content = board,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
                    .padding(horizontal = CtSpacing.gutter),
                content = below,
            )
            if (controls != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CtBoardScreenSpec.ControlsHeight)
                        .testTag(CtBoardScreenTags.CONTROLS)
                        .padding(horizontal = CtSpacing.gutter, vertical = CtSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = controls,
                )
            }
        }
        overlay()
    }
}
