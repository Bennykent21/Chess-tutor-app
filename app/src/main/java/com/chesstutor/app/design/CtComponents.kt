package com.chesstutor.app.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.School
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chesstutor.app.navigation.TopTab

// ------------------------------------------------------------------ surfaces

/** Full-screen theme background used behind every screen. */
@Composable
fun CtScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CtGradients.Screen),
        content = content,
    )
}

/** Rounded card. Clickable when [onClick] is set; [background] can be a gradient. */
@Composable
fun CtCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    background: Brush = CtGradients.Card,
    contentPadding: PaddingValues = PaddingValues(CtSpacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .clip(CtShapes.Card)
        .background(background)
        .border(1.dp, CtColors.Line.copy(alpha = 0.6f), CtShapes.Card)
    val clickable = if (onClick != null) base.ctPressable(onClickLabel = onClickLabel, onClick = onClick) else base
    Column(modifier = clickable.padding(contentPadding), content = content)
}

/** Circle holding an icon, used on cards and rows. */
@Composable
fun CtIconBubble(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    background: Color = tint.copy(alpha = 0.16f),
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CtShapes.Pill)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

// ------------------------------------------------------------------ text blocks

@Composable
fun CtTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CtSpacing.gutter, vertical = CtSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(CtSpacing.touchTarget)
                    .clip(CtShapes.Pill)
                    .background(CtColors.Surface)
                    .ctPressable(onClickLabel = "Go back", onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = CtColors.Text)
            }
            Spacer(Modifier.width(CtSpacing.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = CtType.Headline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(text = subtitle, style = CtType.BodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

@Composable
fun CtSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = CtSpacing.sm, bottom = CtSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = CtType.Title,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = CtType.Label.copy(color = CtColors.Teal),
                modifier = Modifier
                    .clip(CtShapes.Chip)
                    .ctPressable(onClick = onAction)
                    .padding(horizontal = CtSpacing.sm, vertical = CtSpacing.sm),
            )
        }
    }
}

// ------------------------------------------------------------------ actions

enum class CtButtonStyle { Primary, Secondary, Ghost }

/** Big pill button. Primary is the one main action of a screen ("Play", "Solve"). */
@Composable
fun CtButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CtButtonStyle = CtButtonStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val background: Brush = when (style) {
        CtButtonStyle.Primary -> SolidColor(if (enabled) CtColors.Primary else CtColors.Track)
        CtButtonStyle.Secondary -> SolidColor(CtColors.SurfaceRaised)
        CtButtonStyle.Ghost -> SolidColor(Color.Transparent)
    }
    val contentColor = when {
        !enabled -> CtColors.Text2
        style == CtButtonStyle.Primary -> CtColors.OnPrimary
        style == CtButtonStyle.Secondary -> CtColors.Text
        else -> CtColors.Teal
    }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .clip(CtShapes.Pill)
            .background(background)
            .then(
                if (style == CtButtonStyle.Secondary) Modifier.border(1.dp, CtColors.Line, CtShapes.Pill) else Modifier
            )
            .ctPressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = CtSpacing.xl, vertical = CtSpacing.md),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(CtSpacing.sm))
        }
        Text(text = text, style = CtType.Button.copy(color = contentColor), maxLines = 1)
    }
}

/** Small rounded label, optionally selectable (filters) or clickable. */
@Composable
fun CtChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CtColors.Teal,
    selected: Boolean = false,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val background = if (selected) color else color.copy(alpha = 0.14f)
    val contentColor = if (selected) CtColors.BgDeep else color
    val base = modifier
        .clip(CtShapes.Chip)
        .background(background)
    Row(
        modifier = (if (onClick != null) base.ctPressable(onClick = onClick) else base)
            .padding(horizontal = CtSpacing.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(CtSpacing.xs))
        }
        Text(text = text, style = CtType.Caption.copy(color = contentColor), maxLines = 1)
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
    CtChip(text = "Coming in Phase $phase", color = CtColors.Violet, icon = Icons.Rounded.Lock, modifier = modifier)
}

// ------------------------------------------------------------------ data display

/** A number with a label, for XP, streaks and ratings. */
@Composable
fun CtStatTile(
    value: String,
    label: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CtShapes.CardSmall)
            .background(CtColors.Surface)
            .padding(horizontal = CtSpacing.md, vertical = CtSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CtIconBubble(icon = icon, tint = accent, size = 36.dp)
        Spacer(Modifier.width(CtSpacing.sm))
        Column {
            Text(text = value, style = CtType.Number, maxLines = 1)
            Text(text = label, style = CtType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Rounded progress bar that fills smoothly (instantly with "Reduce motion" on). */
@Composable
fun CtProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = CtColors.Teal,
    brush: Brush? = null,
    height: Dp = 10.dp,
    trackColor: Color = CtColors.SurfaceHigh,
) {
    val reduceMotion = LocalReduceMotion.current
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 600),
        label = "ctProgress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CtShapes.Pill)
            .background(trackColor),
    ) {
        if (animated > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(CtShapes.Pill)
                    .background(brush ?: SolidColor(color)),
            )
        }
    }
}

