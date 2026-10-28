package com.chesstutor.app.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSheet
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.account.Badge
import com.chesstutor.app.domain.account.BadgeKind

internal fun badgeIcon(kind: BadgeKind): ImageVector = when (kind) {
    BadgeKind.GAMES -> Icons.Rounded.EmojiEvents
    BadgeKind.PUZZLES -> Icons.Rounded.Extension
    BadgeKind.LEARNING -> Icons.Rounded.School
    BadgeKind.REVIEW -> Icons.Rounded.Insights
}

/** Account > Overview's badges (plan 5.3) in one sideways row, earned ones first; a tap shows one. */
@Composable
fun BadgesRow(badges: List<Badge>, onOpen: (Badge) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        CtSectionHeader(title = "Badges", modifier = Modifier.padding(horizontal = CtSpacing.gutter))
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = CtSpacing.gutter, vertical = CtSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            badges.forEach { badge -> BadgeTile(badge, onClick = { onOpen(badge) }) }
        }
    }
}

@Composable
private fun BadgeTile(badge: Badge, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(74.dp)
            .alpha(if (badge.earned) 1f else 0.55f)
            .ctPressable(onClickLabel = "Show the badge", onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${badge.title}: ${badge.detail}. " + if (badge.earned) "Earned." else "${badge.progress} of ${badge.goal}."
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BadgeMedal(badge, size = 56.dp)
        Text(
            text = badge.title,
            style = CtType.NavLabel.copy(color = CtColors.Text2),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A round medal: raised with an accent ring once earned, flat before. */
@Composable
private fun BadgeMedal(badge: Badge, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CtShapes.Pill)
            .background(if (badge.earned) CtColors.Raised else CtColors.Surface)
            .then(if (badge.earned) Modifier.border(1.5.dp, CtColors.Accent, CtShapes.Pill) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = badgeIcon(badge.kind),
            contentDescription = null,
            tint = if (badge.earned) CtColors.Accent else CtColors.Text2,
            modifier = Modifier.size(size * 0.43f),
        )
    }
}

/** One badge up close: what it is for and, until earned, how far along the player is. */
@Composable
fun BadgeSheet(badge: Badge?, onDismiss: () -> Unit) {
    // Keep the last badge on screen while the sheet slides away.
    var shown by remember { mutableStateOf(badge) }
    if (badge != null) shown = badge
    CtSheet(visible = badge != null, onDismiss = onDismiss) {
        shown?.let { b ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.lg)) {
                BadgeMedal(b, size = 64.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CtSpacing.xxs)) {
                    Text(b.title, style = CtType.Headline)
                    Text(b.detail, style = CtType.BodySmall)
                }
            }
            if (!b.earned && b.goal > 1) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                    CtProgressBar(progress = b.progress.toFloat() / b.goal, modifier = Modifier.weight(1f))
                    Text("${b.progress} / ${b.goal}", style = CtType.Caption)
                }
            }
        }
    }
}
