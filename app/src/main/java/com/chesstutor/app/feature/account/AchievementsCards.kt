package com.chesstutor.app.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Text
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtIconBubble
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.account.ActivityItem
import com.chesstutor.app.domain.account.Badge
import com.chesstutor.app.domain.account.BadgeKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun badgeIcon(kind: BadgeKind): ImageVector = when (kind) {
    BadgeKind.GAMES -> Icons.Rounded.EmojiEvents
    BadgeKind.PUZZLES -> Icons.Rounded.Extension
    BadgeKind.LEARNING -> Icons.Rounded.School
    BadgeKind.REVIEW -> Icons.Rounded.Insights
}

@Composable
@ReadOnlyComposable
internal fun badgeColor(kind: BadgeKind): Color = when (kind) {
    BadgeKind.GAMES -> CtColors.Gold
    BadgeKind.PUZZLES -> CtColors.Teal
    BadgeKind.LEARNING -> CtColors.Lime
    BadgeKind.REVIEW -> CtColors.Violet
}

/** Account > Overview's badges (plan 5.3): earned ones first, the rest with how far along they are. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgesCard(badges: List<Badge>, modifier: Modifier = Modifier) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val earned = badges.count { it.earned }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        CtSectionHeader(
            title = "Badges · $earned of ${badges.size}",
            actionLabel = if (badges.size > SHOWN) (if (showAll) "Fewer" else "All") else null,
            onAction = { showAll = !showAll },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm), modifier = Modifier.fillMaxWidth()) {
            (if (showAll) badges else badges.take(SHOWN)).forEach { badge -> BadgeTile(badge) }
        }
    }
}

private const val SHOWN = 4

@Composable
private fun BadgeTile(badge: Badge) {
    val tint = if (badge.earned) badgeColor(badge.kind) else CtColors.Text3
    CtCard(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(CtSpacing.sm),
        modifier = Modifier
            .widthIn(min = 150.dp, max = 170.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${badge.title}: ${badge.detail}. " + if (badge.earned) "Earned." else "${badge.progress} of ${badge.goal}."
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CtIconBubble(icon = if (badge.earned) badgeIcon(badge.kind) else Icons.Rounded.Lock, tint = tint, size = 32.dp)
            Spacer(Modifier.width(CtSpacing.sm))
            Column {
                Text(badge.title, style = CtType.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(badge.detail, style = CtType.Caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!badge.earned && badge.goal > 1) {
            Spacer(Modifier.height(CtSpacing.sm))
            CtProgressBar(progress = badge.progress.toFloat() / badge.goal, color = badgeColor(badge.kind), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Account > Overview's recent activity (plan 6.4): games, reviews, lessons and puzzles, newest first. */
@Composable
fun RecentActivityCard(items: List<ActivityItem>, modifier: Modifier = Modifier, zone: ZoneId = ZoneId.systemDefault()) {
    if (items.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        CtSectionHeader(title = "Recent activity")
        CtCard(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { i, item ->
                if (i > 0) Spacer(Modifier.height(CtSpacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    CtIconBubble(icon = badgeIcon(item.kind), tint = badgeColor(item.kind), size = 28.dp)
                    Spacer(Modifier.width(CtSpacing.sm))
                    Text(item.text, style = CtType.BodySmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(CtSpacing.sm))
                    Text(dayLabel(item.at, zone), style = CtType.Caption)
                }
            }
        }
    }
}

private val DAY = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

internal fun dayLabel(at: Long, zone: ZoneId): String = DAY.format(Instant.ofEpochMilli(at).atZone(zone))
