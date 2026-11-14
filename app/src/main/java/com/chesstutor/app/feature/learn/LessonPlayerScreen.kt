package com.chesstutor.app.feature.learn

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.rounded.PlayArrow
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtActionPage
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Lightbulb
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
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtIconButton
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.MascotMood
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextOverflow
import com.chesstutor.app.board.EvalMath
import com.chesstutor.app.design.CtControl
import com.chesstutor.app.design.LocalReduceMotion
import com.chesstutor.app.domain.learn.LessonPhase
import com.chesstutor.app.domain.learn.LessonStep
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
    /** Opens the lesson before this one in the course. */
    onPreviousLesson: (lessonId: String) -> Unit = {},
    /** Opens Opening Battle on the course's line; null hides the button. */
    onPractise: (() -> Unit)? = null,
    /** Opens puzzles with the course's themes; null hides the button. */
    onRelatedPuzzles: (() -> Unit)? = null,
    /** Opens the drills a just-finished course opened (Phase 12); null hides the button. */
    onTrain: (() -> Unit)? = null,
) {
    BackHandler(onBack = onBack)
    if (player == null) {
        CtScreenBackground(modifier) { CtTopBar(title = "Lesson", onBack = onBack) }
        return
    }
    val state by player.state.collectAsState()
    if (state.phase != LessonPhase.DONE && state.step !is LessonStep.Watch) {
        BoardStep(player, state, settings, onFeedback, onBack, onPreviousLesson, modifier)
        return
    }
    if (state.phase == LessonPhase.DONE) {
        DonePage(state, settings, onNextLesson, onPreviousLesson, onReplay, onBack, onPractise, onRelatedPuzzles, onTrain, modifier)
    } else {
        WatchPage(state, state.step as LessonStep.Watch, onStart = player::next, onBack = onBack, onPreviousLesson = onPreviousLesson, modifier = modifier)
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

/** The words above the board: a quiet label ("Your turn", "Correct!") and the step's own sentence. */
@Composable
private fun LessonWords(state: LessonPlayerState, label: String?) {
    Column(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom)) {
        if (label != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (state.feedbackTone) {
                    FeedbackTone.GOOD -> ToneBadge(Icons.Rounded.Check, CtColors.Success, CtColors.OnSuccess, "Correct")
                    FeedbackTone.BAD -> ToneBadge(Icons.Rounded.Close, CtColors.Error, CtColors.OnError, "Not quite")
                    null -> Unit
                }
                Text(label, style = CtType.Caption.copy(color = CtColors.Accent))
            }
        }
        Text(state.coachText, style = CtType.Title, maxLines = 5, overflow = TextOverflow.Ellipsis)
    }
}

/** A thin horizontal evaluation bar: White's share on the light side, the number beside it (lessons only). */
@Composable
private fun LessonEvalBar(centipawns: Int?, mate: Int?) {
    val reduceMotion = LocalReduceMotion.current
    val share by animateFloatAsState(EvalMath.whiteShare(centipawns, mate), tween(if (reduceMotion) 0 else 400), label = "lessonEval")
    val label = EvalMath.label(centipawns, mate)
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Evaluation $label" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
    ) {
        Box(Modifier.weight(1f).height(6.dp).clip(CtShapes.Pill).background(CtColors.Text)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(share).background(CtColors.Track))
        }
        Text(label, style = CtType.Label, modifier = Modifier.widthIn(min = 36.dp))
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
    onPreviousLesson: (String) -> Unit,
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
        zone = {
            Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                StepProgress(state)
                LessonWords(state, label)
            }
        },
        under = { if (state.evalCp != null || state.evalMate != null) LessonEvalBar(state.evalCp, state.evalMate) },
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
                soundsOtherMoves = true,
            )
        },
        controls = {
            if (state.stepIndex > 0) {
                CtControl(Icons.AutoMirrored.Rounded.ArrowBack, "Back", description = "Previous step", onClick = player::back)
            } else {
                PreviousLessonButton(state, onPreviousLesson)
            }
            if (yourMove) {
                CtControl(Icons.Rounded.Lightbulb, "Hint", enabled = state.expectsMove, onClick = player::hint)
            } else {
                CtControl(
                    icon = if (state.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    label = if (state.paused) "Play" else "Pause",
                    onClick = player::togglePause,
                )
            }
            CtControl(
                icon = if (state.isLastStep) Icons.Rounded.Check else Icons.Rounded.SkipNext,
                label = if (state.isLastStep) "Finish" else "Skip",
                enabled = state.canAdvance,
                onClick = player::next,
            )
        },
    )
}

