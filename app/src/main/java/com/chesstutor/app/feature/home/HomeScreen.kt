package com.chesstutor.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.board.BoardView
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtHeroCard
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtMascotArt
import com.chesstutor.app.design.CtModeCard
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtScreenTitle
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTile
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.Mascot
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
    /** Explore (D32): any position, every move judged. */
    val onExplore: () -> Unit = {},
    /** Opens one lesson in Learn's player (the Continue card). */
    val onOpenLesson: (String) -> Unit = {},
    /** Puzzles > Mistakes review (the second tile). */
    val onReviewMistakes: () -> Unit = {},
    /** Puzzles with one theme (the second tile). */
    val onPractiseTheme: (String) -> Unit = {},
    /** The Bots page: every bot, your own, build one, watch two (D42). */
    val onOpenBots: () -> Unit = {},
)

/**
 * Home: the play hub (plan section 6.1, Phase 7D, canvas `Home2.dc.html`): a greeting with the
 * streak, the continue card (your game or your next lesson), Quick match with your rating, the
 * daily puzzle and the most useful next step, and more ways to play.
 */
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
                .padding(bottom = CtSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CtSpacing.lg),
        ) {
            CtScreenTitle(title = if (name != null) "Hi, $name" else "Ready to play?") {
                if (home.dayStreak > 0) {
                    CtChip(text = "${home.dayStreak}", icon = Icons.Rounded.LocalFireDepartment, modifier = Modifier.semantics { contentDescription = "${home.dayStreak} day streak" })
                }
            }
            val gutter = Modifier.padding(horizontal = CtSpacing.gutter)
            if (needsWelcome(state.assessmentState)) {
                WelcomeCard(state.appSettings.mascot, actions.onStartingLevel, gutter.fillMaxWidth())
            }
            ContinueCard(state, home, actions, gutter)
            QuickMatch(rating = state.effectiveUserRating, onClick = actions.onQuickMatch, modifier = gutter)
            Row(gutter.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                CtTile(modifier = Modifier.weight(1f).fillMaxHeight(), onClick = actions.onDailyPuzzle, onClickLabel = "Open the daily puzzle") {
                    Icon(Icons.Rounded.Bolt, contentDescription = null, tint = CtColors.Accent, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.weight(1f))
                    TileText("Daily puzzle", if (home.dailySolved) "Solved today" else "Waiting for you")
                }
                home.nextStep?.let { step -> NextStepTile(step, actions, Modifier.weight(1f).fillMaxHeight()) }
            }
            if (state.engineStatus.isDegraded) {
                CtListRow(
                    title = "Lite engine",
                    subtitle = "Stockfish is not running, so bots and analysis are weaker",
                    icon = Icons.Rounded.Warning,
                    iconTint = CtColors.Error,
                    onClick = actions.onOpenEngineHealth,
                    divider = false,
                    modifier = gutter,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                CtSectionHeader(title = "More ways to play", modifier = gutter)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = CtSpacing.gutter),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CtModeCard(Icons.Rounded.SmartToy, "Bots", onClick = actions.onOpenBots)
                    CtModeCard(Icons.Rounded.Flag, "Opening battle", onClick = actions.onOpeningBattle)
                    CtModeCard(Icons.Rounded.GridOn, "From a position", onClick = actions.onFromPosition)
                    CtModeCard(Icons.Rounded.Explore, "Explore", onClick = actions.onExplore)
                }
            }
        }
    }
}

/** The coach's line when there is nothing to continue: a nudge to resume, then the recommendation. */
internal fun coachLine(home: HomeUiState): String = when {
    home.resume != null -> "Your game against ${home.resume.bot.name} is waiting. Shall we finish it?"
    home.gamesPlayed == 0 -> "Fancy a game? Try a quick match, or pick a bot."
    home.recommendation != null -> home.recommendation.reason
    else -> "Fancy a game? I'll be cheering you on."
}

