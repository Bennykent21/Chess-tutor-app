package com.chesstutor.app.domain.account

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.domain.TacticalAnalysis

/**
 * "Brilliant" and "Great" moves in a review (plan 6.4), close to how Chess.com uses them:
 * a brilliant move is a sound sacrifice played from a position that was not already won;
 * a great move is the only good move, when every other move was much worse.
 */
object SpecialMoves {
    /** A sacrifice gives up at least this much (centipawns), so a pawn alone does not count. */
    const val SACRIFICE_CP = 200

    /** A brilliant move may lose at most this much winning chance (percentage points). */
    const val BRILLIANT_MAX_LOSS = 2.0

    /** Not brilliant when the mover was already this sure to win. */
    const val ALREADY_WON = 95.0

    /** After a brilliant move the mover should still be at least level. */
    const val STILL_GOOD = 50.0

    /** A great move is better than the second-best move by at least this much (percentage points). */
    const val GREAT_GAP = 15.0

    /** After a great move the mover is still in the game. */
    const val STILL_ALIVE = 30.0

    /**
     * A sound sacrifice: a best or near-best move ([base] and the loss from [winBefore] to
     * [winAfter]) that leaves material worth at least [SACRIFICE_CP] for the opponent to take,
     * played from a position that was neither lost nor already won.
     */
    fun isBrilliant(before: Position, move: Move, after: Position, base: MoveQuality, winBefore: Double, winAfter: Double): Boolean {
        if (base != MoveQuality.BEST && base != MoveQuality.GOOD) return false
        if (winBefore - winAfter > BRILLIANT_MAX_LOSS) return false
        if (winBefore >= ALREADY_WON || winAfter < STILL_GOOD) return false
        return sacrificed(before, move, after) >= SACRIFICE_CP
    }

    /**
     * The only good move: the engine's choice ([base] is best) when its second-best move was at
     * least [GREAT_GAP] worse ([secondWin] is the mover's chances after it). Recapturing what was
     * just taken does not count: it is usually obvious.
     */
    fun isGreat(before: Position, move: Move, base: MoveQuality, winBefore: Double, secondWin: Double?, winAfter: Double, previous: String?): Boolean {
        if (base != MoveQuality.BEST || secondWin == null) return false
        if (winBefore - secondWin < GREAT_GAP || winAfter < STILL_ALIVE) return false
        val recapture = previous != null && previous.length >= 4 && previous.substring(2, 4) == move.to.algebraic && before.pieceAt(move.to) != null
        return !recapture
    }

    /**
     * Material the mover leaves for the opponent to win after [move] (the biggest loss the
     * opponent can actually force by a legal capture), minus what [move] itself captured.
     */
    fun sacrificed(before: Position, move: Move, after: Position): Int {
        val mover = before.sideToMove
        val captured = when {
            move.isEnPassant -> TacticalAnalysis.pieceValue(PieceType.PAWN)
            else -> before.pieceAt(move.to)?.let { TacticalAnalysis.pieceValue(it.type) } ?: 0
        }
        val replies = LegalMoveGenerator.generateLegalMoves(after)
        val atRisk = TacticalAnalysis.findHangingPieces(after, mover)
            // A piece only counts as given up if the opponent may legally take it.
            .filter { piece -> replies.any { it.to.algebraic == piece.square } }
            .maxOfOrNull { it.netLossCp } ?: 0
        return atRisk - captured
    }
}
