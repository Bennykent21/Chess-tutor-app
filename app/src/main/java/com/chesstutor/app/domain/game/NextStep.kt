package com.chesstutor.app.domain.game

import com.chesstutor.app.domain.account.ThemeScore
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
)

/** Home's "Recommended" card (plan 6.1 and 6.4): the most useful thing to do next, and why. */
sealed interface NextStep {
    val reason: String

    /** Mistakes from the player's games are waiting in Puzzles > Mistakes review. */
    data class Mistakes(val count: Int) : NextStep {
        override val reason: String get() = "$count mistakes from your games are ready to fix."
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
    /** Mistakes come first once this many are due. */
    const val MISTAKES_FIRST = 3

    /** A theme counts as weak below this clean-solve rate. */
    const val WEAK_RATE = 0.6f

    /**
     * Due mistakes first (fixing your own errors teaches the most), then the weakest puzzle
     * theme, else a bot to play. [weaknesses] come worst first, as on the Training page.
     */
    fun recommend(dueMistakes: Int, weaknesses: List<ThemeScore>, bot: PlayRecommendation): NextStep {
        if (dueMistakes >= MISTAKES_FIRST) return NextStep.Mistakes(dueMistakes)
        weaknesses.firstOrNull { it.rate < WEAK_RATE }?.let { return NextStep.Theme(it) }
        return NextStep.Bot(bot)
    }
}
