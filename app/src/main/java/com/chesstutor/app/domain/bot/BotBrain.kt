package com.chesstutor.app.domain.bot

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.repetitionKey
import com.chesstutor.app.domain.opening.Repertoire
import com.chesstutor.app.engine.EngineLine
import com.chesstutor.app.engine.EngineService
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** How a bot came to its move: for the coach strip, the game record and tests. */
enum class BotMoveKind { BOOK, ENGINE, MISTAKE, FALLBACK }

data class BotDecision(
    val uci: String,
    val kind: BotMoveKind,
    /** How long the bot should appear to think, including the engine's own search. */
    val thinkMs: Long,
    /** The bot's view of the position before its move (centipawns, its side), when the engine gave one. */
    val evalForBot: Int? = null,
)

/** A candidate move and its score from the bot's side: higher is better for the bot. */
data class BotCandidate(val uci: String, val score: Int)

/**
 * Plays a bot's moves (plan 6.1, step 3B). In book it picks from its [Repertoire]; out of
 * book it asks the engine for its best few moves at full strength, once per move, and lets
 * [BotMoveChooser] pick among them for the bot's rating and style, sometimes slipping on
 * purpose ([BotStrengthModel.mistakeChance]). One search per move keeps the phone cool.
 */
class BotBrain(private val engine: EngineService, private val random: Random = Random.Default) {

    /**
     * The bot's move in [position] ([ply] moves into the game), or null when it has none.
     * [clockMs] is the bot's remaining time, if the game has a clock.
     */
    suspend fun decide(
        position: Position,
        ply: Int,
        profile: BotProfile,
        repertoire: Repertoire,
        clockMs: Long? = null,
        /** How many plies it follows its book; Opening Battle follows the whole line. */
        bookDepth: Int = BotStrengthModel.bookDepth(profile.rating),
    ): BotDecision? {
        val legal = LegalMoveGenerator.generateLegalMoves(position).map { it.uci }
        if (legal.isEmpty()) return null
        val side = position.sideToMove

        if (ply < bookDepth) {
            val book = repertoire.movesFor(side, position.repetitionKey()).filter { it.uci in legal }
            if (book.isNotEmpty()) {
                val pick = pickWeighted(book.map { it.uci to it.weight.toDouble() }, random)
                return BotDecision(pick, BotMoveKind.BOOK, thinkMs(BotMoveKind.BOOK, profile, 1, clockMs))
            }
        }

        val fen = position.toFen()
        val lines = runCatching {
            engine.analyse(fen, lines = CANDIDATES, movetimeMs = BotStrengthModel.searchMs(profile.rating))
        }.getOrDefault(emptyList())
        val candidates = candidatesOf(lines, side).filter { it.uci in legal }
        if (candidates.isEmpty()) {
            val fallback = runCatching { engine.botMove(fen, profile.rating) }.getOrNull()?.uci?.takeIf { it in legal }
            return BotDecision(fallback ?: legal.random(random), BotMoveKind.FALLBACK, thinkMs(BotMoveKind.FALLBACK, profile, 1, clockMs))
        }
        val choice = BotMoveChooser.choose(position, candidates, legal, profile, random)
        return BotDecision(choice.uci, choice.kind, thinkMs(choice.kind, profile, choice.options, clockMs), candidates.first().score)
    }

    private fun thinkMs(kind: BotMoveKind, profile: BotProfile, options: Int, clockMs: Long?): Long {
        val base = when (kind) {
            BotMoveKind.BOOK -> 300 + random.nextInt(400)
            else -> 500 + random.nextInt(900) + if (options > 2) 500 else 0
        }
        val scaled = (base * profile.thinkSpeed.factor).toLong()
        // With a clock, never spend more than a small slice of what is left.
        return clockMs?.let { min(scaled, max(it / 40, 100)) } ?: scaled
    }

