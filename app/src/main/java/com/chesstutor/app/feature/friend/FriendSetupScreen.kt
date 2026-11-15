package com.chesstutor.app.feature.friend

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.board.BoardView
import com.chesstutor.app.board.rememberPiecePainters
import com.chesstutor.app.core.Chess960
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.design.CtActionPage
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtGroupLabel
import com.chesstutor.app.design.CtIconButton
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtSegmented
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSheet
import com.chesstutor.app.design.CtSliderRow
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtToggleRow
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.game.PlayerClock
import com.chesstutor.app.domain.game.Seating
import kotlin.math.roundToInt

/** Whose clock the custom-clock sheet is setting. */
private enum class ClockOwner { BOTH, FRIEND }

/**
 * Play a friend (Phase 11): on this phone or with a friend's phone nearby, who plays which
 * colour, a clock (the same for both, or one each), where the game starts, and on one phone how
 * the two sit, with Start (or Join and Host, nearby) fixed at the bottom.
 */
@Composable
fun FriendSetupScreen(
    draft: FriendDraft,
    ownerName: String,
    theme: BoardTheme,
    onChange: ((FriendDraft) -> FriendDraft) -> Unit,
    onPickOpening: () -> Unit,
    onSetPosition: () -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    onHost: () -> Unit = {},
    onJoin: () -> Unit = {},
) {
    var customFor by rememberSaveable { mutableStateOf<ClockOwner?>(null) }
    val nearby = draft.mode == FriendMode.NEARBY
    val friend = if (nearby) "your friend" else draft.friendName.trim().ifEmpty { "your friend" }

    CtActionPage(
        title = "",
        onBack = onBack,
        bottom = {
            if (nearby) {
                CtButton(text = "Join", style = CtButtonStyle.Secondary, onClick = onJoin, modifier = Modifier.weight(1f))
                CtButton(text = "Host", enabled = draft.problem == null, onClick = onHost, modifier = Modifier.weight(1f))
            } else {
                CtButton(text = "Start", enabled = draft.problem == null, onClick = onStart, modifier = Modifier.weight(1f))
            }
        },
    ) {
        Text("Play a friend", style = CtType.Display)
        CtSegmented(
            options = FriendMode.entries,
            selected = draft.mode,
            label = { if (it == FriendMode.THIS_PHONE) "This phone" else "Nearby" },
            onSelect = { mode -> onChange { it.copy(mode = mode) } },
            modifier = Modifier.padding(top = CtSpacing.md),
        )

        Row(
            modifier = Modifier.padding(top = CtSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CtSpacing.xs),
        ) {
            Seat(PieceColor.WHITE, draft, ownerName, theme, nearby) { name -> onChange { it.copy(friendName = name) } }
            CtIconButton(
                icon = Icons.Rounded.SwapHoriz,
                contentDescription = "Swap colours",
                onClick = { onChange { it.copy(ownerColor = it.ownerColor.opposite()) } },
            )
            Seat(PieceColor.BLACK, draft, ownerName, theme, nearby) { name -> onChange { it.copy(friendName = name) } }
        }

        CtGroupLabel("Clock")
        ClockChips(
            selected = draft.clock,
            allowNone = true,
            onPick = { clock -> onChange { it.copy(clock = clock, friendClock = it.friendClock.takeIf { clock != null }) } },
            onCustom = { customFor = ClockOwner.BOTH },
        )
        if (draft.clock != null) {
            CtToggleRow(
                title = "A different clock for $friend",
                checked = draft.friendClock != null,
                onCheckedChange = { on -> onChange { it.copy(friendClock = if (on) it.clock else null) } },
            )
            if (draft.friendClock != null) {
                ClockChips(
                    selected = draft.friendClock,
                    allowNone = false,
                    onPick = { clock -> onChange { it.copy(friendClock = clock) } },
                    onCustom = { customFor = ClockOwner.FRIEND },
                )
            }
        }

        CtGroupLabel("Start from")
        CtSegmented(
            options = FriendStart.entries,
            selected = draft.start,
            label = {
                when (it) {
                    FriendStart.NORMAL -> "Normal"
                    FriendStart.OPENING -> "Opening"
                    FriendStart.POSITION -> "Position"
                    FriendStart.CHESS960 -> "960"
                }
            },
            onSelect = { start -> onChange { it.copy(start = start) } },
        )
        when (draft.start) {
            FriendStart.NORMAL -> Unit
            FriendStart.CHESS960 -> CtListRow(
                title = "Chess960 · ${draft.chess960}",
                subtitle = "Tap for another. Castling still ends on g or c.",
                icon = null,
                onClick = { onChange { it.copy(chess960 = Chess960.random()) } },
                leading = {
                    val position = remember(draft.chess960) { Position.fromFen(Chess960.startFen(draft.chess960)) }
                    Box(Modifier.size(44.dp).clip(CtShapes.Chip)) { BoardView(position, theme, showCoordinates = false, showLegalDots = false) }
                },
            )
            FriendStart.OPENING -> CtListRow(title = draft.opening?.name ?: "Choose an opening", icon = null, onClick = onPickOpening)
            FriendStart.POSITION -> CtListRow(
                title = "Set up the position",
                icon = null,
                onClick = onSetPosition,
                subtitle = draft.board.problem(),
                leading = {
                    val position = remember(draft.board) { Position.tryFromFen(draft.board.toFen()).getOrNull() }
                    Box(Modifier.size(44.dp).clip(CtShapes.Chip)) {
                        if (position != null) BoardView(position, theme, showCoordinates = false, showLegalDots = false)
                    }
                },
            )
        }

        // Undo and seating only mean something at one phone.
        if (!nearby) {
            Column(Modifier.padding(top = CtSpacing.sm)) {
                CtToggleRow(title = "Undo", checked = draft.takebacks, onCheckedChange = { on -> onChange { it.copy(takebacks = on) } })
                CtToggleRow(
                    title = "Face to face",
                    checked = draft.seating == Seating.FACE_TO_FACE,
                    onCheckedChange = { on -> onChange { it.copy(seating = if (on) Seating.FACE_TO_FACE else Seating.SIDE_BY_SIDE) } },
                )
            }
        }
    }

    CustomClockSheet(
        visible = customFor != null,
        start = (if (customFor == ClockOwner.FRIEND) draft.friendClock else draft.clock) ?: PlayerClock.of(10),
        onDismiss = { customFor = null },
        onUse = { clock ->
            val owner = customFor
            customFor = null
            onChange { if (owner == ClockOwner.FRIEND) it.copy(friendClock = clock) else it.copy(clock = clock) }
        },
    )
}