/** The same, as a square button beside a full-width one (the Watch and Done pages). */
@Composable
private fun PreviousLessonIcon(state: LessonPlayerState, onPreviousLesson: (String) -> Unit) {
    val previous = state.previousLesson
    CtIconButton(
        icon = Icons.AutoMirrored.Rounded.ArrowBack,
        contentDescription = "Previous lesson",
        onClick = { previous?.let { onPreviousLesson(it.id) } },
        enabled = previous != null,
    )
}

/** Back to the lesson before this one in the course; dimmed on the course's first lesson. */
@Composable
private fun RowScope.PreviousLessonButton(state: LessonPlayerState, onPreviousLesson: (String) -> Unit) {
    val previous = state.previousLesson
    CtControl(
        icon = Icons.AutoMirrored.Rounded.ArrowBack,
        label = "Back",
        description = "Previous lesson",
        enabled = previous != null,
        onClick = { previous?.let { onPreviousLesson(it.id) } },
    )
}

/** The lesson's first page: the video slot (a placeholder until lesson videos exist), its title and one line. */
@Composable
private fun WatchPage(state: LessonPlayerState, step: LessonStep.Watch, onStart: () -> Unit, onBack: () -> Unit, onPreviousLesson: (String) -> Unit, modifier: Modifier) {
    CtActionPage(
        title = state.lessonTitle,
        onBack = onBack,
        modifier = modifier,
        bottom = {
            PreviousLessonIcon(state, onPreviousLesson)
            CtButton(text = "Start", onClick = onStart, modifier = Modifier.weight(1f))
        },
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
    onPreviousLesson: (String) -> Unit,
    onReplay: () -> Unit,
    onBack: () -> Unit,
    onPractise: (() -> Unit)?,
    onRelatedPuzzles: (() -> Unit)?,
    onTrain: (() -> Unit)?,
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
            PreviousLessonIcon(state, onPreviousLesson)
            when {
                // The course is done and its drills just opened: train them.
                onTrain != null -> CtButton(text = "Train it", onClick = onTrain, modifier = Modifier.weight(1f))
                next != null -> CtButton(text = "Next: ${next.title}", onClick = { onNextLesson(next.id) }, modifier = Modifier.weight(1f))
                else -> CtButton(text = "Play it again", onClick = onReplay, modifier = Modifier.weight(1f))
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(top = CtSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            CtMascotArt(mascot = settings.mascot, mood = MascotMood.PROUD, height = 140.dp, modifier = Modifier.size(140.dp))
            Text(
                text = settings.playerName.ifBlank { null }?.let { "Well done, $it" } ?: "Lesson complete",
                style = CtType.Display,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = CtSpacing.md),
            )
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
            if (onTrain != null && next != null) CtListRow(title = "Next: ${next.title}", icon = null, onClick = { onNextLesson(next.id) })
            if (next != null) CtListRow(title = "Play it again", icon = Icons.Rounded.Refresh, onClick = onReplay)
        }
    }
}

private fun MarkTone.kind(): MarkKind = when (this) {
    MarkTone.GOOD -> MarkKind.BEST
    MarkTone.BAD -> MarkKind.MISTAKE
    MarkTone.HINT -> MarkKind.HINT
    MarkTone.IDEA -> MarkKind.ALTERNATIVE
}
