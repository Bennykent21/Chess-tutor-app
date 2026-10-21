package com.chesstutor.app.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** Speech bubble whose sharp corner points down at the mascot. */
private val BubbleShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 4.dp)

/**
 * The coach speaking (plan section 6.3.1): the player's chosen mascot with a speech bubble.
 * Screen readers hear it as one item: the mascot's name, then [text].
 */
@Composable
fun CtCoachBubble(
    mascot: Mascot,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
    ) {
        // Fixed width (Sage's art is the widest), so bubbles line up whichever coach speaks.
        CtMascotArt(mascot = mascot, height = 72.dp, modifier = Modifier.width(66.dp))
        Column(
            modifier = Modifier
                .padding(bottom = CtSpacing.md)
                .widthIn(max = 520.dp)
                .clip(BubbleShape)
                .background(CtColors.SurfaceRaised)
                .border(1.dp, CtColors.Line, BubbleShape)
                .padding(horizontal = CtSpacing.lg, vertical = CtSpacing.md),
        ) {
            Text(text = mascot.displayName, style = CtType.Caption.copy(color = CtColors.Gold))
            Text(text = text, style = CtType.Body.copy(color = CtColors.Text))
        }
    }
}