    companion object {
        const val CANDIDATES = 4

        /** Engine lines as bot-side candidates; a mate scores beyond any material count. */
        fun candidatesOf(lines: List<EngineLine>, side: PieceColor): List<BotCandidate> {
            val sign = if (side == PieceColor.WHITE) 1 else -1
            return lines.mapNotNull { line ->
                val uci = line.firstMove ?: return@mapNotNull null
                val white = when {
                    line.mateInMoves != null && line.mateInMoves != 0 ->
                        if (line.mateInMoves > 0) MATE - line.mateInMoves * 100 else -MATE - line.mateInMoves * 100
                    else -> line.centipawns ?: return@mapNotNull null
                }
                BotCandidate(uci, white * sign)
            }.distinctBy { it.uci }.sortedByDescending { it.score }
        }

        const val MATE = 100_000
    }
}

internal fun pickWeighted(options: List<Pair<String, Double>>, random: Random): String {
    val total = options.sumOf { it.second }
    if (total <= 0.0) return options.first().first
    var roll = random.nextDouble() * total
    for ((value, weight) in options) {
        roll -= weight
        if (roll < 0) return value
    }
    return options.last().first
}

/** How strength turns into behaviour. Ratings between the table rows are interpolated. */
object BotStrengthModel {
    /** Rating to how far (centipawns) below the best move a bot still considers a move fine. */
    private val tolerance = listOf(400 to 150, 800 to 100, 1200 to 70, 1600 to 45, 2000 to 25, 2400 to 10, 3000 to 0)
    /** Rating to the chance that a move out of book is a deliberate slip. */
    private val mistakes = listOf(400 to 0.30, 800 to 0.18, 1200 to 0.10, 1600 to 0.05, 2000 to 0.02, 2400 to 0.005, 3000 to 0.0)
    /** Rating to the largest loss (centipawns) a slip may cost. */
    private val slipSize = listOf(400 to 1_000, 800 to 600, 1200 to 300, 1600 to 200, 2000 to 120, 2400 to 80, 3000 to 60)

    fun tolerance(rating: Int): Int = interpolate(tolerance, rating).toInt()
    fun maxSlip(rating: Int): Int = interpolate(slipSize, rating).toInt()

    fun mistakeChance(profile: BotProfile): Double =
        (interpolate(mistakes, profile.rating) * profile.mistakeLevel).coerceIn(0.0, 0.6)

    /** Below 1000 some slips are a random move (a beginner's blunder), not just a weaker line. */
    fun wildShare(rating: Int): Double = ((1000 - rating) / 1000.0).coerceIn(0.0, 0.75)

    /** How many plies a bot follows its book: beginners leave theory early. */
    fun bookDepth(rating: Int): Int = when {
        rating < 600 -> 4
        rating < 1000 -> 6
        rating < 1400 -> 10
        rating < 1800 -> 16
        else -> Int.MAX_VALUE
    }

    /** Engine search time per move; weak bots do not need deep searches. */
    fun searchMs(rating: Int): Int = when {
        rating < 1000 -> 250
        rating < 1600 -> 400
        rating < 2200 -> 600
        else -> 800
    }

    private fun interpolate(table: List<Pair<Int, Number>>, rating: Int): Double {
        if (rating <= table.first().first) return table.first().second.toDouble()
        val upper = table.indexOfFirst { it.first >= rating }
        if (upper < 0) return table.last().second.toDouble()
        val (r0, v0) = table[upper - 1]
        val (r1, v1) = table[upper]
        val t = (rating - r0).toDouble() / (r1 - r0)
        return v0.toDouble() + (v1.toDouble() - v0.toDouble()) * t
    }
}

/** Picks among scored candidates for a bot's rating and style. Pure, so it is tested directly. */
object BotMoveChooser {
    data class Choice(val uci: String, val kind: BotMoveKind, val options: Int)

    /** Smallest loss that counts as a slip; anything less is just a different good move. */
    const val MIN_SLIP = 60
    private const val STYLE_STRENGTH = 1.2

