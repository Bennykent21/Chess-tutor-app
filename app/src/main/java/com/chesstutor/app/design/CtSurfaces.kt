package com.chesstutor.app.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

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
 * every theme (design language section 2).
 */
private fun Modifier.raisedSurface(shape: Shape, background: Brush, ink: Color): Modifier =
    shadow(elevation = 10.dp, shape = shape, ambientColor = ink.copy(alpha = 0.06f), spotColor = ink.copy(alpha = 0.10f))
        .clip(shape)
        .background(background)

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
    val base = modifier.raisedSurface(CtShapes.Card, background, CtColors.Text)
    val clickable = if (onClick != null) base.ctPressable(onClickLabel = onClickLabel, onClick = onClick) else base
    Column(modifier = clickable.padding(contentPadding), content = content)
}

/**
 * The "continue" card at the top of Home, Learn and Puzzles: the picture fills it, with what it
 * is, the title, one line of context and a thin progress bar on the fade. The whole card is one
 * tap target; the play mark only marks it.
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
    picture: @Composable BoxScope.() -> Unit,
) {
    CtPictureTile(
        title = title,
        modifier = modifier.fillMaxWidth().height(196.dp),
        kicker = kicker,
        subtitle = detail,
        progress = progress,
        alive = true,
        titleStyle = CtType.Headline,
        onClick = onClick,
        onClickLabel = onClickLabel,
        trailing = { CtPlayMark() },
        picture = picture,
    )
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
