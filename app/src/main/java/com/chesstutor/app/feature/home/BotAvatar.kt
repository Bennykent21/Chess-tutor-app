package com.chesstutor.app.feature.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chesstutor.app.R
import com.chesstutor.app.design.CtColors

/** The face drawn for a roster bot (design/bots, made by tools/make_bot_faces.py). */
@DrawableRes
fun botArt(botId: String): Int? = when (botId) {
    "pebble" -> R.drawable.bot_pebble
    "wayne" -> R.drawable.bot_wayne
    "juno" -> R.drawable.bot_juno
    "bramble" -> R.drawable.bot_bramble
    "ziggy" -> R.drawable.bot_ziggy
    "marlo" -> R.drawable.bot_marlo
    "tessa" -> R.drawable.bot_tessa
    "kofi" -> R.drawable.bot_kofi
    "ingrid" -> R.drawable.bot_ingrid
    "nova" -> R.drawable.bot_nova
    "titan" -> R.drawable.bot_titan
    "bolt" -> R.drawable.bot_bolt
    else -> null
}

/** The ring colour around a bot's face: it rises with strength, from lime to coral. */
@Composable
@ReadOnlyComposable
fun botAccent(rating: Int): Color = when {
    rating < 900 -> CtColors.Lime
    rating < 1500 -> CtColors.Teal
    rating < 2100 -> CtColors.Gold
    rating < 2600 -> CtColors.Violet
    else -> CtColors.Coral
}

/** A bot's face on a disc with a coloured ring. Decorative: the name is always shown beside it. */
@Composable
fun BotAvatar(
    botId: String,
    rating: Int,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    accent: Color = botAccent(rating),
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(CtColors.SurfaceHigh)
            .border(size * 0.05f, accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val art = botArt(botId)
        if (art != null) {
            Image(
                painter = painterResource(art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().padding(size * 0.05f).clip(CircleShape),
            )
        } else {
            Icon(Icons.Rounded.SmartToy, contentDescription = null, tint = accent, modifier = Modifier.size(size * 0.55f))
        }
    }
}
