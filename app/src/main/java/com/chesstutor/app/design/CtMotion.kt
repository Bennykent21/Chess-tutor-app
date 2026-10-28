package com.chesstutor.app.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.delay

/** Motion tokens (`docs/design-language.md` section 7). UI motion stays under 300 ms. */
object CtMotion {
    /** Strong ease-out: quick start, soft landing. Pieces, presses, switches, fades. */
    val EaseOut: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    /** Sheets rising from the bottom. */
    val SheetEase: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    const val PRESS_MS = 140
    const val SWITCH_MS = 180
    const val FADE_MS = 180
    const val PIECE_SLIDE_MS = 220
    const val THEME_MS = 240
    const val SHEET_MS = 280

    const val PRESSED_SCALE = 0.97f
    const val ENTER_STAGGER_MS = 45
    const val ENTER_DURATION_MS = 320
    const val ENTER_RISE_PX = 36f
}

/**
 * Clickable that settles to 97% while pressed (140 ms ease-out). Uses the standard clickable,
 * so TalkBack announces it and keyboard and switch access work. With "Reduce motion" on it
 * does not scale.
 */
fun Modifier.ctPressable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val reduceMotion = LocalReduceMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduceMotion) CtMotion.PRESSED_SCALE else 1f,
        animationSpec = tween(CtMotion.PRESS_MS, easing = CtMotion.EaseOut),
        label = "ctPressScale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = onClick,
        )
}

/**
 * Staggered fade-and-rise when a screen appears (plan section 5.2). [index] sets the
 * delay, so a list of cards arrives one after another. Plays once per screen visit, and
 * not at all with "Reduce motion" on.
 */
fun Modifier.ctEnter(index: Int): Modifier = composed {
    val reduceMotion = LocalReduceMotion.current
    var shown by rememberSaveable { mutableStateOf(reduceMotion) }
    LaunchedEffect(Unit) {
        if (!shown) {
            delay((index.coerceIn(0, 12) * CtMotion.ENTER_STAGGER_MS).toLong())
            shown = true
        }
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else CtMotion.ENTER_DURATION_MS),
        label = "ctEnter",
    )
    this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * CtMotion.ENTER_RISE_PX
    }
}
