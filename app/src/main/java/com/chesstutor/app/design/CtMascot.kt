package com.chesstutor.app.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chesstutor.app.domain.Mascot

/** The mascot's artwork. Decorative by default, because its name is usually shown next to it. */
@Composable
fun CtMascotArt(
    mascot: Mascot,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(mascot.art),
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
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.xs),
    ) {
        // Fixed width (Sage's art is the widest), so bubbles line up whichever coach speaks.
        CtMascotArt(mascot = mascot, height = 60.dp, modifier = Modifier.width(56.dp))
        CtSpeechBubble(modifier = Modifier.widthIn(max = 520.dp), tail = CtTail.Start) {
            Column {
                Text(text = mascot.displayName, style = CtType.Caption.copy(color = CtColors.Accent))
                Text(text = text, style = CtType.Body.copy(color = CtColors.Text))
            }
        }
    }
}

/**
 * The coach strip above a board (plan section 4.2): a small mascot, an optional [label]
 * such as "You play Black", and one line of coaching. Read as one item by screen readers.
 */
@Composable
fun CtCoachStrip(
    mascot: Mascot,
    text: String,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Row(
        modifier = modifier
            .clip(CtShapes.CardSmall)
            .background(CtColors.SurfaceRaised)
            .padding(horizontal = CtSpacing.md, vertical = CtSpacing.sm)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
    ) {
        CtMascotArt(mascot = mascot, height = 40.dp, modifier = Modifier.width(38.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label ?: mascot.displayName, style = CtType.Caption.copy(color = CtColors.Gold))
            Text(text = text, style = CtType.BodySmall.copy(color = CtColors.Text))
        }
    }
}
