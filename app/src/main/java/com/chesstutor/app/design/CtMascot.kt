package com.chesstutor.app.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chesstutor.app.domain.Mascot
import com.chesstutor.app.domain.MascotMood

/** The coach's drawing in a [mood]. Decorative by default, because its name is usually shown next to it. */
@Composable
fun CtMascotArt(
    mascot: Mascot,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
    mood: MascotMood = MascotMood.READY,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(mascot.art(mood)),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.height(height),
    )
}

/**
 * The coach speaking (plan section 6.3.1): the player's chosen mascot beside a speech bubble
 * whose tail points at it (D41). Screen readers hear it as one item: the mascot's name, then [text].
 */
@Composable
fun CtCoachBubble(
    mascot: Mascot,
    text: String,
    modifier: Modifier = Modifier,
    mood: MascotMood = MascotMood.READY,
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.xs),
    ) {
        CtMascotArt(mascot = mascot, height = 52.dp, mood = mood, modifier = Modifier.width(52.dp))
        CtSpeechBubble(modifier = Modifier.widthIn(max = 560.dp), tail = CtTail.Start) {
            Column {
                Text(text = mascot.displayName, style = CtType.Caption.copy(color = CtColors.Accent))
                Text(text = text, style = CtType.Chat)
            }
        }
    }
}

/**
 * The coach beside a board (plan section 4.2): a small mascot in a [mood], and a tailed bubble
 * with an optional [label] such as "You play Black" above one line of coaching. Read as one
 * item by screen readers.
 */
@Composable
fun CtCoachStrip(
    mascot: Mascot,
    text: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    mood: MascotMood = MascotMood.READY,
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.xs),
    ) {
        CtMascotArt(mascot = mascot, height = 44.dp, mood = mood, modifier = Modifier.width(44.dp))
        CtSpeechBubble(modifier = Modifier.weight(1f), tail = CtTail.Start) {
            Column {
                Text(text = label ?: mascot.displayName, style = CtType.Caption.copy(color = CtColors.Accent))
                Text(text = text, style = CtType.BodySmall.copy(color = CtColors.Text))
            }
        }
    }
}
