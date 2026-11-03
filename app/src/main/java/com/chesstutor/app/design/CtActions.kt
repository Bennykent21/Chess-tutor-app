package com.chesstutor.app.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * An icon over a short word, in the row of buttons under a board. [description] is what a screen
 * reader says, when the word alone would not be enough.
 */
@Composable
fun RowScope.CtControl(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    description: String = label,
) {
    val tint = if (enabled) CtColors.Text else CtColors.Text2.copy(alpha = 0.5f)
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(CtShapes.Control)
            .ctPressable(enabled = enabled, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CtSpacing.xs, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, style = CtType.NavLabel.copy(color = tint), maxLines = 1)
    }
}

enum class CtButtonStyle { Primary, Secondary, Ghost }

/** Full-width style button. Primary is the one main action of a screen ("Play", "Solve"). */
@Composable
fun CtButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CtButtonStyle = CtButtonStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val background = when {
        style == CtButtonStyle.Ghost -> Color.Transparent
        !enabled -> CtColors.Track
        style == CtButtonStyle.Primary -> CtColors.Primary
        else -> CtColors.Surface
    }
    val contentColor = when {
        !enabled -> CtColors.Text2
        style == CtButtonStyle.Primary -> CtColors.OnPrimary
        style == CtButtonStyle.Secondary -> CtColors.Text
        else -> CtColors.Accent
    }
    val shape = if (style == CtButtonStyle.Primary) CtShapes.Button else CtShapes.Control
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = if (style == CtButtonStyle.Primary) 56.dp else 52.dp)
            .clip(shape)
            .background(background)
            .ctPressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = CtSpacing.lg + CtSpacing.xs, vertical = CtSpacing.md),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(CtSpacing.sm))
        }
        Text(text = text, style = CtType.Button.copy(color = contentColor), maxLines = 1)
    }
}

/**
 * A square button that shows only an icon, beside a wider main button (Analyse next to
 * "Next puzzle"). [contentDescription] is what screen readers say.
 */
@Composable
fun CtIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CtShapes.Control)
            .background(if (enabled) CtColors.Surface else CtColors.Track)
            .ctPressable(enabled = enabled, onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) CtColors.Text else CtColors.Text2, modifier = Modifier.size(22.dp))
    }
}

/**
 * Small rounded label, optionally selectable (filters) or clickable. Neutral by default; a
 * success or error [color] marks a state (solved, failed). Selected chips fill.
 */
@Composable
fun CtChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CtColors.Accent,
    selected: Boolean = false,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val neutral = color == CtColors.Accent
    val background = when {
        selected && neutral -> CtColors.Primary
        selected -> color
        else -> CtColors.Surface
    }
    val contentColor = when {
        selected && neutral -> CtColors.OnPrimary
        selected -> CtColors.Bg
        neutral -> CtColors.Text
        else -> color
    }
    val base = modifier
        .then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier)
        .clip(CtShapes.Pill)
        .background(background)
    Row(
        modifier = (if (onClick != null) base.ctPressable(onClick = onClick) else base)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(CtSpacing.xs))
        }
        Text(text = text, style = CtType.Label.copy(color = contentColor), maxLines = 1)
    }
}

/** A filter that opens a choice, such as "Level: All" on the Openings page. */
@Composable
fun CtDropdownChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(CtShapes.Pill)
            .background(CtColors.Surface)
            .ctPressable(onClick = onClick)
            .padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.xs),
    ) {
        Text(text = text, style = CtType.Label, maxLines = 1)
        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = CtColors.Text2, modifier = Modifier.size(18.dp))
    }
}

/** Status badge, for example "Lite engine". */
@Composable
fun CtBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CtColors.Coral,
    contentColor: Color = CtColors.OnCoral,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(CtShapes.Pill)
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(CtSpacing.xs))
        }
        Text(text = text, style = CtType.Caption.copy(color = contentColor), maxLines = 1)
    }
}

/** "Coming in Phase N" marker for features that are planned but not built. */
@Composable
fun CtComingSoon(phase: Int, modifier: Modifier = Modifier) {
    CtChip(text = "Coming in Phase $phase", icon = Icons.Rounded.Lock, modifier = modifier)
}

