package com.chesstutor.app.feature.learn

import com.chesstutor.app.design.ctPressable
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import com.chesstutor.app.domain.learn.PlainMoves
import androidx.compose.foundation.layout.fillMaxSize
import com.chesstutor.app.feature.play.rememberTalk
import com.chesstutor.app.feature.play.TalkLine
import com.chesstutor.app.feature.play.Talk
import androidx.compose.runtime.LaunchedEffect
import com.chesstutor.app.domain.learn.LessonVideo
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
    /** The lesson's own videos for its first page (D99); without any the lesson opens straight on the board. */
    videos: List<LessonVideo> = emptyList(),
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
    // A lesson with its own videos opens on them first, even when its steps start on the board (D99).
    // The lesson waits behind the page and starts from its first step on Next (D100).
    var watched by rememberSaveable(player.lesson.id) { mutableStateOf(false) }
    if (videos.isNotEmpty() && !watched && state.phase != LessonPhase.DONE && state.step !is LessonStep.Watch) {
        LaunchedEffect(player) { player.holdForVideo() }
        WatchPage(
            state, state.lessonTitle, videos, plain = PlainMoves.appliesTo(player.course),
            onStart = {
                watched = true
                player.startAfterVideo()
            },
            onBack = onBack, onPreviousLesson = onPreviousLesson, modifier = modifier,
        )
        return
    }
    if (state.phase != LessonPhase.DONE && state.step !is LessonStep.Watch) {
        // On the first step, Back returns to the lesson's video when it has one (D100).
        BoardStep(player, state, settings, onFeedback, onBack, onPreviousLesson, modifier, onVideo = { watched = false }.takeIf { videos.isNotEmpty() })
        return
    }
    if (state.phase == LessonPhase.DONE) {
        DonePage(state, settings, onNextLesson, onPreviousLesson, onReplay, onBack, onPractise, onRelatedPuzzles, onTrain, modifier)
    } else if (videos.isEmpty()) {
        // No video yet: no placeholder page, straight to the board.
        LaunchedEffect(state.step) { player.next() }
        CtScreenBackground(modifier) {}
    } else {
        WatchPage(state, (state.step as LessonStep.Watch).title, videos, plain = PlainMoves.appliesTo(player.course), onStart = player::next, onBack = onBack, onPreviousLesson = onPreviousLesson, modifier = modifier)
    }
}

/**
 * What the coach says on a step (D98): "Correct!" or "Your turn" first, unless the step's own
 * sentence already starts that way, then the sentence.
 */
internal fun lessonLine(label: String?, coachText: String): String {
    val lead = label?.takeUnless { coachText.startsWith(it.substringBefore(',').trimEnd('!', '.'), ignoreCase = true) }
        ?.let { if (it.endsWith("!") || it.endsWith(".")) it else "$it." }
    return listOfNotNull(lead, coachText.takeIf { it.isNotBlank() }).joinToString(" ")
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
private fun BoardStep(
    player: LessonPlayer,
    state: LessonPlayerState,
    settings: AppSettings,
    onFeedback: (BoardFeedback) -> Unit,
    onBack: () -> Unit,
    onPreviousLesson: (String) -> Unit,
    modifier: Modifier,
    onVideo: (() -> Unit)? = null,
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
    // Beginner courses say moves in words; the notation course teaches the letters (D99).
    val plain = PlainMoves.appliesTo(player.course)
    CtBoardScreen(
        // The course's name (the variation), shrunk to fit; the steps fill the bar under it (D99).
        title = state.courseTitle,
        onBack = onBack,
        modifier = modifier,
        progress = if (state.stepCount == 0) 0f else (state.stepIndex + 1f) / state.stepCount,
        // The talking coach, as on every board screen (D98); the eval goes under the board.
        zone = {
            val words = lessonLine(label, state.coachText).let { if (plain) PlainMoves.soften(it) else it }
            val talk = rememberTalk(listOf(TalkLine("${state.stepIndex}:$label:$words".hashCode(), words)), key = state.lessonTitle)
            Talk(avatar = { CtMascotArt(settings.mascot, height = 76.dp) }, lines = talk, maxLines = 7)
        },
        under = {
            if (state.evalCp != null || state.evalMate != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LessonEvalBar(state.evalCp, state.evalMate) }
            }
        },
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
            } else if (onVideo != null) {
                CtControl(Icons.AutoMirrored.Rounded.ArrowBack, "Back", description = "Back to the video", onClick = onVideo)
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
            // "Next", not "Skip": the learner goes at their own pace (D99).
            CtControl(
                icon = if (state.isLastStep) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                label = if (state.isLastStep) "Finish" else "Next",
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

/**
 * The lesson's first page (D99): the lesson's own video plays here, with what it teaches; when the
 * lesson has more than one, the others sit under it and a tap swaps them in. Next goes to the board.
 */
@Composable
private fun WatchPage(
    state: LessonPlayerState,
    heading: String,
    videos: List<LessonVideo>,
    plain: Boolean,
    onStart: () -> Unit,
    onBack: () -> Unit,
    onPreviousLesson: (String) -> Unit,
    modifier: Modifier,
) {
    var shown by rememberSaveable(videos) { mutableStateOf(0) }
    val video = videos[shown.coerceIn(videos.indices)]
    CtActionPage(
        title = state.courseTitle,
        onBack = onBack,
        modifier = modifier,
        bottom = {
            PreviousLessonIcon(state, onPreviousLesson)
            CtButton(text = "Next", onClick = onStart, modifier = Modifier.weight(1f))
        },
    ) {
        VideoFrame(video, autoplay = false, modifier = Modifier.fillMaxWidth())
        Text(heading.let { if (plain) PlainMoves.soften(it) else it }, style = CtType.Headline, modifier = Modifier.padding(top = CtSpacing.lg))
        Text(video.why.ifBlank { "Watch the video, then try it on the board." }, style = CtType.Body.copy(color = CtColors.Text2), modifier = Modifier.padding(top = CtSpacing.xs))
        videos.forEachIndexed { i, other ->
            if (i != shown) OtherVideo(other, onClick = { shown = i }, modifier = Modifier.padding(top = CtSpacing.md))
        }
    }
}

/** Another video of the same lesson: its picture, title and channel; a tap plays it above. */
@Composable
private fun OtherVideo(video: LessonVideo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CtShapes.CardSmall)
            .ctPressable(onClickLabel = "Play this video", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "${video.title}, ${video.channel}, ${video.minutes} minutes" },
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VideoThumbnail(video, Modifier.width(128.dp), playMark = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(video.title, style = CtType.Label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(video.channel, style = CtType.Caption.copy(color = CtColors.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
