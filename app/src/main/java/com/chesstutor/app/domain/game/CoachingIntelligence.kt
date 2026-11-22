package com.chesstutor.app.domain.game

import com.chesstutor.app.domain.account.ThemeScore
import com.chesstutor.app.domain.train.GameFinding

/**
 * Turns existing learning evidence into one explainable next action.
 *
 * This is a decision layer, not a chess engine: Stockfish and the existing review systems remain
 * responsible for evaluating positions. The coach only prioritises findings that already have
 * enough repeated evidence to justify practice.
 *
 * The first version is deliberately rule-based. It does not persist a second copy of progress or
 * infer a weakness from one game or one puzzle.
 */
object CoachingIntelligence {
    private const val MIN_ENDGAME_GAMES = 3

    /** A repeated pattern must appear in at least half of the relevant opportunities. */
    const val MIN_PATTERN_SHARE = 0.5

    /** An ending is surfaced only when fewer than half of the material-up games were won. */
    const val MAX_ENDGAME_WIN_RATE = 0.5

    fun recommend(
        dueMistakes: Int,
        weaknesses: List<ThemeScore>,
        findings: List<GameFinding>,
        bot: PlayRecommendation,
    ): NextStep {
        if (dueMistakes >= NextStepRecommender.MISTAKES_FIRST) {
            return NextStep.Mistakes(dueMistakes)
        }

        val repeatedMistake = findings
            .filterIsInstance<GameFinding.RepeatedMistake>()
            .filter { it.times >= 2 && it.reached >= it.times && share(it.times, it.reached) >= MIN_PATTERN_SHARE }
            .maxWithOrNull(
                compareBy<GameFinding.RepeatedMistake> { share(it.times, it.reached) }
                    .thenBy { it.times }
                    .thenBy { it.reached }
            )
        if (repeatedMistake != null) return NextStep.RepeatedMistake(repeatedMistake)

        val lineSlip = findings
            .filterIsInstance<GameFinding.LineSlip>()
            .filter { it.misses >= 2 && it.reached >= it.misses && share(it.misses, it.reached) >= MIN_PATTERN_SHARE }
            .maxWithOrNull(
                compareBy<GameFinding.LineSlip> { share(it.misses, it.reached) }
                    .thenBy { it.misses }
                    .thenBy { it.reached }
            )
        if (lineSlip != null) return NextStep.LineSlip(lineSlip)

        val ending = findings
            .filterIsInstance<GameFinding.EndingResult>()
            .filter {
                it.reached >= MIN_ENDGAME_GAMES &&
                    it.won in 0 until it.reached &&
                    share(it.won, it.reached) < MAX_ENDGAME_WIN_RATE
            }
            .minWithOrNull(
                compareBy<GameFinding.EndingResult> { share(it.won, it.reached) }
                    .thenByDescending { it.reached }
            )
        if (ending != null) return NextStep.Ending(ending)

        val weakTheme = weaknesses
            .filter { it.attempts >= 3 && it.rate < NextStepRecommender.WEAK_RATE }
            .minWithOrNull(compareBy<ThemeScore> { it.rate }.thenByDescending { it.attempts })
        if (weakTheme != null) return NextStep.Theme(weakTheme)

        return NextStep.Bot(bot)
    }

    private fun share(part: Int, total: Int): Double =
        if (total <= 0) 0.0 else part.toDouble() / total
}

