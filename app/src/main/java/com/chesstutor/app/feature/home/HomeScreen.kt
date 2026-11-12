package com.chesstutor.app.feature.home

import androidx.compose.material.icons.rounded.PlayArrow
import com.chesstutor.app.domain.bot.BotRoster
import com.chesstutor.app.design.ctSheen
import com.chesstutor.app.design.CtArtTile
import com.chesstutor.app.design.CtScene
import com.chesstutor.app.design.ctGloss
import com.chesstutor.app.board.BoardPicture
import com.chesstutor.app.design.ctEnter
import com.chesstutor.app.design.CtTickMark
import com.chesstutor.app.design.CtStreak
import com.chesstutor.app.design.CtPictureTile
import com.chesstutor.app.design.CtFade
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
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
    val onPlayFriend: () -> Unit = {},
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
            PlayButton(onClick = actions.onOpenBots, modifier = gutter.ctEnter(1))
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
                val match = home.recommendation?.bot ?: BotRoster.closestTo(state.effectiveUserRating)
                CtPictureTile(
                    title = "Quick match",
                    subtitle = "${match.name}, ${match.rating}",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    fade = CtFade.Soft,
                    onClick = actions.onQuickMatch,
                    onClickLabel = "Play ${match.name} now",
                ) { BotPicture(match.profile.face) }
            }
            if (state.engineStatus.isDegraded) {
                CtListRow(
                    title = "Lite engine",
                    subtitle = "Stockfish is not running, so bots and analysis are weaker",
                    icon = Icons.Rounded.Warning,
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
                    home.nextStep?.let { step -> NextStepTile(step, actions, tile) }
                    CtArtTile(title = "Opening battle", scene = CtScene.BATTLE, modifier = tile, compact = true, onClick = actions.onOpeningBattle)
                    CtArtTile(title = "From a position", scene = CtScene.POSITION, modifier = tile, compact = true, onClick = actions.onFromPosition)
                    CtArtTile(title = "Explore", scene = CtScene.EXPLORE, modifier = tile, compact = true, onClick = actions.onExplore)
                    PlayAFriend(tile, actions.onPlayFriend)
                }
            }
        }
    }
}

/** Two people on this phone (Phase 11). A plain tile: it has no scene of its own yet. */
@Composable
private fun PlayAFriend(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(CtShapes.CardSmall)
            .background(CtColors.Surface)
            .ctPressable(onClickLabel = "Play a friend", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = CtSpacing.sm),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text("Play a friend", style = CtType.Label, maxLines = 1)
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

/** The one solid, glossy button on Home: it opens every bot to choose from. */
@Composable
private fun PlayButton(onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(CtShapes.Button)
            .background(CtColors.Primary)
            .ctGloss(CtShapes.Button, CtColors.Primary)
            .ctSheen(CtColors.Primary)
            .ctPressable(onClickLabel = "Choose who to play", onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm, Alignment.CenterHorizontally),
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = CtColors.OnPrimary, modifier = Modifier.size(24.dp))
        Text("Play", style = CtType.Button.copy(color = CtColors.OnPrimary))
    }
}

/** What to work on next, first among the ways to play: mistakes to fix or a weak theme. A bot is Quick match's job. */
@Composable
private fun NextStepTile(step: NextStep, actions: HomeActions, modifier: Modifier) {
    when (step) {
        is NextStep.Mistakes -> CtArtTile(
            title = if (step.count == 1) "1 mistake to fix" else "${step.count} mistakes to fix",
            scene = CtScene.BLUNDERS,
            modifier = modifier,
            compact = true,
            onClick = actions.onReviewMistakes,
            onClickLabel = step.reason,
        )
        is NextStep.Theme -> CtArtTile(
            title = "Practise ${step.score.name}",
            scene = CtScene.TACTICS,
            modifier = modifier,
            compact = true,
            onClick = { actions.onPractiseTheme(step.score.theme) },
            onClickLabel = step.reason,
        )
        is NextStep.Bot -> Unit
    }
}

