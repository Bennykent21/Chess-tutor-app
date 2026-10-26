package com.chesstutor.app.domain.account

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.SanFormatter
import com.chesstutor.app.engine.WinProbability

/**
 * The engine's view of one position of a game (plan 7.4 `analysis_result`). [ply] 0 is the
 * starting position and ply n the position after the game's n-th move. Scores are from
 * White's side; a finished game (mate or stalemate) is scored without the engine.
 */
data class PlyEval(
    val ply: Int,
    val centipawns: Int?,
    val mate: Int?,
    /** The best move here in UCI, or "" when the game is over. */
    val bestMove: String,
    val line: List<String> = emptyList(),
    val depth: Int? = null,
    /** True when the weak built-in engine answered because Stockfish did not. */
    val lite: Boolean = false,
    /** The engine's second-best line here (White's side), for spotting only-moves ("Great"). Null when unknown. */
    val secondCentipawns: Int? = null,
    val secondMate: Int? = null,
) {
    /** True when the engine gave a second-best line. */
    val hasSecond: Boolean get() = secondCentipawns != null || secondMate != null

    /** The mover's winning chances after the second-best move, 0 to 100, or null when unknown. */
    fun secondWinFor(color: PieceColor): Double? {
        if (!hasSecond) return null
        val white = WinProbability.fromScore(secondCentipawns, secondMate)
        return if (color == PieceColor.WHITE) white else 100.0 - white
    }

    /** White's winning chances, 0 to 100. */
    val whiteWin: Double get() = WinProbability.fromScore(centipawns, mate)

    fun winFor(color: PieceColor): Double = if (color == PieceColor.WHITE) whiteWin else 100.0 - whiteWin

    /** Winning chances as Lichess counts them for accuracy (scores capped at ten pawns). */
    fun lichessWinFor(color: PieceColor): Double = LichessAccuracy.winPercent(centipawns, mate).let { if (color == PieceColor.WHITE) it else 100.0 - it }

    companion object {
        /** A large score for a delivered mate, so it reads as won (or lost) everywhere. */
        const val MATED_SCORE = 10_000
    }
}

/** Which part of the game a move belongs to (plan 6.4, accuracy by phase). */
enum class GamePhase(val label: String) {
    OPENING("Opening"),
    MIDDLEGAME("Middlegame"),
    ENDGAME("Endgame");

    companion object {
        /** Pieces other than kings and pawns, in pawns (queen 9, rook 5, bishop and knight 3). */
        fun materialOf(position: Position): Int = position.squares.sumOf { piece ->
            when (piece?.type) {
                PieceType.QUEEN -> 9
                PieceType.ROOK -> 5
                PieceType.BISHOP, PieceType.KNIGHT -> 3
                else -> 0
            }
        }

        /**
         * The endgame starts once both sides together have at most [ENDGAME_MATERIAL] in pieces
         * (for example a rook and a minor piece each); before that, the first [OPENING_PLIES]
         * plies are the opening.
         */
        fun of(ply: Int, position: Position): GamePhase = when {
            materialOf(position) <= ENDGAME_MATERIAL -> ENDGAME
            ply <= OPENING_PLIES -> OPENING
            else -> MIDDLEGAME
        }

        const val ENDGAME_MATERIAL = 26
        const val OPENING_PLIES = 20
    }
}

/** How good a move was (plan 6.4), judged by how much of the mover's winning chances it threw away. */
enum class MoveQuality(val label: String, val symbol: String) {
    BOOK("Book", ""),
    /** A good sacrifice (see [SpecialMoves]). */
    BRILLIANT("Brilliant", "!!"),
    /** The only good move: everything else was much worse (see [SpecialMoves]). */
    GREAT("Great", "!"),
    BEST("Best", ""),
    GOOD("Good", ""),
    INACCURACY("Inaccuracy", "?!"),
    MISTAKE("Mistake", "?"),
    BLUNDER("Blunder", "??");

    val isError: Boolean get() = this == INACCURACY || this == MISTAKE || this == BLUNDER

