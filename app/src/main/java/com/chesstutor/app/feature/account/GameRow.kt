package com.chesstutor.app.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chesstutor.app.R
import com.chesstutor.app.data.model.GameSource
import com.chesstutor.app.design.ctPressable
import java.time.temporal.ChronoUnit
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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
 * One game in a list (Account and Games): a result square, the opponent, the clock's icon with
 * the opening and day, and the player's accuracy once the game is reviewed (a dash before).
 */
@Composable
fun GameListRow(
    game: GameSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accuracy: Double? = null,
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ClockIcon(game.timeClass, size = 14.dp)
                    Text(gameLine(game, now), style = CtType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                text = accuracy?.let(::accuracyText) ?: "–",
                style = CtType.Label.copy(color = if (accuracy != null) CtColors.Text else CtColors.Text2),
                modifier = if (accuracy == null) Modifier.semantics { contentDescription = "Not reviewed" } else Modifier,
            )
        }
    }
}

/** A result as a picture: a crown, a handshake or a toppled king. */
@Composable
internal fun outcomeIcon(outcome: GameOutcome): ImageVector = when (outcome) {
    GameOutcome.WIN -> ImageVector.vectorResource(R.drawable.ic_result_win)
    GameOutcome.LOSS -> ImageVector.vectorResource(R.drawable.ic_result_loss)
    GameOutcome.DRAW -> Icons.Rounded.Handshake
    GameOutcome.UNKNOWN -> Icons.Rounded.Remove
}

/** A result's colour on the page; a draw is neutral. */
@Composable
@ReadOnlyComposable
internal fun outcomeColor(outcome: GameOutcome): Color = when (outcome) {
    GameOutcome.WIN -> CtColors.Success
    GameOutcome.LOSS -> CtColors.Error
    else -> CtColors.Text2
}

/** The result's picture on the result's colour. */
@Composable
internal fun ResultSquare(outcome: GameOutcome, size: Dp = 32.dp) {
    val fill = when (outcome) {
        GameOutcome.WIN, GameOutcome.LOSS -> outcomeColor(outcome)
        else -> CtColors.Track
    }
    val ink = when (outcome) {
        GameOutcome.WIN -> CtColors.OnSuccess
        GameOutcome.LOSS -> CtColors.OnError
        else -> CtColors.Text
    }
    val spoken = when (outcome) {
        GameOutcome.WIN -> "Won"
        GameOutcome.LOSS -> "Lost"
        GameOutcome.DRAW -> "Drawn"
        GameOutcome.UNKNOWN -> "Unfinished"
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(fill)
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Icon(outcomeIcon(outcome), contentDescription = null, tint = ink, modifier = Modifier.size(size * 0.66f))
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

private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)

internal fun formatDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    dateFormat.format(Instant.ofEpochMilli(millis).atZone(zone))
