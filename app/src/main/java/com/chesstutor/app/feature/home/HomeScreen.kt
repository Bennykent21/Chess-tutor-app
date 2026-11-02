package com.chesstutor.app.feature.home

import com.chesstutor.app.board.BoardScenes
import com.chesstutor.app.board.BoardPicture
import com.chesstutor.app.design.ctEnter
import com.chesstutor.app.design.CtTickMark
import com.chesstutor.app.design.CtStreak
import com.chesstutor.app.design.CtPictureTile
import com.chesstutor.app.design.CtFade
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtHeroCard
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtScreenTitle
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
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
    /** The first run's answer (D30: it replaced onboarding). */
    val onStartingLevel: (StartingLevel) -> Unit = {},
    /** The first run's name (D46). */
    val onName: (String) -> Unit = {},
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
    if (needsWelcome(state.assessmentState)) {
        FirstRunScreen(
            mascot = state.appSettings.mascot,
            pieceSet = state.appSettings.boardTheme.pieceSet,
            onName = actions.onName,
            onStartingLevel = actions.onStartingLevel,
            modifier = modifier,
            initialName = state.appSettings.playerName,
        )
        return
    }
    val name = greetingName(state.appSettings, state.linkedProfile?.username)
    CtScreenBackground(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = CtSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CtSpacing.lg),
        ) {
            CtScreenTitle(title = if (name != null) "Hi, $name" else "Ready to play?") {
                CtStreak(home.dayStreak)
            }
            val gutter = Modifier.padding(horizontal = CtSpacing.gutter)
            ContinueCard(state, home, actions, gutter.ctEnter(0))
            QuickMatch(rating = state.effectiveUserRating, onClick = actions.onQuickMatch, modifier = gutter.ctEnter(1))
            val boardTheme = state.appSettings.boardTheme
            Row(gutter.height(150.dp).ctEnter(2), horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                CtPictureTile(
                    title = "Daily puzzle",
                    subtitle = if (home.dailySolved) "Solved today" else "Waiting for you",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = actions.onDailyPuzzle,
                    onClickLabel = "Open the daily puzzle",
                    trailing = if (home.dailySolved) { { CtTickMark() } } else null,
                ) { BoardPicture(home.daily?.fen, boardTheme, whiteAtBottom = home.daily?.whiteAtBottom ?: true) }
                home.nextStep?.let { step -> NextStepTile(step, boardTheme, actions, Modifier.weight(1f).fillMaxHeight()) }
            }
            if (state.engineStatus.isDegraded) {
                CtListRow(
                    title = "Lite engine",
                    subtitle = "Stockfish is not running, so bots and analysis are weaker",
                    icon = Icons.Rounded.Warning,
                    iconTint = CtColors.Error,
                    onClick = actions.onOpenEngineHealth,
                    modifier = gutter,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                CtSectionHeader(title = "More ways to play", modifier = gutter)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = CtSpacing.gutter).ctEnter(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val tile = Modifier.size(132.dp, 96.dp)
                    CtPictureTile(title = "Bots", modifier = tile, compact = true, fade = CtFade.Soft, onClick = actions.onOpenBots) { BotPicture("wayne") }
                    CtPictureTile(title = "Opening battle", modifier = tile, compact = true, onClick = actions.onOpeningBattle) { BoardPicture(BoardScenes.OPENING, boardTheme) }
                    CtPictureTile(title = "From a position", modifier = tile, compact = true, onClick = actions.onFromPosition) { BoardPicture(BoardScenes.SETUP, boardTheme) }
                    CtPictureTile(title = "Explore", modifier = tile, compact = true, onClick = actions.onExplore) { BoardPicture(BoardScenes.MIDDLEGAME, boardTheme) }
                    PlayAFriend(tile)
                }
            }
        }
    }
}

/** Friend play is on the way (the developer's plan), so it shows here but does nothing yet. */
@Composable
private fun PlayAFriend(modifier: Modifier) {
    Column(
        modifier = modifier.clip(CtShapes.CardSmall).background(CtColors.Surface).padding(horizontal = 10.dp, vertical = CtSpacing.sm),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text("Play a friend", style = CtType.Label, maxLines = 1)
        Text("Coming soon", style = CtType.Caption, maxLines = 1)
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
        ) { BoardPicture(resume.fen, boardTheme, whiteAtBottom = resume.playerColor == PieceColor.WHITE) }
        lesson != null -> CtHeroCard(
            kicker = lesson.courseTitle,
            title = lesson.lessonTitle,
            detail = "Lesson ${lesson.number} of ${lesson.total}",
            progress = (lesson.number - 1).toFloat() / lesson.total,
            onClick = { actions.onOpenLesson(lesson.lessonId) },
            onClickLabel = if (lesson.started) "Continue the lesson" else "Start the lesson",
            modifier = modifier,
        ) { BoardPicture(lesson.fen, boardTheme) }
        else -> CtHeroCard(
            kicker = "Learn",
            title = "Keep learning",
            detail = coachLine(home),
            onClick = actions.onContinueLearning,
            onClickLabel = "Continue learning",
            modifier = modifier,
        ) { BoardPicture(null, boardTheme) }
    }
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
        Icon(Icons.Rounded.SportsEsports, contentDescription = null, tint = CtColors.OnPrimary, modifier = Modifier.size(24.dp))
        Text("Quick match", style = CtType.Button.copy(color = CtColors.OnPrimary), modifier = Modifier.weight(1f))
        Text("$rating", style = CtType.Label.copy(color = CtColors.OnPrimary), modifier = Modifier.padding(end = CtSpacing.xs))
    }
}

/** The second tile: mistakes to fix (a big number), a weak theme to practise, or a bot to play. */
@Composable
private fun NextStepTile(step: NextStep, theme: BoardTheme, actions: HomeActions, modifier: Modifier) {
    val onClick: () -> Unit = when (step) {
        is NextStep.Mistakes -> actions.onReviewMistakes
        is NextStep.Theme -> { -> actions.onPractiseTheme(step.score.theme) }
        is NextStep.Bot -> { -> actions.onOpenBot(step.recommendation.bot.id) }
    }
    when (step) {
        is NextStep.Mistakes -> CtPictureTile(
            title = if (step.count == 1) "1 mistake to fix" else "${step.count} mistakes to fix",
            subtitle = "From your games",
            modifier = modifier,
            onClick = onClick,
            onClickLabel = step.reason,
        ) { BoardPicture(BoardScenes.ATTACK, theme) }
        is NextStep.Theme -> CtPictureTile(
            title = "Practise ${step.score.name}",
            subtitle = "Puzzles that trip you up",
            modifier = modifier,
            onClick = onClick,
            onClickLabel = step.reason,
        ) { BoardPicture(BoardScenes.MIDDLEGAME, theme) }
        is NextStep.Bot -> CtPictureTile(
            title = "Play ${step.recommendation.bot.name}",
            subtitle = "A good match for you",
            modifier = modifier,
            fade = CtFade.Soft,
            onClick = onClick,
            onClickLabel = step.reason,
        ) { BotPicture(step.recommendation.bot.profile.face) }
    }
}

