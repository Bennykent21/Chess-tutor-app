package com.chesstutor.app.feature.learn

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.domain.learn.Course
import com.chesstutor.app.domain.learn.Lesson
import com.chesstutor.app.domain.learn.LessonArrow
import com.chesstutor.app.domain.learn.LessonCompletion
import com.chesstutor.app.domain.learn.LessonHighlight
import com.chesstutor.app.domain.learn.LessonHint
import com.chesstutor.app.domain.learn.LessonMoveResult
import com.chesstutor.app.domain.learn.LessonPhase
import com.chesstutor.app.domain.learn.LessonProgressRepository
import com.chesstutor.app.domain.learn.LessonResult
import com.chesstutor.app.domain.learn.LessonSession
import com.chesstutor.app.domain.learn.LessonStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Whether the coach's last remark was praise or a correction. */
enum class FeedbackTone { GOOD, BAD }

data class LessonPlayerState(
    val courseTitle: String,
    val lessonTitle: String,
    val phase: LessonPhase,
    val step: LessonStep,
    val stepIndex: Int,
    val stepCount: Int,
    /** "Challenge 2/5" during the quiz. */
    val challenge: Pair<Int, Int>? = null,
    val feedback: String? = null,
    val feedbackTone: FeedbackTone? = null,
    val hint: LessonHint? = null,
    /** The square of a wrong move, flashed briefly. */
    val mistakeSquare: String? = null,
    val expectsMove: Boolean = false,
    val canAdvance: Boolean = false,
    /** True while a demonstration move or reply is about to slide. */
    val busy: Boolean = false,
    val orientation: PieceColor = PieceColor.WHITE,
    val result: LessonResult? = null,
    val completion: LessonCompletion? = null,
    val nextLesson: Lesson? = null,
) {
    /** What the coach says now: feedback first, then the step's own text. */
    val coachText: String get() = feedback ?: step.text
    val arrows: List<LessonArrow> get() = step.arrows
    val highlights: List<LessonHighlight> get() = step.highlights
    val isLastStep: Boolean get() = stepIndex == stepCount - 1
}

/** Pauses that make the lesson feel natural (shortened in tests). */
data class LessonTiming(
    val demonstrationMs: Long = 500,
    val replyMs: Long = 550,
    val mistakeFlashMs: Long = 900,
)

/**
 * Runs one lesson on the board: demonstration moves, replies, hints, feedback, "Next",
 * saving the step to resume at, and finally the XP and progress.
 */
class LessonPlayer(
    val course: Course,
    val lesson: Lesson,
    private val progress: LessonProgressRepository,
    private val scope: CoroutineScope,
    startStep: Int = 0,
    private val nextLesson: Lesson? = null,
    private val timing: LessonTiming = LessonTiming(),
) {
    private val judge = LessonSession(lesson, startStep)
    val board = LessonBoardSession(judge)
    private val mutableState = MutableStateFlow(snapshot())
    val state: StateFlow<LessonPlayerState> = mutableState.asStateFlow()
    private var pacing: Job? = null
    private var flash: Job? = null

    init {
        board.onResult = ::onResult
    }

    /** Starts the current step's animation (call once the screen shows). */
    fun start() = enterStep()

    fun hint() {
        val hint = judge.hint() ?: return
        mutableState.value = snapshot(feedback = hint.text, hint = hint)
    }

    /** "Next": the next step, or the Done card after the last one. */
    fun next() {
        if (mutableState.value.busy || !judge.advance()) return
        if (judge.finished) finish() else enterStep()
    }

    /** Plays the current step again from its start. */
    fun replayStep() {
        if (judge.finished) return
        judge.restartStep()
        enterStep()
    }

    private fun enterStep() {
        pacing?.cancel()
        board.showStep()
        val step = judge.step
        scope.launch { progress.saveStep(lesson.id, judge.stepIndex, judge.stepCount) }
        if (step is LessonStep.Explain && step.move != null) {
            mutableState.value = snapshot(busy = true)
            pacing = scope.launch {
                delay(timing.demonstrationMs)
                board.playDemonstration()
                mutableState.value = snapshot()
            }
        } else {
            mutableState.value = snapshot()
        }
    }

    private fun onResult(result: LessonMoveResult, uci: String) {
        when (result) {
            is LessonMoveResult.Correct -> {
                val tone = if (result.feedback != null || result.stepSolved) FeedbackTone.GOOD else null
                val said = result.feedback ?: if (result.stepSolved) DEFAULT_RIGHT else null
                if (result.reply != null) {
                    mutableState.value = snapshot(feedback = said, tone = tone, busy = true)
                    pacing = scope.launch {
                        delay(timing.replyMs)
                        board.playReply()
                        mutableState.value = snapshot(feedback = said, tone = tone)
                    }
                } else {
                    mutableState.value = snapshot(feedback = said, tone = tone)
                }
            }
            is LessonMoveResult.Wrong -> {
                // The board stays put; ring the square the piece was dropped on for a moment.
                val square = uci.substring(2, 4)
                mutableState.value = snapshot(feedback = result.feedback, tone = FeedbackTone.BAD, mistake = square)
                flash?.cancel()
                flash = scope.launch {
                    delay(timing.mistakeFlashMs)
                    val now = mutableState.value
                    if (now.mistakeSquare == square) mutableState.value = now.copy(mistakeSquare = null)
                }
            }
            LessonMoveResult.NotExpected -> Unit
        }
    }

    private fun finish() {
        val result = judge.result()
        mutableState.value = snapshot(result = result)
        scope.launch {
            val completion = progress.complete(lesson, result)
            mutableState.value = snapshot(result = result, completion = completion)
        }
    }

    private fun snapshot(
        feedback: String? = null,
        tone: FeedbackTone? = null,
        hint: LessonHint? = null,
        mistake: String? = null,
        busy: Boolean = false,
        result: LessonResult? = null,
        completion: LessonCompletion? = null,
    ) = LessonPlayerState(
        courseTitle = course.title,
        lessonTitle = lesson.title,
        phase = judge.phase,
        step = judge.step,
        stepIndex = judge.stepIndex,
        stepCount = judge.stepCount,
        challenge = judge.challengeNumber,
        feedback = feedback,
        feedbackTone = tone,
        hint = hint,
        mistakeSquare = mistake,
        expectsMove = judge.expectsMove && !busy,
        canAdvance = judge.canAdvance && !busy,
        busy = busy,
        orientation = lesson.orientation,
        result = result,
        completion = completion,
        nextLesson = nextLesson,
    )

    companion object {
        const val DEFAULT_RIGHT = "Well done!"
    }
}
