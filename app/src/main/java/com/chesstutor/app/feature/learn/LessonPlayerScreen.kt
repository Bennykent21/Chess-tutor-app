package com.chesstutor.app.feature.learn

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
import com.chesstutor.app.design.CtCoachStrip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtGradients
import com.chesstutor.app.design.CtIconBubble
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.learn.LessonPhase
import com.chesstutor.app.domain.learn.LessonStep
import com.chesstutor.app.domain.learn.LessonXp
import com.chesstutor.app.domain.learn.MarkTone
import com.chesstutor.app.domain.learn.phase

/**
 * The lesson player (plan section 6.2): the base board with the coach strip, going through
 * Watch (a video slot), Learn (explanations with arrows), Try (find the move, with hints),
 * Quiz ("Challenge 1/5") and Done (XP and the next lesson).
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
) {
    BackHandler(onBack = onBack)
    CtScreenBackground(modifier) {
        if (player == null) {
            Column(Modifier.fillMaxSize()) { CtTopBar(title = "Lesson", onBack = onBack) }
            return@CtScreenBackground
        }
        val state by player.state.collectAsState()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtTopBar(title = state.lessonTitle, subtitle = state.courseTitle, onBack = onBack)
            Column(
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.sm),
            ) {
                PhaseRow(player, state)
                when {
                    state.phase == LessonPhase.DONE -> DoneCard(state, onNextLesson, onReplay, onBack)
                    state.step is LessonStep.Watch -> WatchCard(state.step as LessonStep.Watch, onStart = player::next)
                    else -> BoardStep(player, state, settings, onFeedback)
                }
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}

/** Watch · Learn · Try · Quiz · Done, with the parts this lesson has, and a progress bar. */
@Composable
private fun PhaseRow(player: LessonPlayer, state: LessonPlayerState) {
    val phases = player.lesson.steps.map { it.phase }.distinct() + LessonPhase.DONE
    val done = if (state.phase == LessonPhase.DONE) 1f else state.stepIndex.toFloat() / state.stepCount
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "${state.phase.label}, step ${minOf(state.stepIndex + 1, state.stepCount)} of ${state.stepCount}"
        },
        verticalArrangement = Arrangement.spacedBy(CtSpacing.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
            phases.forEach { phase ->
                val current = phase == state.phase
                val passed = phases.indexOf(phase) < phases.indexOf(state.phase)
                Text(
                    text = phase.label,
                    style = CtType.Label.copy(color = if (current) CtColors.OnGold else if (passed) CtColors.Text else CtColors.Text3),
                    modifier = Modifier
                        .clip(CtShapes.Pill)
                        .background(if (current) CtColors.Gold else CtColors.SurfaceRaised)
                        .padding(horizontal = CtSpacing.md, vertical = CtSpacing.xs),
                )
            }
        }
        CtProgressBar(progress = done, color = CtColors.Teal, height = 6.dp)
    }
}