/** The one big card: finish your game, or pick up your next lesson, with a picture of the board. */
@Composable
private fun ContinueCard(state: AppUiState, home: HomeUiState, actions: HomeActions, modifier: Modifier) {
    val resume = home.resume
    val lesson = home.nextLesson
    val boardTheme = state.appSettings.boardTheme
    when {
        resume != null -> CtHeroCard(
            kicker = "Your game",
            title = "vs ${resume.bot.name}",
            detail = "Move ${resume.moveCount / 2 + 1}, you play ${if (resume.playerColor == PieceColor.WHITE) "White" else "Black"}",
            onClick = actions.onResume,
            onClickLabel = "Resume your game",
            modifier = modifier,
        ) { Thumbnail(resume.fen, boardTheme, whiteAtBottom = resume.playerColor == PieceColor.WHITE) }
        lesson != null -> CtHeroCard(
            kicker = lesson.courseTitle,
            title = lesson.lessonTitle,
            detail = "Lesson ${lesson.number} of ${lesson.total}",
            progress = (lesson.number - 1).toFloat() / lesson.total,
            onClick = { actions.onOpenLesson(lesson.lessonId) },
            onClickLabel = if (lesson.started) "Continue the lesson" else "Start the lesson",
            modifier = modifier,
        ) { Thumbnail(lesson.fen, boardTheme) }
        else -> CtHeroCard(
            kicker = "Learn",
            title = "Keep learning",
            detail = coachLine(home),
            onClick = actions.onContinueLearning,
            onClickLabel = "Continue learning",
            modifier = modifier,
        ) { Thumbnail(null, boardTheme) }
    }
}

/** A small, square board picture: no coordinates, nothing to tap. */
@Composable
private fun Thumbnail(fen: String?, theme: BoardTheme, whiteAtBottom: Boolean = true) {
    val position = fen?.let { Position.tryFromFen(it).getOrNull() } ?: Position.initial()
    BoardView(position = position, theme = theme, whiteAtBottom = whiteAtBottom, showCoordinates = false, showLegalDots = false)
}

/** The primary row (canvas `Home2.dc.html`): play a fair opponent now, with your rating on the right. */
@Composable
private fun QuickMatch(rating: Int, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(CtShapes.Button)
            .background(CtColors.Primary)
            .ctPressable(onClickLabel = "Quick match", onClick = onClick)
            .padding(horizontal = CtSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CtShapes.Control).background(CtColors.OnPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.SportsEsports, contentDescription = null, tint = CtColors.OnPrimary, modifier = Modifier.size(20.dp))
        }
        Text("Quick match", style = CtType.Button.copy(color = CtColors.OnPrimary), modifier = Modifier.weight(1f))
        Text("$rating", style = CtType.Label.copy(color = CtColors.OnPrimary), modifier = Modifier.padding(end = CtSpacing.xs))
    }
}

/** The second tile: mistakes to fix (a big number), a weak theme to practise, or a bot to play. */
@Composable
private fun NextStepTile(step: NextStep, actions: HomeActions, modifier: Modifier) {
    val onClick: () -> Unit = when (step) {
        is NextStep.Mistakes -> actions.onReviewMistakes
        is NextStep.Theme -> { -> actions.onPractiseTheme(step.score.theme) }
        is NextStep.Bot -> { -> actions.onOpenBot(step.recommendation.bot.id) }
    }
    CtTile(modifier = modifier, onClick = onClick, onClickLabel = step.reason) {
        when (step) {
            is NextStep.Mistakes -> Text("${step.count}", style = CtType.BigNumber)
            is NextStep.Theme -> Icon(Icons.Rounded.Extension, contentDescription = null, tint = CtColors.Accent, modifier = Modifier.size(28.dp))
            is NextStep.Bot -> BotAvatar(step.recommendation.bot.profile.face, step.recommendation.bot.rating, size = 40.dp)
        }
        Spacer(Modifier.weight(1f))
        when (step) {
            is NextStep.Mistakes -> TileText("Mistakes to fix", "From your games")
            is NextStep.Theme -> TileText("Practise ${step.score.name}", "Puzzles that trip you up")
            is NextStep.Bot -> TileText("Play ${step.recommendation.bot.name}", "A good match for you")
        }
    }
}

@Composable
private fun TileText(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = CtType.TitleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(detail, style = CtType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The first-run welcome (D30, instead of onboarding): one question, three answers. New
 * players go to Learn's basics; online players go to Account to link their account.
 */
@Composable
private fun WelcomeCard(mascot: Mascot, onChoose: (StartingLevel) -> Unit, modifier: Modifier) {
    CtCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CtMascotArt(mascot = mascot, height = 52.dp, modifier = Modifier.width(52.dp))
            Spacer(Modifier.width(CtSpacing.md))
            Column(Modifier.weight(1f)) {
                Text("Welcome!", style = CtType.Headline)
                Text("Where should we start?", style = CtType.BodySmall)
            }
        }
        Spacer(Modifier.height(CtSpacing.sm))
        StartingLevel.entries.forEachIndexed { i, level ->
            CtListRow(
                title = level.label,
                subtitle = level.detail,
                icon = when (level) {
                    StartingLevel.NEW -> Icons.Rounded.School
                    StartingLevel.RULES -> Icons.Rounded.SportsEsports
                    StartingLevel.PLAYER -> Icons.Rounded.Link
                },
                onClick = { onChoose(level) },
                divider = i < StartingLevel.entries.lastIndex,
            )
        }
    }
}
