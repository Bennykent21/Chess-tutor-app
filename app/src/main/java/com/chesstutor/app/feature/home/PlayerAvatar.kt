package com.chesstutor.app.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chesstutor.app.data.settings.PlayerPhoto
import com.chesstutor.app.data.settings.PlayerPicture
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtMascotArt
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.Mascot
import java.util.Locale

/** The player's picture on a disc (D52); their initial, or a person, until they choose one. Decorative: the name sits beside it. */
@Composable
fun PlayerAvatar(picture: PlayerPicture, name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier.size(size).clip(CtShapes.Pill).background(CtColors.Surface),
        contentAlignment = Alignment.Center,
    ) {
        when (picture) {
            is PlayerPicture.Face -> botArt(picture.botId)?.let {
                Image(painterResource(it), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } ?: Initial(name, size)
            is PlayerPicture.Coach -> CtMascotArt(mascot = Mascot.fromId(picture.mascotId), height = size * 0.86f, modifier = Modifier.size(size * 0.86f))
            is PlayerPicture.Photo -> {
                val context = LocalContext.current
                val photo by produceState<ImageBitmap?>(null, picture.version) { value = PlayerPhoto.load(context)?.asImageBitmap() }
                photo?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) } ?: Initial(name, size)
            }
            PlayerPicture.None -> Initial(name, size)
        }
    }
}

@Composable
private fun Initial(name: String, size: Dp) {
    val letter = name.trim().take(1).uppercase(Locale.UK)
    if (letter.isNotEmpty()) {
        Text(letter, style = if (size < 48.dp) CtType.TitleSmall else CtType.Headline)
    } else {
        Icon(Icons.Rounded.Person, contentDescription = null, tint = CtColors.Text, modifier = Modifier.size(size / 2))
    }
}
