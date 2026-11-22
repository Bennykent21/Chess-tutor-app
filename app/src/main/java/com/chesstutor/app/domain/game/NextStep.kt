package com.chesstutor.app.domain.game

import com.chesstutor.app.domain.account.ThemeScore
import com.chesstutor.app.domain.train.GameFinding
import kotlin.math.roundToInt

/** Home's Continue card when no game is waiting: the next lesson in Learn (plan 6.1). */
data class NextLesson(
    val lessonId: String,
    val courseTitle: String,
    val lessonTitle: String,
    /** The lesson's place in its course, from 1. */
    val number: Int,
    val total: Int,
    /** True when the player already began it. */
    val started: Boolean,
    /** The lesson's first position, for the card's board picture. */
    val fen: String? = null,
)

/** Home's "Recommended" card (plan 6.1 and 6.4): the most useful thing to do next, and why. */
sealed interface NextStep {
    val reason: String

    /** Mistakes already scheduled for recall in Puzzles > Mistakes review. */
    data class Mistakes(val count: Int) : NextStep {
        override val reason: String get() = "$count mistakes from your games are ready to fix."
    }

    /** A recurring mistake in the same position across reviewed games. */
    data class RepeatedMistake(val finding: GameFinding.RepeatedMistake) : NextStep {
        override val reason: String
            get() = "The same mistake appeared in ${finding.times} of ${finding.reached} reviewed games."
    }

    /** A repeated deviation from one of the learner's own opening lines. */
    data class LineSlip(val finding: GameFinding.LineSlip) : NextStep {
        override val reason: String
            get() = "You left ${finding.bookTitle} the same way in ${finding.misses} of ${finding.reached} games."
    }

    /** Difficulty converting a material advantage in a recurring ending type. */
    data class Ending(val finding: GameFinding.EndingResult) : NextStep {
        override val reason: String
            get() = "You won ${finding.won} of ${finding.reached} games after reaching this ending a pawn up."
    }

    /** A puzzle theme the player keeps missing (from the Training page). */
    data class Theme(val score: ThemeScore) : NextStep {
        override val reason: String get() =
            "${score.name} puzzles trip you up (${(score.rate * 100).roundToInt()}% clean). Practise a few."
    }

    /** A bot to play, from recent results. */
    data class Bot(val recommendation: PlayRecommendation) : NextStep {
        override val reason: String get() = recommendation.reason
    }
}

object NextStepRecommender {
    /** Scheduled mistakes take priority once this many are due. */
    const val MISTAKES_FIRST = 3

    /** A theme counts as weak below this clean-solve rate. */
    const val WEAK_RATE = 0.6f

    /** Compatibility entry point for callers that only have puzzle and review-queue evidence. */
    fun recommend(dueMistakes: Int, weaknesses: List<ThemeScore>, bot: PlayRecommendation): NextStep =
        CoachingIntelligence.recommend(
            dueMistakes = dueMistakes,
            weaknesses = weaknesses,
            findings = emptyList(),
            bot = bot,
        )
}
