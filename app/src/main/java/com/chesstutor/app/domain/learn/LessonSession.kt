package com.chesstutor.app.domain.learn

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Position

/** The part of the lesson player a step belongs to (plan section 6.2). */
enum class LessonPhase(val label: String) {
    WATCH("Watch"),
    LEARN("Learn"),
    TRY("Try"),
    QUIZ("Quiz"),
    DONE("Done"),
}

val LessonStep.phase: LessonPhase
    get() = when (this) {
        is LessonStep.Watch -> LessonPhase.WATCH
        is LessonStep.Explain -> LessonPhase.LEARN
        is LessonStep.Play -> if (quiz) LessonPhase.QUIZ else LessonPhase.TRY
    }

sealed interface LessonMoveResult {
    /**
     * A right move. [reply] is the opponent's answer to show next (null if none);
     * [stepSolved] is true when the step is finished once the reply (if any) is played.
     */
    data class Correct(val reply: String?, val stepSolved: Boolean, val feedback: String?) : LessonMoveResult
    /** A wrong move: the board stays as it was and the player tries again. */
    data class Wrong(val feedback: String) : LessonMoveResult
    /** No move is expected now (an explain step, a solved step, or an illegal move). */
    data object NotExpected : LessonMoveResult
}

/** A hint: first the piece to move, then the whole move. [text] is the step's hint, if any. */
data class LessonHint(val from: String, val to: String?, val text: String?)

/** How one quiz challenge went: clean means no wrong move and no hint. */
data class ChallengeResult(val stepIndex: Int, val clean: Boolean)

/** The end of a lesson, used for XP and progress. */
data class LessonResult(
    val lessonId: String,
    val challenges: List<ChallengeResult>,
    val mistakes: Int,
    val hintsUsed: Int,
    /** True when every quiz challenge of the lesson was played in this run. */
    val playedAllChallenges: Boolean,
) {
    val cleanChallenges: Int get() = challenges.count { it.clean }
    val perfect: Boolean get() = playedAllChallenges && challenges.all { it.clean }
}

/**
 * Plays one [Lesson] step by step, as pure logic (the lesson player draws it): judges moves
 * in play and quiz steps, plays the opponent's replies, gives hints and counts mistakes.
 * [startStep] resumes a lesson part-way through.
 */
class LessonSession(val lesson: Lesson, startStep: Int = 0) {
    var stepIndex: Int = startStep.coerceIn(0, lesson.steps.lastIndex)
        private set
    /** The position now, including moves played in the current step. */
    var position: Position = Position.fromFen(lesson.steps[stepIndex].fen)
        private set
    var finished: Boolean = false
        private set

    private var turn = 0
    private var solved = false
    private var awaitingReply: String? = null
    private var stepMistakes = 0
    private var stepHints = 0
    private var mistakes = 0
    private var hintsUsed = 0
    private val startedAt = stepIndex
    private val challenges = mutableListOf<ChallengeResult>()

    val step: LessonStep get() = lesson.steps[stepIndex]
    val phase: LessonPhase get() = if (finished) LessonPhase.DONE else step.phase
    val stepCount: Int get() = lesson.steps.size

    /** "Challenge 2/5" numbers for a quiz step, else null. */
    val challengeNumber: Pair<Int, Int>?
        get() {
            val current = step as? LessonStep.Play ?: return null
            if (!current.quiz || finished) return null
            val quizSteps = lesson.steps.withIndex().filter { (_, s) -> s is LessonStep.Play && s.quiz }
            return (quizSteps.indexOfFirst { it.index == stepIndex } + 1) to quizSteps.size
        }

    /** True when the player may move now. */
    val expectsMove: Boolean get() = !finished && step is LessonStep.Play && !solved && awaitingReply == null

    /** True when "Next" may be pressed: an explain or watch step, or a solved play step. */
    val canAdvance: Boolean get() = !finished && (step !is LessonStep.Play || solved)

    val stepSolved: Boolean get() = solved

    fun submit(uci: String): LessonMoveResult {
        val play = step as? LessonStep.Play ?: return LessonMoveResult.NotExpected
        if (!expectsMove) return LessonMoveResult.NotExpected
        val move = LessonChess.legal(position, uci) ?: return LessonMoveResult.NotExpected
        val right = if (play.goal != null) LessonChess.reaches(position, move, play.goal) else uci in play.turns[turn].accepted
        if (!right) {
            stepMistakes++
            mistakes++
            val text = play.feedback[uci] ?: play.ifWrong ?: DEFAULT_WRONG
            return LessonMoveResult.Wrong(text)
        }
        position = LegalMoveGenerator.makeMove(position, move)
        val reply = if (play.goal != null) null else play.turns[turn].reply
        val lastTurn = play.goal != null || turn == play.turns.lastIndex
        turn++
        if (reply != null) awaitingReply = reply
        if (lastTurn) markSolved(play)
        val said = play.feedback[uci] ?: if (lastTurn) play.ifRight else null
        return LessonMoveResult.Correct(reply = reply, stepSolved = lastTurn, feedback = said)
    }

    /** Plays the opponent's pending reply; returns it (UCI), or null when there is none. */
    fun playReply(): String? {
        val reply = awaitingReply ?: return null
        awaitingReply = null
        position = LessonChess.play(position, reply) ?: position
        return reply
    }

    /** First call in a step: the piece to move. Later calls: the whole move. Null when no move is expected. */
    fun hint(): LessonHint? {
        val play = step as? LessonStep.Play ?: return null
        if (!expectsMove) return null
        val uci = if (play.goal != null) {
            LegalMoveGenerator.generateLegalMoves(position).firstOrNull { LessonChess.reaches(position, it, play.goal) }?.uci
        } else {
            play.turns[turn].accepted.first()
        } ?: return null
        stepHints++
        hintsUsed++
        val from = uci.substring(0, 2)
        return LessonHint(from = from, to = if (stepHints >= 2) uci.substring(2, 4) else null, text = play.hint)
    }

    /** Goes to the next step, or finishes the lesson after the last one. */
    fun advance(): Boolean {
        if (!canAdvance) return false
        if (stepIndex == lesson.steps.lastIndex) {
            finished = true
            return true
        }
        enter(stepIndex + 1)
        return true
    }

    /** Back to the start of the current step (after a mistake, or to watch it again). */
    fun restartStep() {
        if (finished) return
        // A challenge keeps its first score: mistakes and hints so far still count.
        val (m, h) = stepMistakes to stepHints
        enter(stepIndex)
        stepMistakes = m
        stepHints = h
    }

    fun result(): LessonResult = LessonResult(
        lessonId = lesson.id,
        challenges = challenges.toList(),
        mistakes = mistakes,
        hintsUsed = hintsUsed,
        playedAllChallenges = startedAt == 0 && challenges.size == lesson.quizCount,
    )

    private fun markSolved(play: LessonStep.Play) {
        solved = true
        if (play.quiz && challenges.none { it.stepIndex == stepIndex }) {
            challenges += ChallengeResult(stepIndex, clean = stepMistakes == 0 && stepHints == 0)
        }
    }

    private fun enter(index: Int) {
        stepIndex = index
        position = Position.fromFen(lesson.steps[index].fen)
        turn = 0
        solved = false
        awaitingReply = null
        stepMistakes = 0
        stepHints = 0
    }

    companion object {
        const val DEFAULT_WRONG = "Not quite. Have another look."
    }
}