/** One colour's seat: its king, and the owner's name or a field for the friend's. */
@Composable
private fun RowScope.Seat(color: PieceColor, draft: FriendDraft, ownerName: String, theme: BoardTheme, nearby: Boolean, onFriendName: (String) -> Unit) {
    val king = rememberPiecePainters(theme.pieceSet)[Piece(PieceType.KING, color)]
    Row(
        modifier = Modifier
            .weight(1f)
            .height(52.dp)
            .clip(CtShapes.Control)
            .background(CtColors.Surface)
            .padding(horizontal = CtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.xs),
    ) {
        if (king != null) Image(king, contentDescription = if (color == PieceColor.WHITE) "White" else "Black", modifier = Modifier.size(30.dp))
        if (color == draft.ownerColor) {
            Text(ownerName.trim().ifEmpty { "You" }, style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else if (nearby) {
            // The friend's own phone sends their name.
            Text("Friend", style = CtType.TitleSmall.copy(color = CtColors.Text2), maxLines = 1)
        } else {
            BasicTextField(
                value = draft.friendName,
                onValueChange = { onFriendName(it.take(MAX_NAME)) },
                singleLine = true,
                textStyle = CtType.TitleSmall,
                cursorBrush = SolidColor(CtColors.Text),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.weight(1f).semantics { contentDescription = "Friend's name" },
                decorationBox = { field ->
                    if (draft.friendName.isEmpty()) Text("Friend", style = CtType.TitleSmall.copy(color = CtColors.Text2), maxLines = 1)
                    field()
                },
            )
        }
    }
}

/** No clock, the quick clocks, and Custom, which shows the custom clock once one is set. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClockChips(selected: PlayerClock?, allowNone: Boolean, onPick: (PlayerClock?) -> Unit, onCustom: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        if (allowNone) CtChip(text = "None", selected = selected == null, onClick = { onPick(null) })
        PlayerClock.PRESETS.forEach { clock ->
            CtChip(text = clock.label, selected = selected == clock, onClick = { onPick(clock) })
        }
        val custom = selected != null && selected !in PlayerClock.PRESETS
        CtChip(text = if (custom) selected.label else "Custom", selected = custom, onClick = onCustom)
    }
}

/** Minutes to start with and seconds added per move. */
@Composable
private fun CustomClockSheet(visible: Boolean, start: PlayerClock, onDismiss: () -> Unit, onUse: (PlayerClock) -> Unit) {
    var minutes by remember(visible) { mutableFloatStateOf((start.initialMs / 60_000f).coerceIn(1f, MAX_MINUTES)) }
    var added by remember(visible) { mutableFloatStateOf((start.incrementMs / 1000f).coerceIn(0f, MAX_ADDED)) }
    CtSheet(visible = visible, onDismiss = onDismiss) {
        CtSliderRow(
            title = "Minutes",
            value = minutes,
            onValueChange = { minutes = it.roundToInt().toFloat() },
            valueRange = 1f..MAX_MINUTES,
            valueLabel = "${minutes.roundToInt()}",
        )
        CtSliderRow(
            title = "Seconds added each move",
            value = added,
            onValueChange = { added = it.roundToInt().toFloat() },
            valueRange = 0f..MAX_ADDED,
            valueLabel = "${added.roundToInt()}",
        )
        CtButton(
            text = "Use this clock",
            onClick = { onUse(PlayerClock.of(minutes.roundToInt(), added.roundToInt())) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val MAX_NAME = 20
private const val MAX_MINUTES = 90f
private const val MAX_ADDED = 30f
