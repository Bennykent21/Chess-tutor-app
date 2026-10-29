package com.chesstutor.app.design

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Search
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
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
            .background(CtColors.Bg),
        content = content,
    )
}

/**
 * The raised look of hero cards and sheets: a soft shadow tinted from the theme's ink on
 * light themes, a hairline on dark themes and Mono (design language section 2).
 */
private fun Modifier.raisedSurface(shape: Shape, background: Brush, outlined: Boolean, line: Color, ink: Color): Modifier =
    if (outlined) {
        clip(shape).background(background).border(1.dp, line, shape)
    } else {
        shadow(elevation = 10.dp, shape = shape, ambientColor = ink.copy(alpha = 0.06f), spotColor = ink.copy(alpha = 0.22f))
            .clip(shape)
            .background(background)
    }

/** Raised card. Clickable when [onClick] is set. */
@Composable
fun CtCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    background: Brush = CtGradients.Card,
    contentPadding: PaddingValues = PaddingValues(CtSpacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier.raisedSurface(CtShapes.Card, background, CtOutlinedCards, CtColors.Line, CtColors.Text)
    val clickable = if (onClick != null) base.ctPressable(onClickLabel = onClickLabel, onClick = onClick) else base
    Column(modifier = clickable.padding(contentPadding), content = content)
}

/**
 * The "continue" card at the top of Home, Learn and Puzzles: a board thumbnail, what it is,
 * the title, one line of context, a thin progress bar and a round play button. The whole card
 * is one tap target; the play button only marks it.
 */
@Composable
fun CtHeroCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    detail: String? = null,
    progress: Float? = null,
    onClickLabel: String? = null,
    thumbnail: @Composable BoxScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .raisedSurface(CtShapes.Card, SolidColor(CtColors.Raised), CtOutlinedCards, CtColors.Line, CtColors.Text)
            .ctPressable(onClickLabel = onClickLabel, onClick = onClick)
            .padding(CtSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.lg),
    ) {
        Box(modifier = Modifier.size(112.dp).clip(CtShapes.Chip), content = thumbnail)
        Column(
            modifier = Modifier.weight(1f).height(112.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.xxs)) {
                if (kicker != null) Text(text = kicker, style = CtType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = title, style = CtType.Headline, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = detail.orEmpty(), style = CtType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier.size(36.dp).clip(CtShapes.Pill).background(CtColors.Primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = CtColors.OnPrimary, modifier = Modifier.size(20.dp))
                    }
                }
                if (progress != null) CtProgressBar(progress = progress, height = 3.dp)
            }
        }
    }
}

