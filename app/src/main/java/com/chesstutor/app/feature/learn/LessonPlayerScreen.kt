package com.chesstutor.app.feature.learn

import androidx.compose.material.icons.rounded.PlayArrow
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtActionPage
import com.chesstutor.app.design.CtSpeechBubble
import com.chesstutor.app.design.CtMascotArt
import com.chesstutor.app.design.CtBoardScreen
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.rounded.Close
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardArrow
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardHost
import com.chesstutor.app.board.MarkKind
import com.chesstutor.app.board.SquareRing
import com.chesstutor.app.board.whiteAtBottom
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Square
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtIconBubble
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.MascotMood
import com.chesstutor.app.domain.learn.LessonPhase
import com.chesstutor.app.domain.learn.LessonStep
import com.chesstutor.app.domain.learn.LessonXp
import com.chesstutor.app.domain.learn.MarkTone
import com.chesstutor.app.domain.learn.phase

/**
 * The lesson player (plan section 6.2, Phase 7D, canvas `Lesson.dc.html`), going through
 * Watch (a video slot), Learn (explanations with arrows), Try (find the move, with hints),
 * Quiz ("Challenge 1/5") and Done (XP and the next lesson). Board steps are a board screen:
 * the lesson's progress in the fixed row above the board, the coach below it, and Hint or
 * Again and Next at the bottom, so the board never moves (D39).
 */
@Composable
fun LessonPlayerScreen(
    player: LessonPlayer?,
    settings: AppSettings,
    onFeedback: (BoardFeedback) -> Unit,
    onNextLesson: (lessonId: String) -> Unit,
    onReplay: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens Opening Battle on the course's line; null hides the button. */
    onPractise: (() -> Unit)? = null,
    /** Opens puzzles with the course's themes; null hides the button. */
    onRelatedPuzzles: (() -> Unit)? = null,
) {
    BackHandler(onBack = onBack)
    if (player == null) {
        CtScreenBackground(modifier) { CtTopBar(title = "Lesson", onBack = onBack) }
        return
    }
    val state by player.state.collectAsState()
    if (state.phase != LessonPhase.DONE && state.step !is LessonStep.Watch) {
        BoardStep(player, state, settings, onFeedback, onBack, modifier)
        return
    }
    if (state.phase == LessonPhase.DONE) {
        DonePage(state, settings, onNextLesson, onReplay, onBack, onPractise, onRelatedPuzzles, modifier)
    } else {
        WatchPage(state, state.step as LessonStep.Watch, onStart = player::next, onBack = onBack, modifier = modifier)
    }
}

/** One thin segment per step of the lesson: done, current, still to come (canvas `Lesson.dc.html`). */
@Composable
private fun StepProgress(state: LessonPlayerState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "${state.phase.label}, step ${minOf(state.stepIndex + 1, state.stepCount)} of ${state.stepCount}"
            },
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.xs),
    ) {
        repeat(state.stepCount) { i ->
            val color = when {
                i < state.stepIndex -> CtColors.Accent
                i == state.stepIndex -> CtColors.Text2
                else -> CtColors.Track
            }
            Box(Modifier.weight(1f).height(4.dp).clip(CtShapes.Pill).background(color))
        }
    }
}

/** The coach below the board: its mood, a tailed bubble, and a tick or a cross after a move. */
@Composable
private fun LessonCoach(state: LessonPlayerState, settings: AppSettings, label: String?, mood: MascotMood) {
    Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.xs), modifier = Modifier.semantics(mergeDescendants = true) {}) {
        CtMascotArt(mascot = settings.mascot, mood = mood, height = 60.dp, modifier = Modifier.size(60.dp))
        CtSpeechBubble(modifier = Modifier.weight(1f)) {
            when (state.feedbackTone) {
                FeedbackTone.GOOD -> ToneBadge(Icons.Rounded.Check, CtColors.Success, CtColors.OnSuccess, "Correct")
                FeedbackTone.BAD -> ToneBadge(Icons.Rounded.Close, CtColors.Error, CtColors.OnError, "Not quite")
                null -> Unit
            }
            Column {
                if (label != null) Text(label, style = CtType.Caption.copy(color = CtColors.Accent))
                Text(state.coachText, style = CtType.Chat)
            }
        }
    }
}

