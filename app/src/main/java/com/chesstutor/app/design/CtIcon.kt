package com.chesstutor.app.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chesstutor.app.R

/** The polished metal version of each icon (made by `tools/art/metal.html`). */
private val metal: Map<ImageVector, Int> by lazy {
    mapOf(
        Icons.Rounded.Palette to R.drawable.icon_palette,
        Icons.Rounded.GridOn to R.drawable.icon_grid_on,
        Icons.Rounded.Person to R.drawable.icon_person,
        Icons.Rounded.Image to R.drawable.icon_image,
        Icons.AutoMirrored.Rounded.VolumeUp to R.drawable.icon_volume_up,
        Icons.Rounded.Link to R.drawable.icon_link,
        Icons.Rounded.CloudSync to R.drawable.icon_cloud_sync,
        Icons.Rounded.Memory to R.drawable.icon_memory,
        Icons.Rounded.Storage to R.drawable.icon_storage,
        Icons.Rounded.Info to R.drawable.icon_info,
        Icons.Rounded.Flag to R.drawable.icon_flag,
        Icons.Rounded.Extension to R.drawable.icon_extension,
        Icons.Rounded.Star to R.drawable.icon_star,
        Icons.Rounded.EmojiEvents to R.drawable.icon_emoji_events,
        Icons.Rounded.Refresh to R.drawable.icon_refresh,
        Icons.Rounded.Warning to R.drawable.icon_warning,
        Icons.Rounded.DeleteForever to R.drawable.icon_delete_forever,
        Icons.Rounded.BugReport to R.drawable.icon_bug_report,
        Icons.Rounded.Favorite to R.drawable.icon_favorite,
        Icons.Rounded.Download to R.drawable.icon_download,
        Icons.Rounded.Code to R.drawable.icon_code,
        Icons.Rounded.Description to R.drawable.icon_description,
        Icons.Rounded.Insights to R.drawable.icon_analytics,
        Icons.Rounded.Tune to R.drawable.icon_tune,
        Icons.Rounded.History to R.drawable.icon_history,
        Icons.Rounded.Map to R.drawable.icon_map,
        Icons.Rounded.CheckCircle to R.drawable.icon_check_circle,
        Icons.Rounded.SportsEsports to R.drawable.icon_sports_esports,
        Icons.Rounded.School to R.drawable.icon_school,
        Icons.Rounded.Bolt to R.drawable.icon_bolt,
        Icons.Rounded.Psychology to R.drawable.icon_psychology,
        Icons.Rounded.AutoStories to R.drawable.icon_auto_stories,
        Icons.Rounded.Shield to R.drawable.icon_shield,
    )
}

/** The picture for [icon], or null when it has no metal version yet. */
@DrawableRes
internal fun metalIcon(icon: ImageVector): Int? = metal[icon]

/**
 * An icon as a polished metal object (D57): the shape itself carries the colour, with no
 * background, and each meaning keeps one colour on every theme. Board screens keep plain icons.
 */
@Composable
fun CtMetalIcon(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val art = metalIcon(icon)
    if (art != null) {
        Image(painterResource(art), contentDescription = null, modifier = modifier.size(size))
    } else {
        Icon(icon, contentDescription = null, tint = CtColors.Accent, modifier = modifier.size(size * 0.8f))
    }
}