/** A tile on the surface colour. Two side by side only when they do different jobs. */
@Composable
fun CtTile(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier.clip(CtShapes.Card).background(CtColors.Surface)
    Column(
        modifier = (if (onClick != null) base.ctPressable(onClickLabel = onClickLabel, onClick = onClick) else base).padding(CtSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
        content = content,
    )
}

/** A small card in a horizontal row of ways to play: an icon above a short label. */
@Composable
fun CtModeCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(124.dp)
            .height(96.dp)
            .clip(CtShapes.CardSmall)
            .background(CtColors.Surface)
            .ctPressable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, contentDescription = null, tint = CtColors.Accent, modifier = Modifier.size(22.dp))
        Text(text = label, style = CtType.Label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Circle holding an icon, used on cards and rows. */
@Composable
fun CtIconBubble(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    background: Color = tint.copy(alpha = 0.12f),
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CtShapes.Pill)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** A hairline between rows. */
@Composable
fun CtDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(CtColors.Line))
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
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = if (onBack != null) CtSpacing.sm else CtSpacing.gutter, end = CtSpacing.sm, top = CtSpacing.xs, bottom = CtSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            Spacer(Modifier.width(CtSpacing.xs))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = CtType.TopBar,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(text = subtitle, style = CtType.Caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
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

/** Which side of a speech bubble points at the speaker. Start and End follow the reading direction. */
enum class CtTail { None, Start, End, Top, Bottom }

private val TAIL_DEPTH = 10.dp
private val TAIL_BASE = 16.dp
private val TAIL_OFFSET = 16.dp

/** A speech bubble's body and the three corners of its tail (empty for [CtTail.None]), in pixels. */
internal data class BubbleGeometry(val body: Rect, val tail: List<Offset>)

/**
 * Where the body and tail of a bubble of [size] go. The tail is [depth] deep and [base] wide,
 * [offset] from the body's top (side tails) or start (top and bottom tails); Start and End
 * follow the reading direction.
 */
internal fun bubbleGeometry(tail: CtTail, size: Size, rtl: Boolean, depth: Float, base: Float, offset: Float): BubbleGeometry {
    val left = (tail == CtTail.Start && !rtl) || (tail == CtTail.End && rtl)
    val right = (tail == CtTail.End && !rtl) || (tail == CtTail.Start && rtl)
    val body = Rect(
        left = if (left) depth else 0f,
        top = if (tail == CtTail.Top) depth else 0f,
        right = size.width - if (right) depth else 0f,
        bottom = size.height - if (tail == CtTail.Bottom) depth else 0f,
    )
    val points = when {
        left -> listOf(Offset(body.left, body.top + offset), Offset(0f, body.top + offset + base / 2), Offset(body.left, body.top + offset + base))
        right -> listOf(Offset(body.right, body.top + offset), Offset(size.width, body.top + offset + base / 2), Offset(body.right, body.top + offset + base))
        tail == CtTail.Top -> listOf(Offset(body.left + offset, body.top), Offset(body.left + offset + base / 2, 0f), Offset(body.left + offset + base, body.top))
        tail == CtTail.Bottom -> listOf(Offset(body.left + offset, body.bottom), Offset(body.left + offset + base / 2, size.height), Offset(body.left + offset + base, body.bottom))
        else -> emptyList()
    }
    return BubbleGeometry(body, points)
}

/** A rounded bubble with a small triangle on one side, [TAIL_OFFSET] from its top or start. */
private class BubbleShape(private val tail: CtTail, private val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val g = with(density) {
            bubbleGeometry(tail, size, layoutDirection == LayoutDirection.Rtl, TAIL_DEPTH.toPx(), TAIL_BASE.toPx(), TAIL_OFFSET.toPx())
        }
        val path = Path().apply {
            addRoundRect(RoundRect(g.body, CornerRadius(with(density) { radius.toPx() })))
            if (g.tail.isNotEmpty()) {
                moveTo(g.tail[0].x, g.tail[0].y)
                lineTo(g.tail[1].x, g.tail[1].y)
                lineTo(g.tail[2].x, g.tail[2].y)
                close()
            }
        }
        return Outline.Generic(path)
    }
}

/**
 * A speech bubble whose tail points at whoever is speaking, a coach or a bot (D41). Place the
 * speaker on the [tail] side. Screen readers hear its content once, as one item.
 */
@Composable
fun CtSpeechBubble(
    modifier: Modifier = Modifier,
    tail: CtTail = CtTail.Start,
    color: Color = CtColors.Surface,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .clip(BubbleShape(tail, 18.dp))
            .background(color)
            .semantics(mergeDescendants = true) {}
            .padding(
                start = 14.dp + if (tail == CtTail.Start) TAIL_DEPTH else 0.dp,
                end = 14.dp + if (tail == CtTail.End) TAIL_DEPTH else 0.dp,
                top = CtSpacing.md + if (tail == CtTail.Top) TAIL_DEPTH else 0.dp,
                bottom = CtSpacing.md + if (tail == CtTail.Bottom) TAIL_DEPTH else 0.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

// ------------------------------------------------------------------ actions

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
    val outlined = CtOutlinedCards
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
                outlined -> Modifier.border(1.dp, CtColors.Line, CtShapes.Chip)
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
 * title, at most one short detail and a chevron, over a hairline.
 */
@Composable
fun CtListRow(
    title: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconTint: Color = CtColors.Accent,
    divider: Boolean = true,
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
                icon != null -> CtIconBubble(icon = icon, tint = iconTint, size = 40.dp)
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
        if (divider) CtDivider()
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
    divider: Boolean = true,
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
        if (divider) CtDivider()
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
        containerColor = CtColors.Raised,
        shape = RoundedCornerShape(24.dp),
        title = { Text(text = title, style = CtType.Headline) },
        text = { Text(text = text, style = CtType.Body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = confirmLabel, style = CtType.Button.copy(color = CtColors.Error)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Cancel", style = CtType.Button.copy(color = CtColors.Text)) }
        },
    )
}

/** A search box: a magnifier and a placeholder, no label above (game filters, opening lists). */
@Composable
fun CtSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CtSpacing.touchTarget)
            .clip(CtShapes.Control)
            .background(CtColors.Surface)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = CtColors.Text2, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = CtType.Body.copy(color = CtColors.Text2), maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = CtType.Body.copy(color = CtColors.Text),
                cursorBrush = SolidColor(CtColors.Text),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
    }
}

