package com.chesstutor.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.design.CtBadge
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtCoachBubble
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtGradients
import com.chesstutor.app.design.CtIconBubble
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctEnter
import com.chesstutor.app.domain.bot.RosterBot
import com.chesstutor.app.viewmodel.AppUiState

/** What Home can open. */
data class HomeActions(
    val onQuickMatch: () -> Unit,
    val onResume: () -> Unit,
    val onOpenBot: (String) -> Unit,
    val onNewBot: () -> Unit,
    val onContinueLearning: () -> Unit,
    val onDailyPuzzle: () -> Unit,
    val onOpenEngineHealth: () -> Unit,
)

/** Home: the play hub (plan section 6.1). No board on screen. */
@Composable
fun HomeScreen(
    state: AppUiState,
    home: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val name = state.linkedProfile?.username
    CtScreenBackground(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = CtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
        ) {
            val gutter = Modifier.padding(horizontal = CtSpacing.gutter)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = gutter.ctEnter(0)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (name != null) "Hi, $name" else "Ready to play?",
                        style = CtType.Display,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(text = "Choose what to play today.", style = CtType.Body)
                }
                if (home.dayStreak > 0) {
                    CtChip(text = "${home.dayStreak}", color = CtColors.Coral, icon = Icons.Rounded.LocalFireDepartment)
                    Spacer(Modifier.width(CtSpacing.sm))
                }
                CtChip(text = "${state.effectiveUserRating}", color = CtColors.Gold, icon = Icons.Rounded.EmojiEvents)
            }

            CtCoachBubble(
                mascot = state.appSettings.mascot,
                text = coachLine(home),
                modifier = gutter.fillMaxWidth().ctEnter(1),
            )

            if (state.engineStatus.isDegraded) {
                CtCard(
                    onClick = actions.onOpenEngineHealth,
                    onClickLabel = "Open engine health",
                    modifier = gutter.fillMaxWidth().ctEnter(1),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CtBadge(text = "Lite engine", icon = Icons.Rounded.Warning)
                        Spacer(Modifier.width(CtSpacing.sm))
                        Text(
                            text = "Stockfish is not running, so bots and analysis are weaker. Tap for details.",
                            style = CtType.BodySmall,
                        )
                    }
                }
            }

            ContinueCard(state, home, actions, gutter.fillMaxWidth().ctEnter(2))

            CtSectionHeader(title = "Play", modifier = gutter.ctEnter(3))
            Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md), modifier = gutter.ctEnter(4)) {
                val opponent = home.recommendation?.bot
                PlayTile(
                    title = "Quick match",
                    subtitle = opponent?.let { "vs ${it.name} · ${it.rating}" } ?: "A fair opponent, now",
                    icon = Icons.Rounded.SportsEsports,
                    tint = CtColors.Gold,
                    onClick = actions.onQuickMatch,
                    modifier = Modifier.weight(1f),
                )
                PlayTile(
                    title = "Custom bot",
                    subtitle = "Build your own opponent",
                    icon = Icons.Rounded.Tune,
                    tint = CtColors.Violet,
                    onClick = actions.onNewBot,
                    modifier = Modifier.weight(1f),
                    buttonText = "Build",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md), modifier = gutter.ctEnter(5)) {
                PlannedTile("Opening battle", "Play or drill one opening", Icons.Rounded.Flag, Modifier.weight(1f))
                PlannedTile("From a position", "Set up any position", Icons.Rounded.GridOn, Modifier.weight(1f))
            }

            CtSectionHeader(title = "Meet the bots", modifier = gutter.ctEnter(6))
            LazyRow(
                contentPadding = PaddingValues(horizontal = CtSpacing.gutter),
                horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
                modifier = Modifier.ctEnter(6),
            ) {
                items(home.roster, key = { it.id }) { bot -> BotCard(bot, onClick = { actions.onOpenBot(bot.id) }) }
            }

            if (home.myBots.isNotEmpty()) {
                CtSectionHeader(title = "My bots", actionLabel = "New", onAction = actions.onNewBot, modifier = gutter.ctEnter(6))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = CtSpacing.gutter),
                    horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
                    modifier = Modifier.ctEnter(6),
                ) {
                    items(home.myBots, key = { it.id }) { mine ->
                        val bot = mine.asRosterBot()
                        BotCard(bot, onClick = { actions.onOpenBot(bot.id) })
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md), modifier = gutter.ctEnter(7)) {
                CtCard(
                    onClick = actions.onDailyPuzzle,
                    onClickLabel = "Open the daily puzzle",
                    background = CtGradients.Violet,
                    modifier = Modifier.weight(1f),
                ) {
                    CtIconBubble(icon = Icons.Rounded.Bolt, tint = CtColors.Gold, size = 36.dp)
                    Spacer(Modifier.height(CtSpacing.sm))
                    Text("Daily challenge", style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (home.dailySolved) "Solved today. Well done!" else "Today's puzzle is waiting", style = CtType.Caption.copy(color = CtColors.Text2), maxLines = 2)
                }
                home.recommendation?.let { rec ->
                    CtCard(
                        onClick = { actions.onOpenBot(rec.bot.id) },
                        onClickLabel = "Open ${rec.bot.name}",
                        modifier = Modifier.weight(1f),
                    ) {
                        BotAvatar(rec.bot.profile.face, rec.bot.rating, size = 36.dp)
                        Spacer(Modifier.height(CtSpacing.sm))
                        Text("Recommended", style = CtType.TitleSmall, maxLines = 1)
                        Text(rec.reason, style = CtType.Caption, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(CtSpacing.lg))
        }
    }
}