/** A tappable row with an icon, title, optional subtitle and a chevron. */
@Composable
fun CtListRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconTint: Color = CtColors.Teal,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CtShapes.CardSmall)
            .background(CtColors.Surface)
            .ctPressable(onClick = onClick)
            .padding(horizontal = CtSpacing.md, vertical = CtSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CtIconBubble(icon = icon, tint = iconTint, size = 40.dp)
        Spacer(Modifier.width(CtSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(text = subtitle, style = CtType.BodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            trailing()
            Spacer(Modifier.width(CtSpacing.xs))
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = CtColors.Text3)
    }
}

/**
 * A setting with an on/off switch. The whole row toggles and reads as one switch to
 * screen readers, so the target is large.
 */
@Composable
fun CtToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CtShapes.CardSmall)
            .background(CtColors.Surface)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = CtSpacing.md, vertical = CtSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = CtSpacing.md)) {
            Text(text = title, style = CtType.TitleSmall)
            if (subtitle != null) Text(text = subtitle, style = CtType.BodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CtColors.OnGold,
                checkedTrackColor = CtColors.Gold,
                checkedBorderColor = CtColors.Gold,
                uncheckedThumbColor = CtColors.Text2,
                uncheckedTrackColor = CtColors.SurfaceHigh,
                uncheckedBorderColor = CtColors.Line,
            ),
        )
    }
}

/** Asks before something that cannot be undone, such as deleting. [confirmLabel] names the action. */
@Composable
fun CtConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CtColors.SurfaceRaised,
        title = { Text(text = title, style = CtType.Title) },
        text = { Text(text = text, style = CtType.Body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = confirmLabel, style = CtType.Button.copy(color = CtColors.Coral)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Cancel", style = CtType.Button.copy(color = CtColors.Text)) }
        },
    )
}

/** A one-line text input with its label above, in the card style. */
@Composable
fun CtTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = CtType.Label)
        Spacer(Modifier.height(CtSpacing.xs))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = CtType.Body.copy(color = CtColors.Text),
            cursorBrush = SolidColor(CtColors.Gold),
            modifier = Modifier
                .fillMaxWidth()
                .clip(CtShapes.CardSmall)
                .background(CtColors.Surface)
                .border(1.dp, CtColors.Line, CtShapes.CardSmall)
                .padding(horizontal = CtSpacing.md, vertical = CtSpacing.md)
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder != null) Text(text = placeholder, style = CtType.Body.copy(color = CtColors.Text3))
                    inner()
                }
            },
        )
    }
}

/** A labelled slider with its current value on the right and optional end labels below. */
@Composable
fun CtSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    startLabel: String? = null,
    endLabel: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CtShapes.CardSmall)
            .background(CtColors.Surface)
            .padding(horizontal = CtSpacing.md, vertical = CtSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = CtType.TitleSmall, modifier = Modifier.weight(1f))
            Text(text = valueLabel, style = CtType.Label.copy(color = CtColors.Gold))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = CtColors.Gold,
                activeTrackColor = CtColors.Gold,
                inactiveTrackColor = CtColors.SurfaceHigh,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.semantics { contentDescription = title; stateDescription = valueLabel },
        )
        if (startLabel != null || endLabel != null) {
            Row {
                Text(text = startLabel.orEmpty(), style = CtType.Caption, modifier = Modifier.weight(1f))
                Text(text = endLabel.orEmpty(), style = CtType.Caption)
            }
        }
    }
}

/** Friendly placeholder when there is nothing to show yet. */
@Composable
fun CtEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(CtSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CtSpacing.sm),
    ) {
        CtIconBubble(icon = icon, tint = CtColors.Violet, size = 64.dp)
        Text(text = title, style = CtType.Title)
        Text(text = body, style = CtType.BodySmall)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(CtSpacing.sm))
            CtButton(text = actionLabel, onClick = onAction, style = CtButtonStyle.Secondary)
        }
    }
}

// ------------------------------------------------------------------ navigation

private fun TopTab.icon(): ImageVector = when (this) {
    TopTab.HOME -> Icons.Rounded.Home
    TopTab.LEARN -> Icons.Rounded.School
    TopTab.PUZZLES -> Icons.Rounded.Extension
    TopTab.ACCOUNT -> Icons.Rounded.AccountCircle
}

/** The four-tab bottom bar (plan section 3). */
@Composable
fun CtBottomBar(
    selected: TopTab?,
    onSelect: (TopTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CtColors.BgDeep)
            .navigationBarsPadding()
            .height(68.dp)
            .padding(horizontal = CtSpacing.sm),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val tint = if (isSelected) CtColors.Gold else CtColors.Text3
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(CtShapes.CardSmall)
                    .selectable(
                        selected = isSelected,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(tab) },
                    )
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .clip(CtShapes.Pill)
                        .background(if (isSelected) CtColors.Gold.copy(alpha = 0.16f) else Color.Transparent)
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tab.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                }
                Text(text = tab.label, style = CtType.Caption.copy(color = tint), maxLines = 1)
            }
        }
    }
}