@Composable
private fun BoardStep(player: LessonPlayer, state: LessonPlayerState, settings: AppSettings, onFeedback: (BoardFeedback) -> Unit) {
    val label = when {
        state.feedbackTone == FeedbackTone.GOOD -> "Correct!"
        state.feedbackTone == FeedbackTone.BAD -> "Not quite"
        state.challenge != null -> "Challenge ${state.challenge.first}/${state.challenge.second}"
        state.step is LessonStep.Play -> "Your turn"
        else -> null
    }
    CtCoachStrip(mascot = settings.mascot, text = state.coachText, label = label, modifier = Modifier.fillMaxWidth())

    val arrows = state.arrows.map { BoardArrow(Square.fromAlgebraic(it.from), Square.fromAlgebraic(it.to), it.tone.kind()) } +
        listOfNotNull(state.hint?.to?.let { to -> BoardArrow(Square.fromAlgebraic(state.hint.from), Square.fromAlgebraic(to), MarkKind.HINT) })
    val rings = state.highlights.map { SquareRing(Square.fromAlgebraic(it.square), it.tone.kind()) } +
        listOfNotNull(
            state.hint?.takeIf { it.to == null }?.let { SquareRing(Square.fromAlgebraic(it.from), MarkKind.HINT) },
            state.mistakeSquare?.let { SquareRing(Square.fromAlgebraic(it), MarkKind.MISTAKE) },
        )
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
    val side = if (state.orientation == PieceColor.WHITE) "White" else "Black"
    if (state.step is LessonStep.Play && !state.canAdvance) {
        Text("You play $side", style = CtType.Caption)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        if (state.step is LessonStep.Play && !state.canAdvance) {
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
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The video slot: a placeholder until lesson videos exist (plan Phase 4). */
@Composable
private fun WatchCard(step: LessonStep.Watch, onStart: () -> Unit) {
    CtCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(CtShapes.CardSmall)
                .background(CtColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CtIconBubble(icon = Icons.Rounded.OndemandVideo, tint = CtColors.Violet, size = 56.dp)
                Spacer(Modifier.height(CtSpacing.sm))
                Text("Video coming soon", style = CtType.Label)
            }
        }
        Spacer(Modifier.height(CtSpacing.md))
        Text(step.title, style = CtType.Title)
        if (step.text.isNotBlank()) Text(step.text, style = CtType.Body)
    }
    CtButton(text = "Start the lesson", icon = Icons.AutoMirrored.Rounded.ArrowForward, onClick = onStart, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun DoneCard(state: LessonPlayerState, onNextLesson: (String) -> Unit, onReplay: () -> Unit, onBack: () -> Unit) {
    val result = state.result
    val completion = state.completion
    CtCard(background = CtGradients.Teal, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = CtColors.Gold, modifier = Modifier.size(40.dp))
            Column {
                Text("Lesson complete!", style = CtType.Headline)
                Text(state.lessonTitle, style = CtType.Body.copy(color = CtColors.Text))
            }
        }
        Spacer(Modifier.height(CtSpacing.sm))
        if (result != null && result.challenges.isNotEmpty()) {
            Text(
                "Challenges solved first time: ${result.cleanChallenges} of ${result.challenges.size}",
                style = CtType.Label,
            )
        }
        when {
            completion == null -> Text("Saving…", style = CtType.Caption.copy(color = CtColors.Text))
            completion.xp > 0 -> {
                Text("+${completion.xp} XP", style = CtType.Number.copy(color = CtColors.Gold))
                if (completion.newlyPerfect) Text("Includes a perfect bonus of ${LessonXp.PERFECT_BONUS} XP", style = CtType.Caption.copy(color = CtColors.Text))
            }
            else -> Text("Practice round: XP is earned the first time.", style = CtType.Caption.copy(color = CtColors.Text))
        }
    }
    state.nextLesson?.let { next ->
        CtButton(text = "Next: ${next.title}", icon = Icons.AutoMirrored.Rounded.ArrowForward, onClick = { onNextLesson(next.id) }, modifier = Modifier.fillMaxWidth())
    }
    CtButton(
        text = "Play it again",
        icon = Icons.Rounded.Refresh,
        style = if (state.nextLesson == null) CtButtonStyle.Primary else CtButtonStyle.Secondary,
        onClick = onReplay,
        modifier = Modifier.fillMaxWidth(),
    )
    CtButton(text = "Back to lessons", style = CtButtonStyle.Ghost, onClick = onBack, modifier = Modifier.fillMaxWidth())
}

private fun MarkTone.kind(): MarkKind = when (this) {
    MarkTone.GOOD -> MarkKind.BEST
    MarkTone.BAD -> MarkKind.MISTAKE
    MarkTone.HINT -> MarkKind.HINT
    MarkTone.IDEA -> MarkKind.ALTERNATIVE
}