    /** Best, Great or Brilliant: nothing better to show. */
    val isTop: Boolean get() = this == BEST || this == GREAT || this == BRILLIANT

    companion object {
        /**
         * Thresholds on lost winning chances (percentage points), close to Chess.com's
         * expected-points bands: up to 5 is still good, then inaccuracy, mistake, blunder.
         */
        fun of(loss: Double, playedBest: Boolean): MoveQuality = when {
            playedBest || loss <= 1.0 -> BEST
            loss <= 5.0 -> GOOD
            loss <= 10.0 -> INACCURACY
            loss <= 20.0 -> MISTAKE
            else -> BLUNDER
        }
    }
}

/** One move of a reviewed game. [ply] is 1 for White's first move. */
data class ReviewedMove(
    val ply: Int,
    val uci: String,
    val san: String,
    val mover: PieceColor,
    val quality: MoveQuality,
    /** The mover's winning chances before and after the move, 0 to 100. */
    val winBefore: Double,
    val winAfter: Double,
    val bestMove: String,
    val bestSan: String?,
    /** Move accuracy, 0 to 100 (Lichess's formula). */
    val accuracy: Double,
    /** The position the move was played from. */
    val fenBefore: String,
    val phase: GamePhase = GamePhase.MIDDLEGAME,
) {
    val loss: Double get() = (winBefore - winAfter).coerceAtLeast(0.0)
    val moveNumber: Int get() = (ply + 1) / 2

    /** "14." for White, "14..." for Black. */
    val numberLabel: String get() = if (mover == PieceColor.WHITE) "$moveNumber." else "$moveNumber..."
}

/** Move-quality counts and accuracy for one side. */
data class SideReport(val accuracy: Double?, val counts: Map<MoveQuality, Int>) {
    fun count(quality: MoveQuality): Int = counts[quality] ?: 0
}

/**
 * A game with every analysed move judged (plan 6.4): qualities for the move list, the
 * evaluation graph, accuracy for each side and the player's key moments.
 */
data class GameReview(
    val moves: List<ReviewedMove>,
    /** White's winning chances after each analysed position (ply 0 first), 0 to 1, for the graph. */
    val graph: List<Float>,
    val totalPlies: Int,
    /** Each side's game accuracy (Lichess's method, [LichessAccuracy]) over the analysed moves. */
    val accuracies: Map<PieceColor, Double?> = emptyMap(),
) {
    /** True once every position has been analysed. */
    val complete: Boolean get() = moves.size == totalPlies

    fun move(ply: Int): ReviewedMove? = moves.getOrNull(ply - 1)?.takeIf { it.ply == ply }

    fun report(side: PieceColor): SideReport {
        val mine = moves.filter { it.mover == side }
        val judged = mine.filter { it.quality != MoveQuality.BOOK }
        return SideReport(
            accuracy = accuracies[side] ?: GameReviewer.accuracyOf(mine.map { it.accuracy }),
            counts = MoveQuality.entries.associateWith { q -> (if (q == MoveQuality.BOOK) mine else judged).count { it.quality == q } },
        )
    }

    /** The player's worst moves (mistakes and blunders), biggest first, at most [limit], then in game order. */
    fun keyMoments(player: PieceColor, limit: Int = 3): List<ReviewedMove> =
        moves.filter { it.mover == player && (it.quality == MoveQuality.MISTAKE || it.quality == MoveQuality.BLUNDER) }
            .sortedByDescending { it.loss }
            .take(limit)
            .sortedBy { it.ply }
}

/** Builds a [GameReview] from a game and the engine's view of its positions. Pure. */
object GameReviewer {

    /** Every position of the game from [startFen], or as far as the moves stay legal. */
    fun positions(startFen: String, moves: List<String>): List<Position> {
        var position = Position.tryFromFen(startFen).getOrNull() ?: return emptyList()
        val out = mutableListOf(position)
        for (uci in moves) {
            val legal = LegalMoveGenerator.generateLegalMoves(position).firstOrNull { it.uci == uci } ?: break
            position = LegalMoveGenerator.makeMove(position, legal)
            out += position
        }
        return out
    }