/** The coach's line: a nudge to resume, then the recommendation. */
internal fun coachLine(home: HomeUiState): String = when {
    home.resume != null -> "Your game against ${home.resume.bot.name} is waiting. Shall we finish it?"
    home.gamesPlayed == 0 -> "Fancy a game? Pick a bot below, or try a quick match."
    home.recommendation != null -> home.recommendation.reason
    else -> "Fancy a game? I'll be cheering you on."
}

@Composable
private fun ContinueCard(state: AppUiState, home: HomeUiState, actions: HomeActions, modifier: Modifier) {
    val resume = home.resume
    if (resume != null) {
        CtCard(background = CtGradients.Teal, onClick = actions.onResume, onClickLabel = "Resume your game", modifier = modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BotAvatar(resume.bot.profile.face, resume.bot.rating, size = 48.dp)
                Spacer(Modifier.width(CtSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Continue", style = CtType.Caption.copy(color = CtColors.Teal))
                    Text(text = "Your game vs ${resume.bot.name}", style = CtType.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val side = if (resume.playerColor == PieceColor.WHITE) "White" else "Black"
                    Text(text = "Move ${resume.moveCount / 2 + 1} · you play $side", style = CtType.BodySmall)
                }
                CtIconBubble(icon = Icons.Rounded.PlayArrow, tint = CtColors.Teal)
            }
        }
        return
    }
    CtCard(background = CtGradients.Teal, onClick = actions.onContinueLearning, onClickLabel = "Continue learning", modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CtIconBubble(icon = Icons.Rounded.Lightbulb, tint = CtColors.Teal)
            Spacer(Modifier.width(CtSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Continue", style = CtType.Caption.copy(color = CtColors.Teal))
                Text(
                    text = state.trainingRecommendation.ifBlank { "Pick up your next lesson" },
                    style = CtType.Title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.trainingRecommendationReason.isNotBlank()) {
                    Text(text = state.trainingRecommendationReason, style = CtType.BodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun BotCard(bot: RosterBot, onClick: () -> Unit) {
    CtCard(
        onClick = onClick,
        onClickLabel = "Open ${bot.name}, rated ${bot.rating}",
        contentPadding = PaddingValues(CtSpacing.md),
        modifier = Modifier.width(112.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BotAvatar(bot.profile.face, bot.rating, size = 64.dp)
            Spacer(Modifier.height(CtSpacing.sm))
            Text(bot.name, style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${bot.rating}", style = CtType.Label.copy(color = botAccent(bot.rating)))
            Text(bot.profile.style.label, style = CtType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PlayTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier,
    buttonText: String = "Play",
) {
    CtCard(onClick = onClick, onClickLabel = title, background = CtGradients.Card, modifier = modifier) {
        CtIconBubble(icon = icon, tint = tint, size = 36.dp)
        Spacer(Modifier.height(CtSpacing.sm))
        Text(text = title, style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = subtitle, style = CtType.Caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(CtSpacing.sm))
        CtButton(text = buttonText, icon = Icons.Rounded.PlayArrow.takeIf { buttonText == "Play" }, onClick = onClick, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PlannedTile(title: String, subtitle: String, icon: ImageVector, modifier: Modifier) {
    CtCard(modifier = modifier) {
        CtIconBubble(icon = icon, tint = CtColors.Violet, size = 36.dp)
        Spacer(Modifier.height(CtSpacing.sm))
        Text(text = title, style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = subtitle, style = CtType.Caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(CtSpacing.sm))
        // The shared "Coming in Phase N" chip is too wide for a half-width tile.
        CtChip(text = "Coming soon", color = CtColors.Violet, icon = Icons.Rounded.Lock)
    }
}
