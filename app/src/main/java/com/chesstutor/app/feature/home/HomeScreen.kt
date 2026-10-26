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
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.School
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
import com.chesstutor.app.design.CtMascotArt
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtGradients
import com.chesstutor.app.design.CtIconBubble
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctEnter
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.domain.Mascot
import com.chesstutor.app.domain.bot.RosterBot
import com.chesstutor.app.domain.game.NextStep
import com.chesstutor.app.navigation.StartingLevel
import com.chesstutor.app.navigation.needsWelcome
import com.chesstutor.app.viewmodel.AppUiState

/** What Home can open. */
data class HomeActions(
    val onQuickMatch: () -> Unit,
    val onResume: () -> Unit,
    val onOpenBot: (String) -> Unit,
    val onNewBot: () -> Unit,
    val onOpeningBattle: () -> Unit,
    val onFromPosition: () -> Unit,
    val onWatch: () -> Unit,
    val onContinueLearning: () -> Unit,
    val onDailyPuzzle: () -> Unit,
    val onOpenEngineHealth: () -> Unit,
    /** The welcome card's answer (D30: it replaced onboarding). */
    val onStartingLevel: (StartingLevel) -> Unit = {},
    /** Opens one lesson in Learn's player (the Continue card). */
    val onOpenLesson: (String) -> Unit = {},
    /** Puzzles > Mistakes review (the Recommended card). */
    val onReviewMistakes: () -> Unit = {},
    /** Puzzles with one theme (the Recommended card). */
    val onPractiseTheme: (String) -> Unit = {},
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
            // D28: one slim header row; the coach speaks once, inside the Continue card.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = gutter.ctEnter(0)) {
                Text(
                    text = if (name != null) "Hi, $name" else "Ready to play?",
                    style = CtType.Headline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (home.dayStreak > 0) {
                    CtChip(text = "${home.dayStreak}", color = CtColors.Coral, icon = Icons.Rounded.LocalFireDepartment)
                    Spacer(Modifier.width(CtSpacing.sm))
                }
                CtChip(text = "${state.effectiveUserRating}", color = CtColors.Gold, icon = Icons.Rounded.EmojiEvents)
            }

            if (needsWelcome(state.assessmentState)) {
                WelcomeCard(state.appSettings.mascot, actions.onStartingLevel, gutter.fillMaxWidth().ctEnter(1))
            }
            ContinueCard(state, home, actions, gutter.fillMaxWidth().ctEnter(1))

            Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md), modifier = gutter.ctEnter(2)) {
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
                home.nextStep?.let { step -> RecommendedCard(step, actions, Modifier.weight(1f)) }
            }

            if (state.engineStatus.isDegraded) {
                CtCard(
                    onClick = actions.onOpenEngineHealth,
                    onClickLabel = "Open engine health",
                    modifier = gutter.fillMaxWidth().ctEnter(2),
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
                PlayTile(
                    title = "Opening battle",
                    subtitle = "Play or drill one opening",
                    icon = Icons.Rounded.Flag,
                    tint = CtColors.Teal,
                    onClick = actions.onOpeningBattle,
                    modifier = Modifier.weight(1f),
                    buttonText = "Battle",
                )
                PlayTile(
                    title = "From a position",
                    subtitle = "Set up any position",
                    icon = Icons.Rounded.GridOn,
                    tint = CtColors.Lime,
                    onClick = actions.onFromPosition,
                    modifier = Modifier.weight(1f),
                    buttonText = "Set up",
                )
            }

            CtSectionHeader(title = "Meet the bots", actionLabel = "Watch two", onAction = actions.onWatch, modifier = gutter.ctEnter(6))
            LazyRow(
                contentPadding = PaddingValues(horizontal = CtSpacing.gutter),
                horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
                modifier = Modifier.ctEnter(6),
            ) {
                items(home.roster, key = { it.id }) { bot -> BotCard(bot, onClick = { actions.onOpenBot(bot.id) }) }
            }

            if (home.myBots.isNotEmpty()) {
                CtSectionHeader(title = "My bots", actionLabel = "New", onAction = actions.onNewBot, modifier = gutter.ctEnter(7))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = CtSpacing.gutter),
                    horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
                    modifier = Modifier.ctEnter(7),
                ) {
                    items(home.myBots, key = { it.id }) { mine ->
                        val bot = mine.asRosterBot()
                        BotCard(bot, onClick = { actions.onOpenBot(bot.id) })
                    }
                }
            }
            Spacer(Modifier.height(CtSpacing.lg))
        }
    }
}

