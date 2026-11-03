package com.chesstutor.app.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

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

    const val PRESSED_SCALE = 0.95f
    const val ENTER_MS = 480
    const val ENTER_STAGGER_MS = 80L
    const val SHEEN_DELAY_MS = 500L
    const val SHEEN_MS = 900
    /** A graph or ring drawing itself in. */
    const val DRAW_MS = 800

    /** Above this luminance a surface is too light for white light to show on it. */
    const val LIGHT_SURFACE = 0.5f
}

/**
 * Eases in when first shown, rising a little; a higher [order] comes in slightly later, so a page
 * builds from the top. With "Reduce motion" on it simply appears.
 */
fun Modifier.ctEnter(order: Int = 0): Modifier = composed {
    val reduceMotion = LocalReduceMotion.current
    val shown = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            delay(order * CtMotion.ENTER_STAGGER_MS)
            shown.animateTo(1f, tween(CtMotion.ENTER_MS, easing = CtMotion.EaseOut))
        }
    }
    graphicsLayer {
        alpha = shown.value
        translationY = (1f - shown.value) * 28.dp.toPx()
    }
}

/** A slow swell up to [to] and back, for something that should feel alive. Still with "Reduce motion". */
fun Modifier.ctBreathe(to: Float, periodMs: Int): Modifier = composed {
    if (LocalReduceMotion.current) return@composed Modifier
    val swell = rememberInfiniteTransition(label = "ctBreathe")
    val scale by swell.animateFloat(1f, to, infiniteRepeatable(tween(periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ctBreatheScale")
    Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * One band across the [surface] soon after it appears: light on a dark surface, shade on a white
 * one, where light would not show. Nothing with "Reduce motion".
 */
fun Modifier.ctSheen(surface: Color): Modifier = composed {
    if (LocalReduceMotion.current) return@composed Modifier
    val band = if (surface.luminance() > CtMotion.LIGHT_SURFACE) Color.Black.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.32f)
    val across = remember { Animatable(-0.4f) }
    LaunchedEffect(Unit) {
        delay(CtMotion.SHEEN_DELAY_MS)
        across.animateTo(1.4f, tween(CtMotion.SHEEN_MS, easing = CtMotion.EaseOut))
    }
    Modifier.drawWithContent {
        drawContent()
        val reach = 90.dp.toPx()
        val x = across.value * size.width
        drawRect(Brush.linearGradient(listOf(Color.Transparent, band, Color.Transparent), Offset(x - reach, 0f), Offset(x + reach, size.height)))
    }
}

/**
 * Clickable that dips to 95% while pressed and springs back. Uses the standard clickable,
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
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
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
