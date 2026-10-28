package com.chesstutor.app.feature.learn

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
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtTopBar(title = state.lessonTitle, subtitle = state.courseTitle, onBack = onBack)
            Column(
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.sm),
            ) {
                if (state.phase == LessonPhase.DONE) {
                    DoneCard(state, onNextLesson, onReplay, onBack, onPractise, onRelatedPuzzles)
                } else {
                    WatchCard(state.step as LessonStep.Watch, onStart = player::next)
                }
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
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

/** The video slot: a placeholder until lesson videos exist (plan Phase 4). */
@Composable
private fun WatchCard(step: LessonStep.Watch, onStart: () -> Unit) {
    CtCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(CtShapes.CardSmall)
                .background(CtColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CtIconBubble(icon = Icons.Rounded.OndemandVideo, tint = CtColors.Accent, size = 56.dp)
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
private fun DoneCard(
    state: LessonPlayerState,
    onNextLesson: (String) -> Unit,
    onReplay: () -> Unit,
    onBack: () -> Unit,
    onPractise: (() -> Unit)?,
    onRelatedPuzzles: (() -> Unit)?,
) {
    val result = state.result
    val completion = state.completion
    CtCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = CtColors.Accent, modifier = Modifier.size(40.dp))
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
                Text("+${completion.xp} XP", style = CtType.Number.copy(color = CtColors.Accent))
                if (completion.newlyPerfect) Text("Includes a perfect bonus of ${LessonXp.PERFECT_BONUS} XP", style = CtType.Caption.copy(color = CtColors.Text))
            }
            else -> Text("Practice round: XP is earned the first time.", style = CtType.Caption.copy(color = CtColors.Text))
        }
    }
    state.nextLesson?.let { next ->
        CtButton(text = "Next: ${next.title}", icon = Icons.AutoMirrored.Rounded.ArrowForward, onClick = { onNextLesson(next.id) }, modifier = Modifier.fillMaxWidth())
    }
    onRelatedPuzzles?.let {
        CtButton(text = "Related puzzles", icon = Icons.Rounded.Extension, style = CtButtonStyle.Secondary, onClick = it, modifier = Modifier.fillMaxWidth())
    }
    onPractise?.let {
        CtButton(text = "Practise in Opening Battle", icon = Icons.Rounded.Flag, style = CtButtonStyle.Secondary, onClick = it, modifier = Modifier.fillMaxWidth())
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