/** The "Recommended" card: mistakes to fix, a weak theme to practise, or a bot to play. */
@Composable
private fun RecommendedCard(step: NextStep, actions: HomeActions, modifier: Modifier) {
    val onClick: () -> Unit = when (step) {
        is NextStep.Mistakes -> actions.onReviewMistakes
        is NextStep.Theme -> { -> actions.onPractiseTheme(step.score.theme) }
        is NextStep.Bot -> { -> actions.onOpenBot(step.recommendation.bot.id) }
    }
    val label = when (step) {
        is NextStep.Mistakes -> "Review your mistakes"
        is NextStep.Theme -> "Practise ${step.score.name} puzzles"
        is NextStep.Bot -> "Open ${step.recommendation.bot.name}"
    }
    CtCard(onClick = onClick, onClickLabel = label, modifier = modifier) {
        when (step) {
            is NextStep.Bot -> BotAvatar(step.recommendation.bot.profile.face, step.recommendation.bot.rating, size = 36.dp)
            is NextStep.Mistakes -> CtIconBubble(icon = Icons.Rounded.Replay, tint = CtColors.Coral, size = 36.dp)
            is NextStep.Theme -> CtIconBubble(icon = Icons.Rounded.Extension, tint = CtColors.Teal, size = 36.dp)
        }
        Spacer(Modifier.height(CtSpacing.sm))
        Text("Recommended", style = CtType.TitleSmall, maxLines = 1)
        Text(step.reason, style = CtType.Caption, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** The coach's line: a nudge to resume, then the recommendation. */
internal fun coachLine(home: HomeUiState): String = when {
    home.resume != null -> "Your game against ${home.resume.bot.name} is waiting. Shall we finish it?"
    home.gamesPlayed == 0 -> "Fancy a game? Pick a bot below, or try a quick match."
    home.recommendation != null -> home.recommendation.reason
    else -> "Fancy a game? I'll be cheering you on."
}

/**
 * The coach and the next thing to do, in one card (D28): resume the unfinished game, or else
 * pick up learning. The coach's line is said here once.
 */
@Composable
private fun ContinueCard(state: AppUiState, home: HomeUiState, actions: HomeActions, modifier: Modifier) {
    val resume = home.resume
    val mascot = state.appSettings.mascot
    CtCard(
        background = CtGradients.Teal,
        onClick = when {
            resume != null -> actions.onResume
            home.nextLesson != null -> { -> actions.onOpenLesson(home.nextLesson.lessonId) }
            else -> actions.onContinueLearning
        },
        onClickLabel = if (resume != null) "Resume your game" else "Continue learning",
        contentPadding = PaddingValues(horizontal = CtSpacing.md, vertical = CtSpacing.md),
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CtMascotArt(mascot = mascot, height = 52.dp, modifier = Modifier.width(48.dp))
            Spacer(Modifier.width(CtSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = mascot.displayName, style = CtType.Caption.copy(color = CtColors.Teal))
                if (resume != null) {
                    Text(text = coachLine(home), style = CtType.Body.copy(color = CtColors.Text), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val side = if (resume.playerColor == PieceColor.WHITE) "White" else "Black"
                    Text(text = "Move ${resume.moveCount / 2 + 1} · you play $side", style = CtType.Caption.copy(color = CtColors.Text2))
                } else {
                    val lesson = home.nextLesson
                    Text(
                        text = lesson?.let { (if (it.started) "Pick up where you left off: " else "Next up: ") + it.lessonTitle } ?: coachLine(home),
                        style = CtType.Body.copy(color = CtColors.Text),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = lesson?.let { "${it.courseTitle} · lesson ${it.number} of ${it.total}" } ?: "Continue learning",
                        style = CtType.Caption.copy(color = CtColors.Text2),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (resume != null) BotAvatar(resume.bot.profile.face, resume.bot.rating, size = 40.dp)
            else CtIconBubble(icon = if (home.nextLesson != null) Icons.Rounded.School else Icons.Rounded.Lightbulb, tint = CtColors.Teal, size = 40.dp)
        }
    }
}

/**
 * The first-run welcome (D30, instead of onboarding): one question, three answers. New
 * players go to Learn's basics; online players go to Account to link their account.
 */
@Composable
private fun WelcomeCard(mascot: Mascot, onChoose: (StartingLevel) -> Unit, modifier: Modifier) {
    CtCard(background = CtGradients.Violet, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CtMascotArt(mascot = mascot, height = 52.dp, modifier = Modifier.width(48.dp))
            Spacer(Modifier.width(CtSpacing.md))
            Column(Modifier.weight(1f)) {
                Text("Welcome!", style = CtType.Title)
                Text("Where should we start?", style = CtType.BodySmall)
            }
        }
        Spacer(Modifier.height(CtSpacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
            StartingLevel.entries.forEach { level ->
                CtListRow(
                    title = level.label,
                    subtitle = level.detail,
                    icon = when (level) {
                        StartingLevel.NEW -> Icons.Rounded.School
                        StartingLevel.RULES -> Icons.Rounded.SportsEsports
                        StartingLevel.PLAYER -> Icons.Rounded.Link
                    },
                    onClick = { onChoose(level) },
                )
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