/** One entry of a [CtOverflowMenu]; [destructive] ones (Delete) are written in the error colour. */
data class CtMenuItem(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/** The three-dot button at the top right and its menu, for actions that are not the page's main one. */
@Composable
fun CtOverflowMenu(items: List<CtMenuItem>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            modifier = Modifier
                .size(CtSpacing.touchTarget)
                .clip(CtShapes.Pill)
                .ctPressable(onClickLabel = "More options") { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = CtColors.Text)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = CtColors.Raised) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label, style = CtType.Body.copy(color = if (item.destructive) CtColors.Error else CtColors.Text)) },
                    onClick = {
                        open = false
                        item.onClick()
                    },
                )
            }
        }
    }
}

/** The small grey name over a group of choices ("You play", "Clock"). */
@Composable
fun CtGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = CtType.Label.copy(color = CtColors.Text2), modifier = modifier.padding(top = CtSpacing.lg, bottom = CtSpacing.sm))
}

/**
 * A settings-style page whose content scrolls under a top bar while its one main action
 * stays fixed at the bottom ("Play", "Save bot", "Start").
 */
@Composable
fun CtActionPage(
    title: String,
    onBack: (() -> Unit)?,
    bottom: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize()) {
            CtTopBar(title = title, onBack = onBack, trailing = actions)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = CtSpacing.gutter, end = CtSpacing.gutter, bottom = CtSpacing.lg),
                content = content,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = CtSpacing.gutter, end = CtSpacing.gutter, top = CtSpacing.sm, bottom = CtSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = bottom,
            )
        }
    }
}

/** A one-line text input with its label above. */
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
            cursorBrush = SolidColor(CtColors.Accent),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .clip(CtShapes.Control)
                .background(CtColors.Surface)
                .border(1.dp, CtColors.Line, CtShapes.Control)
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder != null) Text(text = placeholder, style = CtType.Body)
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
            .padding(horizontal = CtSpacing.lg, vertical = CtSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = CtType.TitleSmall, modifier = Modifier.weight(1f))
            Text(text = valueLabel, style = CtType.Label.copy(color = CtColors.Accent))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = CtColors.Primary,
                activeTrackColor = CtColors.Primary,
                inactiveTrackColor = CtColors.Track,
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
        CtIconBubble(icon = icon, tint = CtColors.Accent, size = 64.dp)
        Text(text = title, style = CtType.Title)
        Text(text = body, style = CtType.BodySmall)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(CtSpacing.sm))
            CtButton(text = actionLabel, onClick = onAction, style = CtButtonStyle.Secondary)
        }
    }
}

// ------------------------------------------------------------------ overlays

private val SCRIM = Color(0x52000000)

/**
 * A sheet that slides up over the screen (game over, results). It never pushes content (D39).
 * Tapping outside or Back closes it. Place it last in a full-screen Box.
 */
@Composable
fun CtSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val fade = if (reduceMotion) 0 else CtMotion.FADE_MS
    val slide = if (reduceMotion) 0 else CtMotion.SHEET_MS
    BackHandler(enabled = visible, onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(fade)), exit = fadeOut(tween(fade))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(SCRIM)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Close",
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(slide, easing = CtMotion.SheetEase)) { it },
            exit = slideOutVertically(tween(slide, easing = CtMotion.SheetEase)) { it },
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(CtShapes.Sheet)
                    .background(CtColors.Raised)
                    // Taps on the sheet itself must not reach the scrim behind it.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .navigationBarsPadding()
                    .padding(start = CtSpacing.gutter, end = CtSpacing.gutter, top = 10.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.lg),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(CtShapes.Pill).background(CtColors.Track))
                content()
            }
        }
    }
}

// ------------------------------------------------------------------ navigation

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
        CtDivider()
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