    fun choose(position: Position, candidates: List<BotCandidate>, legal: List<String>, profile: BotProfile, random: Random): Choice {
        val best = candidates.first().score
        if (random.nextDouble() < BotStrengthModel.mistakeChance(profile)) {
            slip(candidates, legal, best, profile.rating, random)?.let { return it }
        }

        val tolerance = BotStrengthModel.tolerance(profile.rating)
        val mating = best > BotBrain.MATE / 2 && profile.rating >= 1000
        val fine = candidates.filter { best - it.score <= tolerance && (!mating || it.score > BotBrain.MATE / 2) }
        val moves = fine.map { candidate -> position.legalMove(candidate.uci) to candidate }
        val weights = moves.map { (move, candidate) ->
            val closeness = exp(-(best - candidate.score) / max(tolerance / 2.0, 1.0))
            val lean = profile.aggression * BotStyleScore.aggression(position, move) + profile.trades * BotStyleScore.trade(position, move)
            candidate.uci to closeness * exp(STYLE_STRENGTH * lean)
        }
        return Choice(pickWeighted(weights, random), BotMoveKind.ENGINE, fine.size)
    }

    private fun slip(candidates: List<BotCandidate>, legal: List<String>, best: Int, rating: Int, random: Random): Choice? {
        if (random.nextDouble() < BotStrengthModel.wildShare(rating)) {
            val unseen = legal - candidates.first().uci
            if (unseen.isNotEmpty()) return Choice(unseen.random(random), BotMoveKind.MISTAKE, 1)
        }
        val maxSlip = BotStrengthModel.maxSlip(rating)
        val worse = candidates.filter { abs(best - it.score) in MIN_SLIP..maxSlip }
        return worse.takeIf { it.isNotEmpty() }?.let { Choice(it.random(random).uci, BotMoveKind.MISTAKE, 1) }
    }

    private fun Position.legalMove(uci: String): Move = LegalMoveGenerator.generateLegalMoves(this).first { it.uci == uci }
}

/** How well a move fits a style, each from -1 to 1. Simple board features, no search. */
object BotStyleScore {

    /** Checks, captures, moves toward the enemy king and pawn storms score high; retreats score low. */
    fun aggression(position: Position, move: Move): Float {
        val mover = position.pieceAt(move.from) ?: return 0f
        val after = LegalMoveGenerator.makeMove(position, move)
        val enemyKing = position.findKing(mover.color.opposite())
        var score = 0f
        if (LegalMoveGenerator.isKingInCheck(after, after.sideToMove)) score += 0.6f
        if (position.pieceAt(move.to) != null || move.isEnPassant) score += 0.3f
        val before = distance(move.from.file, move.from.rank, enemyKing.file, enemyKing.rank)
        val now = distance(move.to.file, move.to.rank, enemyKing.file, enemyKing.rank)
        if (mover.type != PieceType.KING && now < before) score += if (now <= 2) 0.3f else 0.15f
        val forward = if (mover.color == PieceColor.WHITE) move.to.rank - move.from.rank else move.from.rank - move.to.rank
        if (mover.type == PieceType.PAWN && abs(move.to.file - enemyKing.file) <= 1 && forward > 0) score += 0.2f
        if (mover.type != PieceType.PAWN && forward < 0 && position.pieceAt(move.to) == null) score -= 0.4f
        return score.coerceIn(-1f, 1f)
    }

    /** Even trades of pieces (not pawns) score 1, queen trades most of all; other moves 0. */
    fun trade(position: Position, move: Move): Float {
        val mover = position.pieceAt(move.from) ?: return 0f
        val taken = position.pieceAt(move.to) ?: return 0f
        if (mover.type == PieceType.PAWN || mover.type == PieceType.KING) return 0f
        val even = abs(mover.type.value - taken.type.value) <= 30
        return when {
            even && mover.type == PieceType.QUEEN -> 1f
            even -> 0.7f
            else -> 0f
        }
    }

    private fun distance(f1: Int, r1: Int, f2: Int, r2: Int) = max(abs(f1 - f2), abs(r1 - r2))
}

/** When a bot gives up (rules in the bot builder). Scores are the bot's own, after each of its moves. */
object BotResign {
    fun shouldResign(profile: BotProfile, recentEvals: List<Int>): Boolean {
        // Beginners play on: a kid's bot resigning is no fun, and the player should learn to mate.
        if (profile.rating < 600) return false
        val (count, threshold) = when (profile.resign) {
            ResignPolicy.NEVER -> return false
            ResignPolicy.WHEN_LOST -> 3 to -900
            ResignPolicy.EARLY -> 2 to -500
        }
        return recentEvals.size >= count && recentEvals.takeLast(count).all { it <= threshold }
    }
}
