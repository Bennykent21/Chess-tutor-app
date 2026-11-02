package com.chesstutor.app.design

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.chesstutor.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A rendered scene (D56): one picture on pure black for dark themes and one on pure white for
 * light themes, made by `tools/art`. [CtArt] blends it into any theme's colours.
 */
enum class CtScene(@param:DrawableRes val night: Int, @param:DrawableRes val day: Int) {
    BASICS(R.drawable.art_basics_night, R.drawable.art_basics_day),
    OPENINGS(R.drawable.art_openings_night, R.drawable.art_openings_day),
    TACTICS(R.drawable.art_tactics_night, R.drawable.art_tactics_day),
    MIDDLEGAME(R.drawable.art_middlegame_night, R.drawable.art_middlegame_day),
    ENDGAMES(R.drawable.art_endgames_night, R.drawable.art_endgames_day),
    BLUNDERS(R.drawable.art_blunders_night, R.drawable.art_blunders_day),
    BATTLE(R.drawable.art_battle_night, R.drawable.art_battle_day),
    EXPLORE(R.drawable.art_explore_night, R.drawable.art_explore_day),
    POSITION(R.drawable.art_position_night, R.drawable.art_position_day),
    RANK_PAWN(R.drawable.art_rank_pawn_night, R.drawable.art_rank_pawn_day),
    RANK_KNIGHT(R.drawable.art_rank_knight_night, R.drawable.art_rank_knight_day),
    RANK_BISHOP(R.drawable.art_rank_bishop_night, R.drawable.art_rank_bishop_day),
    RANK_ROOK(R.drawable.art_rank_rook_night, R.drawable.art_rank_rook_day),
    RANK_QUEEN(R.drawable.art_rank_queen_night, R.drawable.art_rank_queen_day),
    RANK_KING(R.drawable.art_rank_king_night, R.drawable.art_rank_king_day),
}

/** Decoded scenes, at the size asked for, so going back to a page does not flash. */
private object ArtCache {
    private const val MAX_KB = 48 * 1024
    private const val SOURCE_WIDTH = 1080
    private val pictures = object : LruCache<Long, ImageBitmap>(MAX_KB) {
        override fun sizeOf(key: Long, value: ImageBitmap): Int = value.width * value.height * 4 / 1024
    }

    /** Halves the picture while it still covers [width] pixels. */
    private fun sampleFor(width: Int): Int {
        var sample = 1
        while (width > 0 && SOURCE_WIDTH / (sample * 2) >= width) sample *= 2
        return sample
    }

    private fun key(id: Int, width: Int): Long = id.toLong() * 16 + sampleFor(width)

    fun cached(id: Int, width: Int): ImageBitmap? = pictures.get(key(id, width))

    fun load(resources: Resources, id: Int, width: Int): ImageBitmap? {
        cached(id, width)?.let { return it }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(width) }
        val picture = BitmapFactory.decodeResource(resources, id, options)?.asImageBitmap() ?: return null
        pictures.put(key(id, width), picture)
        return picture
    }
}

/** Draws [picture] covering the area, shifted up or down by [focusY] (0 top, 1 bottom). */
private fun DrawScope.drawCover(picture: ImageBitmap, focusY: Float, blend: BlendMode, alpha: Float) {
    val fit = max(size.width / picture.width, size.height / picture.height)
    val width = (size.width / fit).roundToInt().coerceAtMost(picture.width)
    val height = (size.height / fit).roundToInt().coerceAtMost(picture.height)
    drawImage(
        image = picture,
        srcOffset = IntOffset((picture.width - width) / 2, ((picture.height - height) * focusY).roundToInt()),
        srcSize = IntSize(width, height),
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        alpha = alpha,
        blendMode = blend,
        filterQuality = FilterQuality.High,
    )
}

/**
 * A scene melted into the page: black becomes [stage] on dark themes and white becomes it on
 * light ones, so there is no box around the picture. The foot fades to [stage], where words go,
 * and [lift] slides the picture up by that share of its height to keep them clear of the pieces.
 * [alive] lets it swell very slowly, for the one big picture on a page.
 */
@Composable
fun CtArt(
    scene: CtScene,
    modifier: Modifier = Modifier,
    stage: Color = CtColors.Bg,
    focusY: Float = 0.5f,
    fadeFrom: Float = 0.38f,
    lift: Float = 0f,
    alive: Boolean = false,
) {
    val dark = CtColors.IsDark
    val id = if (dark) scene.night else scene.day
    val blend = if (dark) BlendMode.Screen else BlendMode.Modulate
    val reduceMotion = LocalReduceMotion.current
    val resources = LocalContext.current.resources
    BoxWithConstraints(modifier.background(stage).clipToBounds().clearAndSetSemantics {}) {
        val width = constraints.maxWidth
        val picture by produceState(ArtCache.cached(id, width), id, width) {
            value = withContext(Dispatchers.IO) { ArtCache.load(resources, id, width) }
        }
        val shown by animateFloatAsState(if (picture != null) 1f else 0f, tween(if (reduceMotion) 0 else CtMotion.THEME_MS), label = "ctArtShown")
        val swell = if (alive && !reduceMotion) {
            val transition = rememberInfiniteTransition(label = "ctArtSwell")
            val value by transition.animateFloat(1f, 1.06f, infiniteRepeatable(tween(9_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ctArtSwellScale")
            value
        } else {
            1f
        }
        Canvas(Modifier.fillMaxSize()) {
            picture?.let { translate(top = -size.height * lift) { scale(swell) { drawCover(it, focusY, blend, shown) } } }
        }
        // The fade must end before a lifted picture does, or the picture's foot shows as an edge.
        val fadeTo = min(0.92f, 0.98f - lift)
        if (fadeFrom < fadeTo) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(fadeFrom to stage.copy(alpha = 0f), fadeTo to stage)))
        }
    }
}

/** A tile the [scene] fills, with its words in the theme's own colours on the fade. */
@Composable
fun CtArtTile(
    title: String,
    scene: CtScene,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    subtitle: String? = null,
    progress: Float? = null,
    compact: Boolean = false,
    titleStyle: TextStyle = if (compact) CtType.Label else CtType.TitleSmall,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    lift: Float = 0.2f,
    trailing: (@Composable () -> Unit)? = null,
) {
    val stage = CtColors.Surface
    val base = modifier.clip(if (compact) CtShapes.CardSmall else CtShapes.Card).background(stage)
    Box(if (onClick != null) base.ctPressable(onClickLabel = onClickLabel, onClick = onClick) else base) {
        CtArt(scene, Modifier.matchParentSize(), stage = stage, fadeFrom = 0.48f, lift = lift)
        TileWords(
            title = title,
            kicker = kicker,
            subtitle = subtitle,
            progress = progress,
            compact = compact,
            titleStyle = titleStyle,
            ink = CtColors.Text,
            softInk = CtColors.Text2,
            track = CtColors.Track,
            trailing = trailing,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

/** The play mark on a scene: solid, because a glass mark would vanish on a light theme's picture. */
@Composable
fun CtArtPlayMark() {
    Box(
        modifier = Modifier.size(40.dp).clip(CtShapes.Pill).background(CtColors.Primary).ctGloss(CtShapes.Pill, CtColors.Primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = CtColors.OnPrimary, modifier = Modifier.size(22.dp))
    }
}
