package com.chesstutor.app.feature.home

import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import com.chesstutor.app.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import com.chesstutor.app.domain.bot.BotRoster
import com.chesstutor.app.design.ctSheen
import com.chesstutor.app.design.CtArtTile
import com.chesstutor.app.design.CtScene
import com.chesstutor.app.design.ctGloss
import com.chesstutor.app.board.BoardPicture
import com.chesstutor.app.design.ctEnter
import com.chesstutor.app.design.CtTickMark
import com.chesstutor.app.data.update.UpdateState
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.semantics
import com.chesstutor.app.design.CtGroupLabel
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.border
import com.chesstutor.app.design.CtStreak
import com.chesstutor.app.feature.update.UpdateIcon
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
    /** What's new, from the update icon beside the streak (D90). */
    val onWhatsNew: () -> Unit = {},
    /** One saved game: its page and review (Your last game, D92). */
    val onOpenGame: (String) -> Unit = {},
    /** Start the existing opening-line trainer for a recurring repertoire slip. */
    val onTrainLine: (bookId: String, path: List<String>) -> Unit = { _, _ -> },
    /** Start the existing endgame set linked to a recurring conversion problem. */
    val onTrainEnding: (setId: String) -> Unit = {},
    /** Open the existing analysis board at a recurring mistake position. */
    val onTrainPosition: (fen: String) -> Unit = {},
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
    update: UpdateState = UpdateState.Idle,
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
                UpdateIcon(update, actions.onWhatsNew)
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
            // Every way to play lives behind Play (D92); Home looks back and points to what is next.
            home.nextStep?.let { step -> NextStepRow(step, actions, gutter.ctEnter(3)) }
            home.lastGame?.let { last ->
                Column(gutter.ctEnter(3)) {
                    CtGroupLabel("Your last game")
                    CtListRow(
                        title = last.title,
                        subtitle = listOfNotNull(last.accuracy?.let { "${"%.1f".format(it)}% accuracy" }, "${last.moves} moves").joinToString(" · "),
                        icon = Icons.Rounded.History,
                        onClick = { actions.onOpenGame(last.id) },
                    )
                    if (last.graph.size > 1) Sparkline(last.graph, Modifier.padding(start = 52.dp).fillMaxWidth().height(40.dp))
                }
            }
            Column(gutter.ctEnter(4), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                CtGroupLabel("This week")
                WeekRow(home.week)
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
private fun NextStepRow(step: NextStep, actions: HomeActions, modifier: Modifier) {
    when (step) {
        is NextStep.Mistakes -> CtListRow(
            title = if (step.count == 1) "1 mistake to fix" else "${step.count} mistakes to fix",
            subtitle = step.reason,
            icon = Icons.Rounded.Replay,
            onClick = actions.onReviewMistakes,
            modifier = modifier,
        )
        is NextStep.RepeatedMistake -> CtListRow(
            title = "Review a recurring mistake",
            subtitle = step.reason,
            icon = Icons.Rounded.Warning,
            onClick = { actions.onTrainPosition(step.finding.fen) },
            modifier = modifier,
        )
        is NextStep.LineSlip -> CtListRow(
            title = "Fix your ${step.finding.bookTitle} line",
            subtitle = step.reason,
            icon = Icons.Rounded.Replay,
            onClick = { actions.onTrainLine(step.finding.bookId, step.finding.path) },
            modifier = modifier,
        )
        is NextStep.Ending -> CtListRow(
            title = "Practise ${step.finding.kind.title.lowercase()}",
            subtitle = step.reason,
            icon = Icons.Rounded.Warning,
            onClick = { actions.onTrainEnding(step.finding.kind.setId) },
            modifier = modifier,
        )
        is NextStep.Theme -> CtListRow(
            title = "Practise ${step.score.name}",
            subtitle = step.reason,
            icon = Icons.Rounded.Extension,
            onClick = { actions.onPractiseTheme(step.score.theme) },
            modifier = modifier,
        )
        is NextStep.Bot -> Unit
    }
}

/** The game's swings from the player's side (D93): above the middle line was good for them. */
@Composable
private fun Sparkline(points: List<Float>, modifier: Modifier = Modifier) {
    val line = CtColors.Text
    val middle = CtColors.Track
    Canvas(modifier.semantics { contentDescription = "How the game went" }) {
        val y = { p: Float -> size.height * (1f - p.coerceIn(0f, 1f)) }
        drawLine(middle, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx())
        val step = size.width / (points.size - 1)
        val path = Path().apply {
            moveTo(0f, y(points.first()))
            points.forEachIndexed { i, p -> if (i > 0) lineTo(i * step, y(p)) }
        }
        drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Monday to Sunday: a day with a game or a solved puzzle carries the metal flame; today's letter is bold. */
@Composable
private fun WeekRow(week: List<Boolean>) {
    val today = java.time.LocalDate.now().dayOfWeek.value - 1
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, letter ->
            val on = week.getOrElse(i) { false }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CtSpacing.xs),
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "${java.time.DayOfWeek.of(i + 1).name.lowercase().replaceFirstChar { it.uppercase() }}${if (on) ", played" else ""}"
                },
            ) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    if (on) {
                        Image(painterResource(R.drawable.icon_local_fire_department), contentDescription = null, modifier = Modifier.size(34.dp))
                    } else {
                        Box(Modifier.size(12.dp).clip(CtShapes.Pill).background(CtColors.Track))
                    }
                }
                Text(letter, style = if (i == today) CtType.Label.copy(color = CtColors.Text) else CtType.Caption.copy(color = CtColors.Text2))
            }
        }
    }
}

