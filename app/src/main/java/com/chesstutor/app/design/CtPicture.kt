package com.chesstutor.app.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.R

/** Words on a picture are the same in every theme: the picture darkens under them. */
private val PictureInk = Color.White
private val Shade = Color.Black

/** A board is busier than a face, so it fades nearly to black behind the words. */
enum class CtFade(val brush: Brush) {
    Soft(Brush.verticalGradient(0.45f to Shade.copy(alpha = 0f), 0.72f to Shade.copy(alpha = 0.34f), 1f to Shade.copy(alpha = 0.78f))),
    Strong(Brush.verticalGradient(0.24f to Shade.copy(alpha = 0f), 0.5f to Shade.copy(alpha = 0.55f), 0.72f to Shade.copy(alpha = 0.92f), 1f to Shade.copy(alpha = 0.96f))),
}

/**
 * A tile the [picture] fills (D53): the page stays quiet and the colour comes from the chess art.
 * The picture is decoration, so a screen reader hears only the words. Give the tile its size.
 */
@Composable
fun CtPictureTile(
    title: String,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    subtitle: String? = null,
    progress: Float? = null,
    fade: CtFade = CtFade.Strong,
    compact: Boolean = false,
    titleStyle: TextStyle = if (compact) CtType.Label else CtType.TitleSmall,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    picture: @Composable BoxScope.() -> Unit,
) {
    val base = modifier.clip(if (compact) CtShapes.CardSmall else CtShapes.Card).background(CtColors.Surface)
    Box(if (onClick != null) base.ctPressable(onClickLabel = onClickLabel, onClick = onClick) else base) {
        Box(Modifier.matchParentSize().clearAndSetSemantics {}, content = picture)
        Box(Modifier.matchParentSize().background(fade.brush))
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) CtSpacing.sm else CtSpacing.md),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                if (kicker != null) Text(kicker, style = CtType.Caption.copy(color = PictureInk), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(title, style = titleStyle.copy(color = PictureInk), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = CtType.Caption.copy(color = PictureInk), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (progress != null) {
                    Box(Modifier.padding(top = CtSpacing.sm).fillMaxWidth().height(3.dp).clip(CtShapes.Pill).background(PictureInk.copy(alpha = 0.3f))) {
                        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(PictureInk))
                    }
                }
            }
            trailing?.invoke()
        }
    }
}

/** The round play mark on a picture. The whole tile is the tap target; this only marks it. */
@Composable
fun CtPlayMark() {
    Box(Modifier.size(36.dp).clip(CtShapes.Pill).background(PictureInk), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Shade, modifier = Modifier.size(20.dp))
    }
}

/** The tick on a picture once something is done. */
@Composable
fun CtTickMark() {
    Box(Modifier.size(24.dp).clip(CtShapes.Pill).background(PictureInk), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = Shade, modifier = Modifier.size(16.dp))
    }
}

/** The day streak: a real flame while it lives, and a cold outline once it is lost (D53). */
@Composable
fun CtStreak(days: Int, modifier: Modifier = Modifier) {
    val lit = days > 0
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$days-day streak" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.xs),
    ) {
        if (lit) {
            Image(painterResource(R.drawable.ic_flame_lit), contentDescription = null, modifier = Modifier.size(24.dp))
        } else {
            Icon(painterResource(R.drawable.ic_flame_off), contentDescription = null, tint = CtColors.Text2, modifier = Modifier.size(24.dp))
        }
        Text("$days", style = CtType.TitleSmall.copy(color = if (lit) CtColors.Text else CtColors.Text2))
    }
}
