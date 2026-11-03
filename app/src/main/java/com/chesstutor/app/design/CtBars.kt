package com.chesstutor.app.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.navigation.TopTab

@Composable
fun CtTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Layout(
        content = {
            Box {
                if (onBack != null) {
                    Box(
                        modifier = Modifier
                            .size(CtSpacing.touchTarget)
                            .clip(CtShapes.Pill)
                            .ctPressable(onClickLabel = "Go back", onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = CtColors.Text)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = CtType.TopBar,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (subtitle != null) {
                    Text(text = subtitle, style = CtType.Caption, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { trailing?.invoke(this) }
        },
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = CtSpacing.sm, vertical = CtSpacing.xs),
    ) { measurables, constraints ->
        // The title stays centred on the screen whatever sits beside it: it gets the width left
        // between the wider side, mirrored on both.
        val sides = constraints.copy(minWidth = 0, minHeight = 0)
        val leading = measurables[0].measure(sides)
        val end = measurables[2].measure(sides)
        val side = maxOf(leading.width, end.width)
        val centre = measurables[1].measure(sides.copy(maxWidth = (constraints.maxWidth - 2 * side).coerceAtLeast(0)))
        val height = maxOf(leading.height, centre.height, end.height, constraints.minHeight)
        layout(constraints.maxWidth, height) {
            leading.placeRelative(0, (height - leading.height) / 2)
            centre.placeRelative((constraints.maxWidth - centre.width) / 2, (height - centre.height) / 2)
            end.placeRelative(constraints.maxWidth - end.width, (height - end.height) / 2)
        }
    }
}

/**
 * The big title at the top of a tab or a top-level page (30 sp), with small [trailing] items
 * on the right, such as a streak or a settings button (design language section 5).
 */
@Composable
fun CtScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = CtSpacing.gutter, end = CtSpacing.gutter, top = CtSpacing.sm, bottom = CtSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = CtType.Display,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), content = trailing)
        }
    }
}

/** Just a back arrow, above a page that shows its own big [CtScreenTitle] (Settings). */
@Composable
fun CtBackBar(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().height(56.dp).padding(horizontal = CtSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(CtSpacing.touchTarget)
                .clip(CtShapes.Pill)
                .ctPressable(onClickLabel = "Go back", onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = CtColors.Text)
        }
    }
}

private fun TopTab.icon(): ImageVector = when (this) {
    TopTab.HOME -> Icons.Rounded.Home
    TopTab.LEARN -> Icons.AutoMirrored.Rounded.MenuBook
    TopTab.PUZZLES -> Icons.Rounded.Extension
    TopTab.ACCOUNT -> Icons.Rounded.Person
}

/** The four-tab bottom bar (plan section 3): the active tab sits in a tonal pill. */
@Composable
fun CtBottomBar(
    selected: TopTab?,
    onSelect: (TopTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    Column(modifier = modifier.fillMaxWidth().background(CtColors.Bg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(80.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopTab.entries.forEach { tab ->
                val isSelected = tab == selected
                val pill by animateColorAsState(
                    targetValue = if (isSelected) CtColors.NavPill else CtColors.Bg,
                    animationSpec = tween(if (reduceMotion) 0 else CtMotion.FADE_MS, easing = CtMotion.EaseOut),
                    label = "navPill",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = isSelected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(tab) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CtSpacing.xs, Alignment.CenterVertically),
                ) {
                    Box(
                        modifier = Modifier.size(width = 60.dp, height = 32.dp).clip(CtShapes.Pill).background(pill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(tab.icon(), contentDescription = null, tint = if (isSelected) CtColors.NavIcon else CtColors.Text2, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        text = tab.label,
                        style = if (isSelected) CtType.NavLabel.copy(color = CtColors.Text, fontWeight = CtType.Label.fontWeight) else CtType.NavLabel,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