/**
 * One choice out of a few, side by side (System, Light, Dark). Read as radio buttons.
 * The selected segment is raised; colours fade over 240 ms.
 */
@Composable
fun <T> CtSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CtSpacing.touchTarget)
            .clip(CtShapes.Control)
            .background(CtColors.Surface)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            val fill by animateColorAsState(
                targetValue = if (on) CtColors.Raised else CtColors.Surface,
                animationSpec = tween(if (reduceMotion) 0 else CtMotion.THEME_MS, easing = CtMotion.EaseOut),
                label = "segment",
            )
            val lift = when {
                !on -> Modifier
                else -> Modifier.shadow(1.dp, CtShapes.Chip, ambientColor = CtColors.Text.copy(alpha = 0.08f), spotColor = CtColors.Text.copy(alpha = 0.16f))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(lift)
                    .clip(CtShapes.Chip)
                    .background(fill)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(option) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = label(option), style = CtType.Label.copy(color = if (on) CtColors.Text else CtColors.Text2), maxLines = 1)
            }
        }
    }
}

/** The switch drawing (no input): the row around it toggles, so the target is large. */
@Composable
fun CtSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val x by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = tween(if (reduceMotion) 0 else CtMotion.SWITCH_MS, easing = CtMotion.EaseOut),
        label = "switchKnob",
    )
    val track by animateColorAsState(
        targetValue = if (checked) CtColors.Primary else CtColors.SwitchOff,
        animationSpec = tween(if (reduceMotion) 0 else CtMotion.SWITCH_MS, easing = CtMotion.EaseOut),
        label = "switchTrack",
    )
    Box(modifier.size(width = 48.dp, height = 28.dp).clip(CtShapes.Pill).background(track)) {
        Box(
            Modifier
                .padding(3.dp)
                .offset { IntOffset(x.roundToPx(), 0) }
                .size(22.dp)
                .shadow(1.dp, CtShapes.Pill)
                .clip(CtShapes.Pill)
                .background(if (checked) CtColors.OnPrimary else KNOB_OFF),
        )
    }
}

private val KNOB_OFF = Color(0xFFFFFFFF)

/**
 * A theme to pick in Settings, drawn in its own colours: a corner of its board, a line of
 * text, a surface and its accent. The selected card has an accent ring and a tick.
 */
@Composable
fun CtThemeCard(
    theme: AppTheme,
    dark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = Palette.of(theme, dark)
    val reduceMotion = LocalReduceMotion.current
    val ring by animateColorAsState(
        targetValue = if (selected) CtColors.Accent else CtColors.Line,
        animationSpec = tween(if (reduceMotion) 0 else CtMotion.THEME_MS, easing = CtMotion.EaseOut),
        label = "themeRing",
    )
    Column(
        modifier = modifier
            .clip(CtShapes.CardSmall)
            .background(CtColors.Raised)
            .border(2.dp, ring, CtShapes.CardSmall)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(CtShapes.Chip)
                .background(Color(p.bg))
                .padding(CtSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
        ) {
            Column(Modifier.size(48.dp).clip(RoundedCornerShape(4.dp))) {
                Row(Modifier.weight(1f)) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color(p.boardLight)))
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color(p.boardDark)))
                }
                Row(Modifier.weight(1f)) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color(p.boardDark)))
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color(p.boardLight)))
                }
            }
            Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth(0.8f).height(8.dp).clip(CtShapes.Pill).background(Color(p.text)))
                Box(Modifier.fillMaxWidth(0.55f).height(8.dp).clip(CtShapes.Pill).background(Color(p.surface)))
                Box(Modifier.padding(top = 2.dp).fillMaxWidth(0.6f).height(14.dp).clip(CtShapes.Pill).background(Color(p.accent)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = theme.label, style = CtType.Label, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.size(20.dp).clip(CtShapes.Pill).background(if (selected) CtColors.Accent else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = CtColors.Bg, modifier = Modifier.size(14.dp))
            }
        }
    }
}
