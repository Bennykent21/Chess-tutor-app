package com.chesstutor.app.domain.account

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.SanFormatter
import java.util.Locale
import kotlin.math.abs

/** What the coach says about one move: a headline ("Nf6 is a blunder") and a sentence or two. */
data class MoveComment(val headline: String, val detail: String)

/**
 * The coach's words in a game review (plan 6.4): one comment per move from the engine's
 * verdicts, and a one-line verdict on the whole game. Pure, so it is unit tested.
 */
object ReviewCommentary {

    /**
     * [after] is the engine's view of the position after [move] (its best move is the
     * opponent's best reply), [positionAfter] that position, [openingName] the game's opening.
     */
    fun comment(move: ReviewedMove, after: PlyEval?, positionAfter: Position?, openingName: String? = null): MoveComment {
        val san = move.san
        val headline = when (move.quality) {
            MoveQuality.BOOK -> "$san is a book move"
            MoveQuality.FORCED -> "$san is forced"
            MoveQuality.BRILLIANT -> "$san is brilliant!"
            MoveQuality.GREAT -> "$san is a great move"
            MoveQuality.BEST -> "$san is best"
            MoveQuality.EXCELLENT -> "$san is excellent"
            MoveQuality.GOOD -> "$san is good"
            MoveQuality.INACCURACY -> "$san is an inaccuracy"
            MoveQuality.MISS -> "$san is a miss"
            MoveQuality.MISTAKE -> "$san is a mistake"
            MoveQuality.BLUNDER -> "$san is a blunder"
        }
        val detail = when (move.quality) {
            MoveQuality.BOOK -> openingName?.let { "A known move in the ${it.substringBefore(':')}." } ?: "A known opening move."
            MoveQuality.BRILLIANT -> "A sacrifice that works: the material you give up comes back with interest."
            MoveQuality.GREAT -> "The only good move here. Everything else let your chances slip."
            MoveQuality.BEST -> when {
                san.endsWith("#") -> "Checkmate!"
                san.endsWith("+") -> "A strong check, and the engine's first choice."
                "x" in san -> "Taking was exactly right."
                move.uci != move.bestMove && move.bestSan != null -> "As strong as the engine's first choice, ${move.bestSan}."
                else -> "The engine's first choice."
            }
            MoveQuality.FORCED -> "The only legal move."
            MoveQuality.EXCELLENT -> move.bestSan?.let { "Nearly as strong as $it." } ?: "Nearly as strong as the engine's first choice."
            MoveQuality.GOOD -> move.bestSan?.let { "A solid move. $it was a touch more precise." } ?: "A solid move."
            MoveQuality.MISS -> move.bestSan?.let { "Your opponent slipped, and $it would have made them pay." } ?: "Your opponent slipped, and this let them off."
            else -> errorDetail(move, after, positionAfter)
        }
        return MoveComment(headline, detail)
    }

    private fun errorDetail(move: ReviewedMove, after: PlyEval?, positionAfter: Position?): String {
        val parts = mutableListOf<String>()
        val opponent = move.mover.opposite()
        val mate = after?.mate
        val opponentMates = mate != null && mate != 0 && (mate > 0) == (opponent == PieceColor.WHITE)
        val reply = replyOf(after, positionAfter)
        when {
            move.winBefore >= 100.0 -> parts += "You had a forced mate here."
            opponentMates -> parts += "It allows mate in ${abs(mate!!)}" + (reply?.let { ", starting with ${it.san}." } ?: ".")
            reply?.captured != null -> parts += "It lets ${reply.san} win your ${reply.captured}."
            move.winBefore >= WINNING && move.winAfter <= UNCLEAR -> parts += "Your advantage slips away."
            reply != null -> parts += "${reply.san} is the strong answer."
        }
        move.bestSan?.let { parts += "Best was $it." }
        return parts.joinToString(" ")
    }

    private data class Reply(val san: String, val captured: String?)

    private fun replyOf(after: PlyEval?, position: Position?): Reply? {
        val uci = after?.bestMove?.takeIf { it.length >= 4 } ?: return null
        val pos = position ?: return null
        val move = LegalMoveGenerator.generateLegalMoves(pos).firstOrNull { it.uci == uci } ?: return null
        val taken = pos.pieceAt(move.to)?.takeIf { it.color != pos.sideToMove }?.type
        return Reply(SanFormatter.format(pos, move), taken?.let(::pieceName))
    }

    private fun pieceName(type: PieceType): String = when (type) {
        PieceType.PAWN -> "pawn"
        PieceType.KNIGHT -> "knight"
        PieceType.BISHOP -> "bishop"
        PieceType.ROOK -> "rook"
        PieceType.QUEEN -> "queen"
        PieceType.KING -> "king"
    }

    /** "+0.74", "-1.20", "M3" (White mates), "-M2" (Black mates), "1-0" once mated. */
    fun evalLabel(eval: PlyEval?): String {
        eval ?: return ""
        val mate = eval.mate
        return when {
            mate == 0 -> if ((eval.centipawns ?: 0) >= 0) "1-0" else "0-1"
            mate != null -> if (mate > 0) "M$mate" else "-M${-mate}"
            else -> String.format(Locale.US, "%+.2f", (eval.centipawns ?: 0) / 100.0).replace("+0.00", "0.00")
        }
    }

    /** The coach's one-line verdict on the game, from the player's side. */
    fun summary(review: GameReview, player: PieceColor, outcome: GameOutcome): String {
        val mine = review.moves.filter { it.mover == player }
        if (mine.isEmpty()) return "Analyse the game and I'll tell you how it went."
        val slips = mine.count { it.quality == MoveQuality.MISTAKE || it.quality == MoveQuality.BLUNDER }
        val worst = review.keyMoments(player, limit = 1).firstOrNull()
        val turning = worst?.let { "${it.numberLabel} ${it.san}" }
        val best = worst?.bestSan
        return when (outcome) {
            GameOutcome.WIN -> when {
                slips == 0 -> "A clean win: not a single mistake or blunder. Well played!"
                else -> "You won, with $slips slip${if (slips == 1) "" else "s"} on the way. The biggest was $turning" + (best?.let { "; $it was the move." } ?: ".")
            }
            GameOutcome.LOSS -> when {
                turning != null -> "The turning point was $turning" + (best?.let { ". $it would have kept you in the game." } ?: ".")
                else -> "No big blunder from you: this one slipped away through small inaccuracies."
            }
            GameOutcome.DRAW -> when {
                turning != null -> "A draw. Your biggest chance to do better was $turning" + (best?.let { " ($it)." } ?: ".")
                else -> "A solid draw with no mistakes from you."
            }
            GameOutcome.UNKNOWN -> turning?.let { "Your biggest slip was $it." } ?: "No mistakes from you so far."
        }
    }

    private const val WINNING = 75.0
    private const val UNCLEAR = 60.0
}
