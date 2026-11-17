package com.chesstutor.app.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A centred pop-up for a short moment of its own: a game's result, a cup won, a badge, a few
 * choices (D91). It covers the whole app, bottom bar included; a tap outside or Back closes it.
 * Longer flows get a [CtFullPage] or a screen of their own, never a sheet from the bottom.
 */
@Composable
fun CtDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    val reduceMotion = LocalReduceMotion.current
    val shown = remember { MutableTransitionState(reduceMotion) }
    LaunchedEffect(Unit) { shown.targetState = true }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(SCRIM)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close", onClick = onDismiss)
                .systemBarsPadding()
                .padding(CtSpacing.gutter),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visibleState = shown,
                enter = if (reduceMotion) fadeIn(tween(0)) else fadeIn(tween(CtMotion.FADE_MS)) + scaleIn(tween(CtMotion.OVERLAY_MS, easing = CtMotion.OverlayEase), initialScale = 0.94f),
                exit = fadeOut(tween(0)) + scaleOut(tween(0)),
            ) {
                Column(
                    modifier = modifier
                        .widthIn(max = 440.dp)
                        .fillMaxWidth()
                        .clip(DialogShape)
                        .background(CtColors.Raised)
                        // Taps on the card itself must not reach the scrim behind it.
                        .pointerInput(Unit) { detectTapGestures { } }
                        .verticalScroll(rememberScrollState())
                        .padding(CtSpacing.xl),
                    verticalArrangement = Arrangement.spacedBy(CtSpacing.lg),
                    content = content,
                )
            }
        }
    }
}

/**
 * A full page over the current screen for a flow that needs room (filters, set-up choices, a
 * picker), with its one main action fixed at the bottom (D91). It slides in like a new screen and
 * Back closes it, but it shares the screen's state, so nothing is lost on the way back.
 */
@Composable
fun CtFullPage(
    visible: Boolean,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable (RowScope.() -> Unit)? = null,
    bottom: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val slide = if (reduceMotion) 0 else CtMotion.OVERLAY_MS
    BackHandler(enabled = visible, onBack = onBack)
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(slide, easing = CtMotion.OverlayEase)) { it },
        exit = slideOutHorizontally(tween(slide, easing = CtMotion.OverlayEase)) { it },
    ) {
        CtActionPage(
            title = title,
            onBack = onBack,
            bottom = bottom,
            actions = actions,
            // Taps on the page must not reach the screen underneath.
            modifier = modifier.pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.lg), content = content)
        }
    }
}

private val DialogShape = RoundedCornerShape(28.dp)
private val SCRIM = Color(0x99000000)
