package com.chesstutor.app.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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

object CtMotion {
    const val PRESSED_SCALE = 0.96f
    const val ENTER_STAGGER_MS = 45
    const val ENTER_DURATION_MS = 320
    const val ENTER_RISE_PX = 36f
}

/**
 * Clickable with a soft spring press (plan section 5.2). Uses the standard clickable, so
 * TalkBack announces it and keyboard and switch access work. With "Reduce motion" on it
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
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
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
