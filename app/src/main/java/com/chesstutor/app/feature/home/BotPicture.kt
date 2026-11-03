package com.chesstutor.app.feature.home

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.chesstutor.app.design.CtColors

/** The side of the tiny copy of a face that makes its colour wash. */
private const val WASH_SIZE = 12

/** A bot's sharp face standing in a wash of its own colours (D53), for a tile to fill. */
@Composable
fun BotPicture(face: String) {
    val art = botArt(face)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        if (art != null) {
            FaceWash(art)
            Image(painterResource(art), contentDescription = null, modifier = Modifier.size(min(maxWidth * 0.94f, maxHeight)))
        } else {
            Icon(Icons.Rounded.SmartToy, contentDescription = null, tint = CtColors.Text2, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/**
 * The face drawn tiny and stretched, which melts it into its colours on every Android version;
 * from Android 12 the blur smooths that further. The colour comes from the art, not a palette.
 */
@Composable
private fun FaceWash(@DrawableRes art: Int) {
    val painter = painterResource(art)
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val tiny = remember(art) {
        Bitmap.createBitmap(WASH_SIZE, WASH_SIZE, Bitmap.Config.ARGB_8888).asImageBitmap().also { bitmap ->
            CanvasDrawScope().draw(density, direction, Canvas(bitmap), Size(WASH_SIZE.toFloat(), WASH_SIZE.toFloat())) {
                with(painter) { draw(size) }
            }
        }
    }
    Image(
        bitmap = tiny,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.Low,
        modifier = Modifier.fillMaxSize().scale(1.5f).blur(20.dp, BlurredEdgeTreatment.Unbounded),
    )
}
