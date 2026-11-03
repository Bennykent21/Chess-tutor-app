package com.chesstutor.app.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The colours of the app's icons (D57). They are the same on every theme, like the glow of a
 * topic's scene, so an icon keeps its meaning wherever it appears. Each runs light to deep.
 */
enum class CtHue(val top: Color, val bottom: Color) {
    GOLD(Color(0xFFF4C95D), Color(0xFFC78A17)),
    EMBER(Color(0xFFFFA45C), Color(0xFFD9541A)),
    RED(Color(0xFFF47A84), Color(0xFFC62F45)),
    VIOLET(Color(0xFFB096FF), Color(0xFF6A3FE0)),
    BLUE(Color(0xFF74BBFF), Color(0xFF2B6FD6)),
    TEAL(Color(0xFF54DCCA), Color(0xFF119C8C)),
    GREEN(Color(0xFF63DD9C), Color(0xFF1F9A5C)),
    SLATE(Color(0xFFA3AFBB), Color(0xFF5E6B78)),
    ;

    companion object {
        private val byIcon: Map<ImageVector, CtHue> by lazy {
            mapOf(
                Icons.Rounded.Palette to VIOLET,
                Icons.Rounded.GridOn to GOLD,
                Icons.Rounded.Person to BLUE,
                Icons.Rounded.Image to TEAL,
                Icons.AutoMirrored.Rounded.VolumeUp to EMBER,
                Icons.Rounded.Link to TEAL,
                Icons.Rounded.CloudSync to TEAL,
                Icons.Rounded.Memory to BLUE,
                Icons.Rounded.Storage to GOLD,
                Icons.Rounded.Info to SLATE,
                Icons.Rounded.Flag to GREEN,
                Icons.Rounded.Extension to VIOLET,
                Icons.Rounded.Star to GOLD,
                Icons.Rounded.EmojiEvents to GOLD,
                Icons.Rounded.Refresh to BLUE,
                Icons.Rounded.Warning to RED,
                Icons.Rounded.DeleteForever to RED,
                Icons.Rounded.BugReport to RED,
                Icons.Rounded.Favorite to RED,
                Icons.Rounded.Download to BLUE,
                Icons.Rounded.Code to SLATE,
                Icons.Rounded.Description to SLATE,
                Icons.Rounded.Insights to BLUE,
                Icons.Rounded.Tune to SLATE,
                Icons.Rounded.History to EMBER,
                Icons.Rounded.Map to GREEN,
                Icons.Rounded.CheckCircle to GREEN,
                Icons.Rounded.SportsEsports to VIOLET,
                Icons.Rounded.School to GREEN,
                Icons.Rounded.Bolt to GOLD,
                Icons.Rounded.Psychology to BLUE,
                Icons.Rounded.AutoStories to RED,
                Icons.Rounded.Shield to EMBER,
            )
        }

        /** The colour that belongs to [icon], so it is the same everywhere. */
        fun of(icon: ImageVector): CtHue = byIcon[icon] ?: SLATE
    }
}

/** A coloured, glossy disc holding a white icon, used on rows, medals and empty pages. */
@Composable
fun CtIconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    hue: CtHue = CtHue.of(icon),
    size: Dp = 40.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CtShapes.Pill)
            .background(Brush.verticalGradient(listOf(hue.top, hue.bottom)))
            .ctGloss(CtShapes.Pill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = CtPictureInk, modifier = Modifier.size(size * 0.54f))
    }
}