@Composable
private fun ToneBadge(icon: ImageVector, fill: Color, ink: Color, description: String) {
    Box(
        modifier = Modifier
            .padding(top = 1.dp)
            .size(20.dp)
            .clip(CtShapes.Pill)
            .background(fill)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun BoardStep(
    player: LessonPlayer,
    state: LessonPlayerState,
    settings: AppSettings,
    onFeedback: (BoardFeedback) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val side = if (state.orientation == PieceColor.WHITE) "White" else "Black"
    val label = when {
        state.feedbackTone == FeedbackTone.GOOD -> "Correct!"
        state.feedbackTone == FeedbackTone.BAD -> "Not quite"
        state.challenge != null -> "Challenge ${state.challenge.first}/${state.challenge.second}"
        state.step is LessonStep.Play -> if (state.orientation == PieceColor.WHITE) "Your turn" else "Your turn, you play $side"
        else -> null
    }
    val mood = when (state.feedbackTone) {
        FeedbackTone.GOOD -> MascotMood.PROUD
        FeedbackTone.BAD -> MascotMood.KIND
        else -> MascotMood.READY
    }
    val arrows = state.arrows.map { BoardArrow(Square.fromAlgebraic(it.from), Square.fromAlgebraic(it.to), it.tone.kind()) } +
        listOfNotNull(state.hint?.to?.let { to -> BoardArrow(Square.fromAlgebraic(state.hint.from), Square.fromAlgebraic(to), MarkKind.HINT) })
    val rings = state.highlights.map { SquareRing(Square.fromAlgebraic(it.square), it.tone.kind()) } +
        listOfNotNull(
            state.hint?.takeIf { it.to == null }?.let { SquareRing(Square.fromAlgebraic(it.from), MarkKind.HINT) },
            state.mistakeSquare?.let { SquareRing(Square.fromAlgebraic(it), MarkKind.MISTAKE) },
        )
    val yourMove = state.step is LessonStep.Play && !state.canAdvance
    CtBoardScreen(
        title = state.lessonTitle,
        onBack = onBack,
        modifier = modifier,
        status = { StepProgress(state) },
        board = {
            BoardHost(
                session = player.board,
                theme = settings.boardTheme,
                whiteAtBottom = whiteAtBottom(settings.boardOrientation, state.orientation),
                showCoordinates = settings.showCoordinates,
                showLegalDots = settings.showLegalDots,
                autoQueen = settings.autoQueen,
                arrows = arrows,
                rings = rings,
                onFeedback = onFeedback,
            )
        },
        controls = {
            if (yourMove) {
                CtButton(
                    text = "Hint",
                    icon = Icons.Rounded.Lightbulb,
                    style = CtButtonStyle.Secondary,
                    enabled = state.expectsMove,
                    onClick = player::hint,
                    modifier = Modifier.weight(1f),
                )
            } else {
                CtButton(
                    text = "Again",
                    icon = Icons.Rounded.Refresh,
                    style = CtButtonStyle.Secondary,
                    enabled = !state.busy,
                    onClick = player::replayStep,
                    modifier = Modifier.weight(1f),
                )
                CtButton(
                    text = if (state.isLastStep) "Finish" else "Next",
                    icon = if (state.isLastStep) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                    enabled = state.canAdvance,
                    onClick = player::next,
                    modifier = Modifier.weight(1.6f),
                )
            }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(top = CtSpacing.lg)) {
            LessonCoach(state, settings, label, mood)
        }
    }
}

/** The lesson's first page: the video slot (a placeholder until lesson videos exist), its title and one line. */
@Composable
private fun WatchPage(state: LessonPlayerState, step: LessonStep.Watch, onStart: () -> Unit, onBack: () -> Unit, modifier: Modifier) {
    CtActionPage(
        title = state.lessonTitle,
        onBack = onBack,
        modifier = modifier,
        bottom = { CtButton(text = "Start", onClick = onStart, modifier = Modifier.weight(1f)) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(CtShapes.Card)
                .background(CtColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CtSpacing.xs)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = CtColors.Text2, modifier = Modifier.size(40.dp))
                Text("Video coming soon", style = CtType.Caption)
            }
        }
        Text(step.title, style = CtType.Headline, modifier = Modifier.padding(top = CtSpacing.lg))
        if (step.text.isNotBlank()) Text(step.text, style = CtType.Body.copy(color = CtColors.Text2), modifier = Modifier.padding(top = CtSpacing.xs))
    }
}

/** The lesson's last page: the coach, the XP earned, a dot per challenge (filled when solved first time), and where to go next. */
@Composable
private fun DonePage(
    state: LessonPlayerState,
    settings: AppSettings,
    onNextLesson: (String) -> Unit,
    onReplay: () -> Unit,
    onBack: () -> Unit,
    onPractise: (() -> Unit)?,
    onRelatedPuzzles: (() -> Unit)?,
    modifier: Modifier,
) {
    val result = state.result
    val completion = state.completion
    val next = state.nextLesson
    CtActionPage(
        title = "",
        onBack = onBack,
        modifier = modifier,
        bottom = {
            if (next != null) CtButton(text = "Next: ${next.title}", onClick = { onNextLesson(next.id) }, modifier = Modifier.weight(1f))
            else CtButton(text = "Play it again", onClick = onReplay, modifier = Modifier.weight(1f))
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(top = CtSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            CtMascotArt(mascot = settings.mascot, mood = MascotMood.PROUD, height = 140.dp, modifier = Modifier.size(140.dp))
            Text("Lesson complete", style = CtType.Display, modifier = Modifier.padding(top = CtSpacing.md))
            when {
                completion == null -> Text("Saving…", style = CtType.Caption)
                completion.xp > 0 -> Text("+${completion.xp} XP", style = CtType.BigNumber.copy(color = CtColors.Accent))
                else -> Text("Practice round", style = CtType.Caption)
            }
            if (result != null && result.challenges.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = CtSpacing.sm).semantics(mergeDescendants = true) {
                        contentDescription = "${result.cleanChallenges} of ${result.challenges.size} challenges solved first time"
                    },
                    horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
                ) {
                    repeat(result.challenges.size) { i ->
                        Box(Modifier.size(12.dp).clip(CtShapes.Pill).background(if (i < result.cleanChallenges) CtColors.Success else CtColors.Track))
                    }
                }
            }
        }
        Column(Modifier.padding(top = CtSpacing.xl)) {
            onRelatedPuzzles?.let { CtListRow(title = "Related puzzles", icon = Icons.Rounded.Extension, onClick = it) }
            onPractise?.let { CtListRow(title = "Practise in Opening Battle", icon = Icons.Rounded.Flag, onClick = it) }
            if (next != null) CtListRow(title = "Play it again", icon = Icons.Rounded.Refresh, onClick = onReplay, divider = false)
        }
    }
}

private fun MarkTone.kind(): MarkKind = when (this) {
    MarkTone.GOOD -> MarkKind.BEST
    MarkTone.BAD -> MarkKind.MISTAKE
    MarkTone.HINT -> MarkKind.HINT
    MarkTone.IDEA -> MarkKind.ALTERNATIVE
}