    /** The score of a finished game's last position, or null while moves remain. */
    fun terminalEval(ply: Int, position: Position): PlyEval? {
        if (LegalMoveGenerator.generateLegalMoves(position).isNotEmpty()) return null
        val mated = LegalMoveGenerator.isKingInCheck(position)
        val score = when {
            !mated -> 0
            position.sideToMove == PieceColor.WHITE -> -PlyEval.MATED_SCORE
            else -> PlyEval.MATED_SCORE
        }
        return PlyEval(ply = ply, centipawns = score, mate = if (mated) 0 else null, bestMove = "")
    }

    /**
     * Judges each move whose before and after positions are both in [evals]. Moves up to
     * [bookPlies] (the opening database's moves) count as book moves.
     */
    fun review(startFen: String, moves: List<String>, evals: List<PlyEval>, bookPlies: Int = 0): GameReview {
        val positions = positions(startFen, moves)
        val byPly = evals.associateBy { it.ply }
        val reviewed = mutableListOf<ReviewedMove>()
        for (i in 0 until positions.size - 1) {
            val before = byPly[i] ?: break
            val after = byPly[i + 1] ?: break
            val position = positions[i]
            val mover = position.sideToMove
            val legal = LegalMoveGenerator.generateLegalMoves(position)
            val played = legal.first { it.uci == moves[i] }
            val best = legal.firstOrNull { it.uci == before.bestMove }
            val winBefore = before.winFor(mover)
            val winAfter = after.winFor(mover)
            val loss = (winBefore - winAfter).coerceAtLeast(0.0)
            val book = i < bookPlies
            val base = MoveQuality.of(loss, playedBest = best?.uci == played.uci)
            val quality = when {
                book -> MoveQuality.BOOK
                SpecialMoves.isBrilliant(position, played, positions[i + 1], base, winBefore, winAfter) -> MoveQuality.BRILLIANT
                SpecialMoves.isGreat(position, played, base, winBefore, before.secondWinFor(mover), winAfter, previous = moves.getOrNull(i - 1)) -> MoveQuality.GREAT
                else -> base
            }
            reviewed += ReviewedMove(
                ply = i + 1,
                uci = played.uci,
                san = SanFormatter.format(position, played),
                mover = mover,
                quality = quality,
                winBefore = winBefore,
                winAfter = winAfter,
                bestMove = before.bestMove,
                bestSan = best?.let { SanFormatter.format(position, it) },
                accuracy = LichessAccuracy.moveAccuracy(before.lichessWinFor(mover), after.lichessWinFor(mover)),
                fenBefore = position.toFen(),
                phase = GamePhase.of(i + 1, position),
            )
        }
        val graph = positions.indices.asSequence()
            .map { byPly[it] }
            .takeWhile { it != null }
            .map { (it!!.whiteWin / 100.0).toFloat() }
            .toList()
        val lichessWins = positions.indices.asSequence().map { byPly[it] }.takeWhile { it != null }
            .map { LichessAccuracy.winPercent(it!!.centipawns, it.mate) }.toList()
        val firstMover = positions.firstOrNull()?.sideToMove ?: PieceColor.WHITE
        return GameReview(reviewed, graph, totalPlies = positions.size - 1, accuracies = LichessAccuracy.gameAccuracy(lichessWins, firstMover))
    }

    /**
     * An accuracy over any set of moves (a game phase, many games): the average of the plain
     * and harmonic means, so one blunder weighs more than in a plain average.
     */
    fun accuracyOf(moveAccuracies: List<Double>): Double? {
        if (moveAccuracies.isEmpty()) return null
        val mean = moveAccuracies.average()
        val harmonic = moveAccuracies.size / moveAccuracies.sumOf { 1.0 / it.coerceAtLeast(1.0) }
        return (mean + harmonic) / 2.0
    }
}
