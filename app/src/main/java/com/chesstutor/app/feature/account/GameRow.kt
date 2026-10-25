package com.chesstutor.app.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.design.CtBadge
import com.chesstutor.app.design.CtCard
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

/** One game in a list: opponent, result, opening, where and when it was played, and accuracy once analysed. */
@Composable
fun GameRow(game: GameSummary, onClick: () -> Unit, modifier: Modifier = Modifier, accuracy: Double? = null) {
    CtCard(
        onClick = onClick,
        onClickLabel = "Open the game against ${game.opponent}",
        contentPadding = androidx.compose.foundation.layout.PaddingValues(CtSpacing.md),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutcomeBadge(game.outcome)
            Spacer(Modifier.width(CtSpacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CtSpacing.xxs)) {
                Text(
                    text = opponentLine(game),
                    style = CtType.TitleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = game.openingName ?: if (game.startFen != null) "From a set-up position" else "Opening not named",
                    style = CtType.BodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = detailLine(game), style = CtType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            accuracy?.let {
                Spacer(Modifier.width(CtSpacing.sm))
                Column(horizontalAlignment = Alignment.End) {
                    Text(accuracyText(it), style = CtType.Label.copy(color = CtColors.Teal))
                    Text("accuracy", style = CtType.Caption)
                }
            }
        }
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

/** "vs Magnus Bot (1500) · White". */
internal fun opponentLine(game: GameSummary): String {
    val rating = if (game.opponentRating > 0) " (${game.opponentRating})" else ""
    val side = if (game.userColor == PieceColor.WHITE) "White" else "Black"
    return "vs ${game.opponent}$rating · $side"
}

/** "Chess.com · Blitz · 34 moves · 12 Oct 2026". */
internal fun detailLine(game: GameSummary): String =
    "${game.source.label} · ${game.timeClass.label} · ${game.moveNumber} move${if (game.moveNumber == 1) "" else "s"} · ${formatDate(game.dateMillis)}"

private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)

internal fun formatDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    dateFormat.format(Instant.ofEpochMilli(millis).atZone(zone))
