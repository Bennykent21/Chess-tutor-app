package com.chesstutor.app.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.data.model.GameSource
import com.chesstutor.app.design.CtDivider
import com.chesstutor.app.design.ctPressable
import java.time.temporal.ChronoUnit
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.chesstutor.app.design.CtBadge
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.account.GameOutcome
import com.chesstutor.app.domain.account.GameSummary
import com.chesstutor.app.domain.account.label
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One game in a list (Account > Overview and Games): a result square, the opponent, the
 * opening and day, and the player's accuracy once the game is reviewed (a dash before).
 */
@Composable
fun GameListRow(
    game: GameSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accuracy: Double? = null,
    divider: Boolean = true,
    now: Long = System.currentTimeMillis(),
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .ctPressable(onClickLabel = "Open the game") { onClick() }
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ResultSquare(game.outcome)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CtSpacing.xxs)) {
                Text(opponentName(game), style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(gameLine(game, now), style = CtType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = accuracy?.let(::accuracyText) ?: "–",
                style = CtType.Label.copy(color = if (accuracy != null) CtColors.Text else CtColors.Text2),
                modifier = if (accuracy == null) Modifier.semantics { contentDescription = "Not reviewed" } else Modifier,
            )
        }
        if (divider) CtDivider()
    }
}

/** W, L or ½ on the result's colour. */
@Composable
private fun ResultSquare(outcome: GameOutcome) {
    val fill = when (outcome) {
        GameOutcome.WIN -> CtColors.Success
        GameOutcome.LOSS -> CtColors.Error
        else -> CtColors.Track
    }
    val ink = when (outcome) {
        GameOutcome.WIN -> CtColors.OnSuccess
        GameOutcome.LOSS -> CtColors.OnError
        else -> CtColors.Text
    }
    val (text, spoken) = when (outcome) {
        GameOutcome.WIN -> "W" to "Won"
        GameOutcome.LOSS -> "L" to "Lost"
        GameOutcome.DRAW -> "½" to "Drawn"
        GameOutcome.UNKNOWN -> "–" to "Unfinished"
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(fill)
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = CtType.Label.copy(color = ink))
    }
}

/** A bot by its name; an online opponent by "@name". */
internal fun opponentName(game: GameSummary): String =
    if (game.source == GameSource.ARENA) game.opponent else "@${game.opponent}"

/** "Italian Game, today". */
internal fun gameLine(game: GameSummary, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val day = relativeDay(game.dateMillis, now, zone)
    val opening = game.openingFamily ?: if (game.startFen != null) "Set-up position" else null
    return if (opening == null) day else "$opening, $day"
}

/** "today", "yesterday", "3 days ago", then the date. */
internal fun relativeDay(millis: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (val days = ChronoUnit.DAYS.between(day, today)) {
        0L -> "today"
        1L -> "yesterday"
        in 2L..6L -> "$days days ago"
        else -> formatDate(millis, zone)
    }
}

@Composable
internal fun OutcomeBadge(outcome: GameOutcome, modifier: Modifier = Modifier) {
    when (outcome) {
        GameOutcome.WIN -> CtBadge(text = "Win", color = CtColors.Lime, contentColor = CtColors.OnLime, modifier = modifier)
        GameOutcome.LOSS -> CtBadge(text = "Loss", color = CtColors.Coral, contentColor = CtColors.OnCoral, modifier = modifier)
        GameOutcome.DRAW -> CtBadge(text = "Draw", color = CtColors.SurfaceHigh, contentColor = CtColors.Text, modifier = modifier)
        GameOutcome.UNKNOWN -> CtBadge(text = "–", color = CtColors.SurfaceHigh, contentColor = CtColors.Text, modifier = modifier)
    }
}

/** "Chess.com · Blitz · 34 moves · 12 Oct 2026". */
internal fun detailLine(game: GameSummary): String =
    "${game.source.label} · ${game.timeClass.label} · ${game.moveNumber} move${if (game.moveNumber == 1) "" else "s"} · ${formatDate(game.dateMillis)}"

private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)

internal fun formatDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    dateFormat.format(Instant.ofEpochMilli(millis).atZone(zone))
