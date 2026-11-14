package com.chesstutor.app.domain.train

import com.chesstutor.app.core.GameState
import com.chesstutor.app.core.GameStatus
import com.chesstutor.app.core.PieceColor

/** An engine score from the learner's side: mate in [mate] moves (negative when they get mated), else [cp] centipawns. */
data class LearnerScore(val cp: Int?, val mate: Int?) {
    val winning: Boolean get() = mate?.let { it > 0 } ?: false
    val losing: Boolean get() = mate?.let { it < 0 } ?: false

    companion object {
        /** From the engine's White-side score. */
        fun of(whiteCp: Int?, whiteMate: Int?, learner: PieceColor): LearnerScore {
            val sign = if (learner == PieceColor.WHITE) 1 else -1
            return LearnerScore(whiteCp?.times(sign), whiteMate?.times(sign))
        }
    }
}

enum class PositionOutcome { PLAYING, WON, HELD, SLIPPED }

/**
 * Says when a position played out against Stockfish is done (Phase 12, step 3). A win is a mate,
 * or a promotion that keeps a winning score; a convert is a score grown to [CONVERTED_CP]; a hold
 * lasts [HOLD_MOVES] moves or ends drawn. Whenever the score falls out of the goal's band after the
 * learner's move, that move let it slip.
 */
object PositionJudge {
    /** Below this a won position has become a draw. Stockfish shows a drawn ending as about 0. */
    const val WIN_KEPT_CP = 150

    /** After a promotion, a score this high (or a mate) counts as won. */
    const val PROMOTED_CP = 1000

    /** A middlegame edge grown to this is converted; below [CONVERT_KEPT_CP] it is gone. */
    const val CONVERTED_CP = 500
    const val CONVERT_KEPT_CP = 80

    /** A hold is lost below this. */
    const val HOLD_LOST_CP = -300

    /** A hold that lasts this many of the learner's moves is held. */
    const val HOLD_MOVES = 15

    /** The outcome when the game itself has ended (mate, stalemate, a draw by rule), else null. */
    fun ended(goal: PositionGoal, game: GameState, learner: PieceColor): PositionOutcome? {
        val status = game.status
        return when {
            status == GameStatus.CHECKMATE -> if (game.position.sideToMove == learner) PositionOutcome.SLIPPED else PositionOutcome.WON
            game.isOver || status == GameStatus.DRAW_THREEFOLD_REPETITION || status == GameStatus.DRAW_50_MOVES ->
                if (goal == PositionGoal.HOLD) PositionOutcome.HELD else PositionOutcome.SLIPPED
            else -> null
        }
    }

    /** The outcome after the learner's move, from the engine's score of the position it left. */
    fun scored(goal: PositionGoal, score: LearnerScore, promoted: Boolean, learnerMoves: Int): PositionOutcome {
        val cp = score.cp ?: 0
        return when (goal) {
            PositionGoal.WIN -> when {
                score.losing || (!score.winning && cp < WIN_KEPT_CP) -> PositionOutcome.SLIPPED
                promoted && (score.winning || cp >= PROMOTED_CP) -> PositionOutcome.WON
                else -> PositionOutcome.PLAYING
            }
            PositionGoal.CONVERT -> when {
                score.winning || cp >= CONVERTED_CP -> PositionOutcome.WON
                score.losing || cp < CONVERT_KEPT_CP -> PositionOutcome.SLIPPED
                else -> PositionOutcome.PLAYING
            }
            PositionGoal.HOLD -> when {
                score.losing || (score.mate == null && cp <= HOLD_LOST_CP) -> PositionOutcome.SLIPPED
                learnerMoves >= HOLD_MOVES -> PositionOutcome.HELD
                else -> PositionOutcome.PLAYING
            }
        }
    }
}
