package com.chesstutor.app.design

import android.graphics.Bitmap
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
 * light themes, made by `tools/art`. [CtArt] lifts it off that ground onto any theme's colours.
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
    FRIEND(R.drawable.art_friend_night, R.drawable.art_friend_day),
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

    fun load(resources: Resources, id: Int, width: Int, night: Boolean): ImageBitmap? {
        cached(id, width)?.let { return it }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(width) }
        val decoded = BitmapFactory.decodeResource(resources, id, options) ?: return null
        val picture = keyOut(decoded, night).asImageBitmap()
        pictures.put(key(id, width), picture)
        return picture
    }

    /**
     * The art's black (night) or white (day) ground made see-through once, as it loads (D108), so
     * it is drawn plainly on any background with no blending layer, which flickered while scrolling.
     * Each pixel's distance from the ground becomes its opacity. A night piece's body is nearly
     * black, though, and would let the background through like a film, so a soft dark pool is kept
     * behind every piece, which stays solid and sharp.
     */
    private fun keyOut(source: Bitmap, night: Boolean): Bitmap {
        val w = source.width
        val h = source.height
        val px = IntArray(w * h)
        source.getPixels(px, 0, w, 0, 0, w, h)
        val pool = if (night) IntArray(w * h).also { poolOf(px, w, h).apply { getPixels(it, 0, w, 0, 0, w, h) }.recycle() } else null
        for (i in px.indices) {
            val c = px[i]
            val r = c shr 16 and 0xFF
            val g = c shr 8 and 0xFF
            val b = c and 0xFF
            px[i] = if (night) {
                // The keyed picture over its black pool: opacity a + p(1 - a), colour c / opacity.
                val keyed = maxOf(r, g, b)
                val a = keyed + (pool!![i] ushr 24) * (255 - keyed) / 255
                if (a == 0) 0 else (a shl 24) or (r * 255 / a shl 16) or (g * 255 / a shl 8) or (b * 255 / a)
            } else {
                val a = 255 - minOf(r, g, b)
                if (a == 0) 0 else (a shl 24) or (255 - (255 - r) * 255 / a shl 16) or (255 - (255 - g) * 255 / a shl 8) or (255 - (255 - b) * 255 / a)
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * The dark pool behind a night picture's pieces, as a black picture of [w] by [h] whose opacity
     * is the pool. On a coarse grid: the lit cells are widened to close gaps in a piece's rim, what
     * they enclose is filled, the widening is taken back, and the edge is softened.
     */
    private fun poolOf(px: IntArray, w: Int, h: Int): Bitmap {
        val cell = max(4, w / POOL_CELLS)
        val cols = (w + cell - 1) / cell
        val rows = (h + cell - 1) / cell
        val lit = BooleanArray(cols * rows)
        for (y in 0 until h) for (x in 0 until w) {
            val c = px[y * w + x]
            if (maxOf(c shr 16 and 0xFF, c shr 8 and 0xFF, c and 0xFF) > POOL_LIGHT) lit[y / cell * cols + x / cell] = true
        }
        val closed = grow(lit, cols, rows)
        // Dark cells reached from the edge are ground; every other cell is a piece or inside one.
        val ground = BooleanArray(cols * rows)
        val queue = ArrayDeque<Int>()
        for (k in closed.indices) {
            val x = k % cols
            val y = k / cols
            if (!closed[k] && (x == 0 || y == 0 || x == cols - 1 || y == rows - 1)) {
                ground[k] = true
                queue.add(k)
            }
        }
        while (queue.isNotEmpty()) {
            val k = queue.removeFirst()
            val x = k % cols
            val y = k / cols
            for (n in intArrayOf(if (x > 0) k - 1 else -1, if (x < cols - 1) k + 1 else -1, if (y > 0) k - cols else -1, if (y < rows - 1) k + cols else -1)) {
                if (n >= 0 && !closed[n] && !ground[n]) {
                    ground[n] = true
                    queue.add(n)
                }
            }
        }
        val wideGround = grow(ground, cols, rows)
        var pool = FloatArray(cols * rows) { if (lit[it] || !wideGround[it]) 1f else 0f }
        repeat(2) { pool = soften(pool, cols, rows) }
        val small = IntArray(cols * rows) { (pool[it] * 255).roundToInt() shl 24 }
        val coarse = Bitmap.createBitmap(small, cols, rows, Bitmap.Config.ARGB_8888)
        return Bitmap.createScaledBitmap(coarse, w, h, true).also { if (it !== coarse) coarse.recycle() }
    }

    /** [cells] widened by [POOL_CLOSE] cells every way. */
    private fun grow(cells: BooleanArray, cols: Int, rows: Int): BooleanArray {
        val out = BooleanArray(cells.size)
        for (y in 0 until rows) for (x in 0 until cols) {
            if (!cells[y * cols + x]) continue
            for (yy in max(0, y - POOL_CLOSE)..min(rows - 1, y + POOL_CLOSE)) for (xx in max(0, x - POOL_CLOSE)..min(cols - 1, x + POOL_CLOSE)) out[yy * cols + xx] = true
        }
        return out
    }

    /** Each cell averaged with its eight neighbours (the edge repeated). */
    private fun soften(cells: FloatArray, cols: Int, rows: Int): FloatArray = FloatArray(cells.size) { k ->
        val x = k % cols
        val y = k / cols
        var sum = 0f
        for (dy in -1..1) for (dx in -1..1) sum += cells[(y + dy).coerceIn(0, rows - 1) * cols + (x + dx).coerceIn(0, cols - 1)]
        sum / 9
    }

    /** The pool's grid: this many cells across the picture. */
    private const val POOL_CELLS = 60

    /** Cells a piece's rim is widened by to close its gaps. */
    private const val POOL_CLOSE = 2

    /** Brightness, out of 255, above which a cell holds part of a piece. */
    private const val POOL_LIGHT = 38
}

/** Draws [picture] covering the area, shifted up or down by [focusY] (0 top, 1 bottom). */
private fun DrawScope.drawCover(picture: ImageBitmap, focusY: Float, alpha: Float) {
    val fit = max(size.width / picture.width, size.height / picture.height)
    val width = (size.width / fit).roundToInt().coerceAtMost(picture.width)
    val height = (size.height / fit).roundToInt().coerceAtMost(picture.height)
    drawImage(
        image = picture,
        srcOffset = IntOffset((picture.width - width) / 2, ((picture.height - height) * focusY).roundToInt()),
        srcSize = IntSize(width, height),
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.High,
    )
}

/**
 * A scene melted into the page: its black (dark themes) or white (light ones) ground is see-through,
 * so there is no box around the picture. The foot fades to [stage], where words go,
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
    // The art carries its own transparency (D108), so it is drawn plainly: on the stage, or straight
    // on the glow in frosted glass, where its foot fades out inside its own layer (D107).
    val glass = LocalGlass.current
    val reduceMotion = LocalReduceMotion.current
    val resources = LocalContext.current.resources
    val layer = if (glass) Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } else Modifier.background(stage)
    BoxWithConstraints(modifier.then(layer).clipToBounds().clearAndSetSemantics {}) {
        val width = constraints.maxWidth
        val picture by produceState(ArtCache.cached(id, width), id, width) {
            value = withContext(Dispatchers.IO) { ArtCache.load(resources, id, width, night = dark) }
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
            picture?.let { translate(top = -size.height * lift) { scale(swell) { drawCover(it, focusY, shown) } } }
        }
        // The fade must end before a lifted picture does, or the picture's foot shows as an edge.
        val fadeTo = min(0.92f, 0.98f - lift)
        if (fadeFrom < fadeTo) {
            if (glass) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Brush.verticalGradient(0f to Color.Black, fadeFrom to Color.Black, fadeTo to Color.Transparent), blendMode = BlendMode.DstIn)
                }
            } else {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(fadeFrom to stage.copy(alpha = 0f), fadeTo to stage)))
            }
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

/** The play mark on a scene: the same glass as on a picture, in the theme's ink, so it shows on a light page too. */
@Composable
fun CtArtPlayMark() {
    CtGlassMark(40.dp, ink = CtColors.Text, surface = CtColors.Surface) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = CtColors.Text, modifier = Modifier.size(22.dp))
    }
}
