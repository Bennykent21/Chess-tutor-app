package com.chesstutor.app.feature.account

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chesstutor.app.R
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSheet
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.account.Badge
import com.chesstutor.app.domain.account.BadgeKind
import com.chesstutor.app.domain.account.BadgeTier

/** A medal's picture: the badge's kind in the metal of its rank. */
@DrawableRes
private fun medal(badge: Badge): Int = when (badge.kind) {
    BadgeKind.GAMES -> byTier(badge.tier, R.drawable.medal_cup_bronze, R.drawable.medal_cup_silver, R.drawable.medal_cup_gold)
    BadgeKind.PUZZLES -> byTier(badge.tier, R.drawable.medal_puzzle_bronze, R.drawable.medal_puzzle_silver, R.drawable.medal_puzzle_gold)
    BadgeKind.LEARNING -> byTier(badge.tier, R.drawable.medal_cap_bronze, R.drawable.medal_cap_silver, R.drawable.medal_cap_gold)
    BadgeKind.REVIEW -> byTier(badge.tier, R.drawable.medal_chart_bronze, R.drawable.medal_chart_silver, R.drawable.medal_chart_gold)
}

private fun byTier(tier: BadgeTier, bronze: Int, silver: Int, gold: Int): Int = when (tier) {
    BadgeTier.BRONZE -> bronze
    BadgeTier.SILVER -> silver
    BadgeTier.GOLD -> gold
}

/** A medal not yet earned has no shine: the colour goes and it fades back. */
private val Unearned = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

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

/** The badge's shape in polished metal once earned, dull before. */
@Composable
private fun BadgeMedal(badge: Badge, size: Dp) {
    Image(
        painter = painterResource(medal(badge)),
        contentDescription = null,
        colorFilter = if (badge.earned) null else Unearned,
        modifier = Modifier.size(size),
    )
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
