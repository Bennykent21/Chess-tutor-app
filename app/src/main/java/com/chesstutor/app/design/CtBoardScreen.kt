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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min

/** Fixed sizes of the board screen layout (mock-up round 18). */
object CtBoardScreenSpec {
    val TopBarHeight = 56.dp
    /** The least room kept above the board for the coach or the bot, on short phones. */
    val MinZone = 120.dp
    /** The least height of the row under the board: the move list, a player or a progress bar. */
    val UnderHeight = 48.dp
    /** The bottom slot for a screen's buttons (Undo, Hint, Resign...). Always shown in full. */
    val ControlsHeight = 72.dp
    val MinBoard = 200.dp

    /** The board's side: the full width, unless the screen is too short to leave [MinZone] above it. */
    fun boardSize(maxWidth: Dp, maxHeight: Dp): Dp =
        max(MinBoard, min(maxWidth, maxHeight - TopBarHeight - MinZone - UnderHeight - ControlsHeight))

    /**
     * The height left over goes above the board, less one square: the board sits a square above
     * the lowest it could, as far as that leaves [MinZone]. The row under it shares that square.
     */
    fun zoneHeight(maxHeight: Dp, board: Dp): Dp {
        val spare = max(0.dp, maxHeight - TopBarHeight - board - UnderHeight - ControlsHeight)
        return max(spare - board / 8, min(spare, MinZone))
    }
}

/** Test tags, so tests can check where the board sits (D39). */
object CtBoardScreenTags {
    const val ZONE = "ct-board-zone"
    const val BOARD = "ct-board"
    const val UNDER = "ct-board-under"
    const val CONTROLS = "ct-board-controls"
}

/**
 * The one layout of every board screen: games, puzzles, lessons, drills, review and analysis
 * (D39, D49). From the top: a top bar, the [zone] where the coach or the bot talks, the square
 * [board], the row [under] it and the [controls] in a fixed slot at the bottom. The board sits one
 * square above the lowest those two rows allow and the zone takes the rest, so it is at the same
 * height on every board screen of a phone and nothing above it can move it. The zone stacks up from the
 * board: long text fills it upward and scrolls inside (see [CtTutor]), and anything that spills
 * out of it covers the top bar but never the board, which is drawn after it. Sheets and passing
 * messages go in [overlay], which covers instead of pushing.
 */
@Composable
fun CtBoardScreen(
    title: String,
    onBack: (() -> Unit)?,
    board: @Composable BoxScope.() -> Unit,
    zone: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable (RowScope.() -> Unit)? = null,
    under: @Composable BoxScope.() -> Unit = {},
    controls: (@Composable RowScope.() -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxSize().background(CtColors.Bg)) {
        // The same sizes on every board screen, with or without controls, so all boards line up.
        val side = CtBoardScreenSpec.boardSize(maxWidth, maxHeight)
        val zoneHeight = CtBoardScreenSpec.zoneHeight(maxHeight, side)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(CtBoardScreenSpec.TopBarHeight), contentAlignment = Alignment.CenterStart) {
                CtTopBar(title = title, onBack = onBack, trailing = actions)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(zoneHeight)
                    .padding(start = CtSpacing.lg, end = CtSpacing.lg, bottom = CtSpacing.sm)
                    .testTag(CtBoardScreenTags.ZONE),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.sm, Alignment.Bottom),
                content = zone,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(side)
                    .testTag(CtBoardScreenTags.BOARD),
                content = board,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = CtSpacing.lg)
                    .testTag(CtBoardScreenTags.UNDER),
                contentAlignment = Alignment.Center,
                content = under,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CtBoardScreenSpec.ControlsHeight)
                    .testTag(CtBoardScreenTags.CONTROLS)
                    .padding(horizontal = CtSpacing.gutter, vertical = CtSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = { controls?.invoke(this) },
            )
        }
        overlay()
    }
}
