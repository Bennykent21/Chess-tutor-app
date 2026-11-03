package com.chesstutor.app.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

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
