package com.chesstutor.app.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
            .padding(top = CtSpacing.lg, bottom = CtSpacing.xs),
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
                style = CtType.Label.copy(color = CtColors.Accent),
                modifier = Modifier
                    .clip(CtShapes.Pill)
                    .ctPressable(onClick = onAction)
                    .padding(horizontal = CtSpacing.sm, vertical = CtSpacing.md),
            )
        }
    }
}

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
            .padding(horizontal = 14.dp, vertical = CtSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(text = value, style = CtType.Number, maxLines = 1)
            Text(text = label, style = CtType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Thin rounded progress bar that fills smoothly (instantly with "Reduce motion" on). */
@Composable
fun CtProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = CtColors.Accent,
    brush: Brush? = null,
    height: Dp = 4.dp,
    trackColor: Color = CtColors.Track,
) {
    val reduceMotion = LocalReduceMotion.current
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 600, easing = CtMotion.EaseOut),
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

/**
 * A tappable row: an icon (or any 40 dp [leading] picture, such as a board or a coach), a
 * title, at most one short detail and a chevron.
 */
@Composable
fun CtListRow(
    title: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .ctPressable(onClick = onClick)
                .padding(vertical = CtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                leading != null -> Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { leading() }
                icon != null -> Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { CtMetalIcon(icon, size = 32.dp) }
            }
            if (leading != null || icon != null) Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CtSpacing.xxs)) {
                Text(text = title, style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(text = subtitle, style = CtType.Caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (trailing != null) {
                trailing()
                Spacer(Modifier.width(CtSpacing.xs))
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = CtColors.Text2)
        }
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
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(vertical = CtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = CtSpacing.md), verticalArrangement = Arrangement.spacedBy(CtSpacing.xxs)) {
                Text(text = title, style = CtType.Body.copy(color = CtColors.Text))
                if (subtitle != null) Text(text = subtitle, style = CtType.Caption)
            }
            CtSwitch(checked = checked)
        }
    }
}

/** The small grey name over a group of choices ("You play", "Clock"). */
@Composable
fun CtGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = CtType.Label.copy(color = CtColors.Text2), modifier = modifier.padding(top = CtSpacing.lg, bottom = CtSpacing.sm))
}

/** Calm placeholder when there is nothing to show yet. */
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
        CtMetalIcon(icon, size = 64.dp)
        Text(text = title, style = CtType.Title)
        Text(text = body, style = CtType.BodySmall)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(CtSpacing.sm))
            CtButton(text = actionLabel, onClick = onAction, style = CtButtonStyle.Secondary)
        }
    }
}
