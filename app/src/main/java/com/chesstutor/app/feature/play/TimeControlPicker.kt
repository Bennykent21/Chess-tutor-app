package com.chesstutor.app.feature.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtMotion
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSliderRow
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.LocalReduceMotion
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.game.PlayerClock
import com.chesstutor.app.domain.game.TimeControl
import kotlin.math.roundToInt

/** Keeps a [TimeControl] across rotation and process death. */
val TimeControlSaver = Saver<TimeControl, String>(save = { it.encode() }, restore = { TimeControl.decode(it) })

/** A friend game's clock as a [TimeControl]: none, or the same start and increment. */
fun PlayerClock?.asTimeControl(): TimeControl = this?.let { TimeControl(it.initialMs, it.incrementMs) } ?: TimeControl.NONE

/** The [PlayerClock] for a [TimeControl]; null plays without one. */
val TimeControl.playerClock: PlayerClock? get() = initialMs?.let { PlayerClock(it, incrementMs) }

/**
 * The one clock picker (D92) for bot games, custom games and friend games: closed, a row with
 * the choice; open, bullet, blitz and rapid, no clock, and a custom clock with two sliders.
 */
@Composable
fun TimeControlPicker(
    selected: TimeControl,
    onSelect: (TimeControl) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Time control",
    allowNone: Boolean = true,
) {
    val reduceMotion = LocalReduceMotion.current
    var open by rememberSaveable { mutableStateOf(false) }
    val custom = selected != TimeControl.NONE && selected !in TimeControl.PRESETS
    var customOpen by rememberSaveable { mutableStateOf(custom) }
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(if (reduceMotion) 0 else CtMotion.OVERLAY_MS), label = "pickerChevron")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(CtShapes.Control)
                .background(CtColors.Surface)
                .ctPressable(onClickLabel = if (open) "Close the clocks" else "Choose a clock") { open = !open }
                .semantics { stateDescription = selected.label }
                .padding(horizontal = CtSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = CtType.TitleSmall, modifier = Modifier.weight(1f))
            Text(selected.label, style = CtType.Body.copy(color = CtColors.Text2))
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = CtColors.Text2, modifier = Modifier.padding(start = CtSpacing.xs).size(20.dp).rotate(turn))
        }
        AnimatedVisibility(
            visible = open,
            enter = if (reduceMotion) fadeIn(tween(0)) else expandVertically(tween(CtMotion.OVERLAY_MS, easing = CtMotion.OverlayEase)) + fadeIn(),
            exit = if (reduceMotion) fadeOut(tween(0)) else shrinkVertically(tween(CtMotion.OVERLAY_MS, easing = CtMotion.OverlayEase)) + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                TimeControl.GROUPS.forEach { (kind, clocks) ->
                    Group(kind) {
                        clocks.forEach { clock ->
                            Tile(clock.label, selected == clock && !customOpen) {
                                customOpen = false
                                onSelect(clock)
                                open = false
                            }
                        }
                    }
                }
                Group("Other") {
                    if (allowNone) {
                        Tile("No clock", selected == TimeControl.NONE && !customOpen) {
                            customOpen = false
                            onSelect(TimeControl.NONE)
                            open = false
                        }
                    }
                    Tile("Custom", customOpen || custom) {
                        customOpen = true
                        if (!custom) onSelect(selected.takeIf { it != TimeControl.NONE } ?: TimeControl.RAPID_10)
                    }
                    if (allowNone) Spacer(Modifier.weight(1f)) else Spacer(Modifier.weight(2f, fill = true))
                }
                if (customOpen || custom) {
                    val minutes = ((selected.initialMs ?: 600_000L) / 60_000f).coerceIn(1f, MAX_MINUTES)
                    val added = (selected.incrementMs / 1000f).coerceIn(0f, MAX_ADDED)
                    CtSliderRow(
                        title = "Minutes",
                        value = minutes,
                        onValueChange = { onSelect(TimeControl.of(it.roundToInt(), added.roundToInt())) },
                        valueRange = 1f..MAX_MINUTES,
                        steps = (MAX_MINUTES - 2).toInt(),
                        valueLabel = "${minutes.roundToInt()}",
                    )
                    CtSliderRow(
                        title = "Added per move",
                        value = added,
                        onValueChange = { onSelect(TimeControl.of(minutes.roundToInt(), it.roundToInt())) },
                        valueRange = 0f..MAX_ADDED,
                        steps = (MAX_ADDED - 1).toInt(),
                        valueLabel = "${added.roundToInt()} s",
                    )
                }
            }
        }
    }
}

private const val MAX_MINUTES = 90f
private const val MAX_ADDED = 60f

@Composable
private fun Group(title: String, tiles: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        Text(title, style = CtType.Label.copy(color = CtColors.Text2))
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), content = tiles)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Tile(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .clip(CtShapes.Chip)
            .background(if (on) CtColors.Primary else CtColors.Surface)
            .selectable(selected = on, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = CtType.Label.copy(color = if (on) CtColors.OnPrimary else CtColors.Text), maxLines = 1)
    }
}
