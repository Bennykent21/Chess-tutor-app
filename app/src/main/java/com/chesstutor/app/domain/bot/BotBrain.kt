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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chesstutor.app.domain.bot.maia.MaiaPolicy
import kotlin.math.exp
import kotlin.math.pow

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

/** Where a bot finds the Maia network for a rating band (1100 to 1900); null if none is available. */
fun interface MaiaSource {
    suspend fun policyFor(net: Int): MaiaPolicy?
}

/**
 * Plays a bot's moves (plan 6.1, step 3B; reworked by D26). In book it picks from its
 * [Repertoire]. Out of book, bots up to about 2400 play like people: Maia, trained on human
 * games of their rating band, suggests the moves a human would play and the bot picks one
 * of them by chance; stronger bots check Maia's suggestion with a shallow Stockfish search
 * and reject real blunders. Without Maia (or above 2400) the bot searches only as deep as its
 * rating allows and picks like Stockfish's Skill Level ([SkillPicker]). Beginners sometimes
 * play a random move.
 */
class BotBrain(
    private val engine: EngineService,
    private val random: Random = Random.Default,
    private val maia: MaiaSource? = null,
    /** Where the network runs; never the main thread. */
    private val compute: CoroutineContext = Dispatchers.Default,
) {

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
        /** The game so far, oldest first, ending with [position]; Maia reads the last eight. */
        history: List<Position> = listOf(position),
        fromStandardStart: Boolean = true,
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

        val strength = BotStrengthModel.settingsFor(profile, maiaAvailable = maia != null)
        if (random.nextDouble() < strength.randomMoveChance) {
            return BotDecision(legal.random(random), BotMoveKind.MISTAKE, thinkMs(BotMoveKind.MISTAKE, profile, 1, clockMs))
        }
        strength.maia?.let { settings ->
            maiaMove(position, history, fromStandardStart, settings, profile, legal, clockMs)?.let { return it }
        }

        val fen = position.toFen()
        val lines = runCatching {
            if (strength.depth == null) engine.analyse(fen, lines = CANDIDATES, movetimeMs = FULL_STRENGTH_MS)
            else engine.analyseToDepth(fen, lines = CANDIDATES, depth = strength.depth)
        }.getOrDefault(emptyList())
        val candidates = candidatesOf(lines, side).filter { it.uci in legal }
        if (candidates.isEmpty()) {
            val fallback = runCatching { engine.botMove(fen, profile.rating) }.getOrNull()?.uci?.takeIf { it in legal }
            return BotDecision(fallback ?: legal.random(random), BotMoveKind.FALLBACK, thinkMs(BotMoveKind.FALLBACK, profile, 1, clockMs))
        }
        val choice = SkillPicker.pick(position, candidates, strength.skillLevel, profile, random)
        return BotDecision(choice.uci, choice.kind, thinkMs(choice.kind, profile, candidates.size, clockMs), candidates.first().score)
    }

    /**
     * Maia's human move: sampled from its probabilities (sharpened or flattened by the
     * temperature, tilted a little by the bot's style). With a check, a pick that a shallow
     * search finds losing more than the margin is dropped for the next one.
     */
    private suspend fun maiaMove(
        position: Position,
        history: List<Position>,
        fromStandardStart: Boolean,
        settings: MaiaSettings,
        profile: BotProfile,
        legal: List<String>,
        clockMs: Long?,
    ): BotDecision? {
        val policy = runCatching { maia?.policyFor(settings.net) }.getOrNull() ?: return null
        val probabilities = withContext(compute) { policy.probabilities(history, fromStandardStart) }
        if (probabilities.isEmpty()) return null
        val order = MaiaSampler.order(position, probabilities, settings.temperature, profile, random, limit = MAX_CHECKS)
        val fen = position.toFen()
        val best = bestLine(fen, position.sideToMove, settings.checkDepth ?: EVAL_DEPTH)
        val margin = settings.checkMargin
        if (margin == null || best == null) {
            val pick = order.first()
            return BotDecision(pick, BotMoveKind.ENGINE, thinkMs(BotMoveKind.ENGINE, profile, order.size, clockMs), best?.score)
        }
        for (uci in order) {
            if (uci !in legal) continue
            val score = scoreAfter(position, uci, settings.checkDepth ?: EVAL_DEPTH) ?: continue
            val loss = best.score - score
            if (loss <= margin) {
                val kind = if (loss >= SkillPicker.MISTAKE_CP) BotMoveKind.MISTAKE else BotMoveKind.ENGINE
                return BotDecision(uci, kind, thinkMs(kind, profile, order.size, clockMs), best.score)
            }
        }
        // Every human-looking move failed the check: play the engine's move.
        return BotDecision(best.uci, BotMoveKind.ENGINE, thinkMs(BotMoveKind.ENGINE, profile, order.size, clockMs), best.score)
    }

    /** The best move of a shallow search and its score for [side]. */
    private suspend fun bestLine(fen: String, side: PieceColor, depth: Int): BotCandidate? =
        runCatching { candidatesOf(engine.analyseToDepth(fen, lines = 1, depth = depth), side).firstOrNull() }.getOrNull()

    /** How good [uci] is for the side playing it: the opponent's best reply, negated. */
    private suspend fun scoreAfter(position: Position, uci: String, depth: Int): Int? {
        val move = LegalMoveGenerator.generateLegalMoves(position).firstOrNull { it.uci == uci } ?: return null
        val next = LegalMoveGenerator.makeMove(position, move)
        if (LegalMoveGenerator.generateLegalMoves(next).isEmpty()) {
            return if (LegalMoveGenerator.isKingInCheck(next, next.sideToMove)) MATE else 0
        }
        val reply = bestLine(next.toFen(), next.sideToMove, (depth - 1).coerceAtLeast(1)) ?: return null
        return -reply.score
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
        /** Search time for a full-strength bot, which has no depth limit. */
        const val FULL_STRENGTH_MS = 800
        /** Maia picks checked before the bot falls back to the engine's move. */
        const val MAX_CHECKS = 4
        /** A quick search for the bot's own view (chat and resigning) when it does not check. */
        const val EVAL_DEPTH = 4

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

/**
 * What strength means for one bot: Stockfish skill level (0 to 20), search depth and random
 * moves for the engine mode, or [maia] for the human-like mode.
 */
data class StrengthSettings(val skillLevel: Int, val depth: Int?, val randomMoveChance: Double, val maia: MaiaSettings? = null)

/**
 * The human-like mode: which Maia network (rating band 1100 to 1900), how freely to sample
 * its moves (1 = as often as people play them, lower = more often the favourite), and for
 * strong bots a shallow Stockfish check that drops picks losing more than [checkMargin].
 */
data class MaiaSettings(val net: Int, val temperature: Double, val checkDepth: Int? = null, val checkMargin: Int? = null)

/**
 * Rating (Chess.com scale, D27) to engine settings, interpolated between rows. Built like
 * Lichess's computer levels, which pair Stockfish's Skill Level with a shallow depth cap
 * (fishnet: levels 1 to 5 search depth 5, level 8 depth 22). A first estimate, to be
 * measured against Maia's human-calibrated networks (D26).
 */
object BotStrengthModel {
    private data class Row(val rating: Int, val level: Double, val depth: Double, val random: Double)

    private val table = listOf(
        Row(250, 0.0, 1.0, 0.25),
        Row(500, 0.0, 1.0, 0.12),
        Row(800, 1.0, 2.0, 0.04),
        Row(1000, 2.0, 3.0, 0.0),
        Row(1200, 3.0, 4.0, 0.0),
        Row(1400, 5.0, 5.0, 0.0),
        Row(1600, 7.0, 6.0, 0.0),
        Row(1800, 9.0, 7.0, 0.0),
        Row(2000, 11.0, 8.0, 0.0),
        Row(2200, 13.0, 10.0, 0.0),
        Row(2500, 16.0, 13.0, 0.0),
        Row(2800, 19.0, 18.0, 0.0),
    )

    /** Rating to the human-like mode, up to [MAIA_UP_TO]. A first estimate, to be measured (D26). */
    private data class MaiaRow(val rating: Int, val net: Int, val temperature: Double, val random: Double, val checkDepth: Int?, val margin: Int?)

    private val maiaTable = listOf(
        MaiaRow(250, 1100, 2.0, 0.20, null, null),
        MaiaRow(500, 1100, 1.6, 0.10, null, null),
        MaiaRow(800, 1100, 1.25, 0.03, null, null),
        MaiaRow(1000, 1100, 1.0, 0.0, null, null),
        MaiaRow(1200, 1300, 1.0, 0.0, null, null),
        MaiaRow(1400, 1600, 0.9, 0.0, null, null),
        MaiaRow(1600, 1900, 0.8, 0.0, 6, 300),
        MaiaRow(1800, 1900, 0.6, 0.0, 8, 180),
        MaiaRow(2000, 1900, 0.5, 0.0, 10, 100),
        MaiaRow(2200, 1900, 0.4, 0.0, 12, 60),
        MaiaRow(2400, 1900, 0.3, 0.0, 14, 35),
    )

    /** Above this rating bots play in the engine mode only. */
    const val MAIA_UP_TO = 2400

    fun settingsFor(profile: BotProfile, maiaAvailable: Boolean = false): StrengthSettings {
        val engine = engineSettings(profile)
        // The engine settings stay underneath: they are the fallback if a network cannot load.
        return if (maiaAvailable && profile.rating <= MAIA_UP_TO) maiaSettings(profile, engine) else engine
    }

    private fun engineSettings(profile: BotProfile): StrengthSettings {
        val rating = profile.rating
        if (rating >= 3000) return StrengthSettings(20, null, 0.0)
        val row = interpolate(rating)
        // "Mistakes" in the builder: Never plays 4 levels cleaner, Often 4 levels sloppier.
        val level = (row.level + (1f - profile.mistakeLevel) * 4).roundToInt().coerceIn(0, 20)
        val randomChance = (row.random * profile.mistakeLevel).coerceIn(0.0, 0.5)
        return StrengthSettings(level, row.depth.roundToInt().coerceAtLeast(1), randomChance)
    }

    private fun maiaSettings(profile: BotProfile, engine: StrengthSettings): StrengthSettings {
        val rating = profile.rating
        val upper = maiaTable.indexOfFirst { it.rating >= rating }.let { if (it < 0) maiaTable.lastIndex else it }
        val b = maiaTable[upper]
        val a = if (upper == 0 || rating >= b.rating) b else maiaTable[upper - 1]
        val t = if (a === b) 0.0 else (rating - a.rating).toDouble() / (b.rating - a.rating)
        fun mix(x: Double, y: Double) = x + (y - x) * t
        // "Mistakes" in the builder: Never samples sharper and checks tighter, Often the reverse.
        val sloppiness = profile.mistakeLevel.toDouble()
        val temperature = mix(a.temperature, b.temperature) * (0.6 + 0.4 * sloppiness)
        val margin = (b.margin ?: a.margin)?.let { m ->
            val base = if (a.margin != null && b.margin != null) mix(a.margin.toDouble(), b.margin.toDouble()) else m.toDouble()
            (base * (0.5 + 0.5 * sloppiness)).roundToInt()
        }
        val depth = b.checkDepth ?: a.checkDepth
        val net = if (t < 0.5) a.net else b.net
        val random = (mix(a.random, b.random) * sloppiness).coerceIn(0.0, 0.5)
        return engine.copy(randomMoveChance = random, maia = MaiaSettings(net, temperature.coerceIn(0.05, 3.0), depth, margin))
    }

    /** How many plies a bot follows its book: beginners leave theory early. */
    fun bookDepth(rating: Int): Int = when {
        rating < 600 -> 4
        rating < 1000 -> 6
        rating < 1400 -> 10
        rating < 1800 -> 16
        else -> Int.MAX_VALUE
    }

    private fun interpolate(rating: Int): Row {
        if (rating <= table.first().rating) return table.first()
        val upper = table.indexOfFirst { it.rating >= rating }
        if (upper < 0) return table.last()
        val a = table[upper - 1]
        val b = table[upper]
        val t = (rating - a.rating).toDouble() / (b.rating - a.rating)
        fun mix(x: Double, y: Double) = x + (y - x) * t
        return Row(rating, mix(a.level, b.level), mix(a.depth, b.depth), mix(a.random, b.random))
    }
}

/**
 * Chooses among a shallow search's best moves like Stockfish's Skill Level
 * (`Skill::pick_best`): each move gets a push of `(weakness * gap + delta * random) / 128`,
 * where weakness is `120 - 2 * level`, gap is how far it trails the best move and delta the
 * spread of the candidates, capped at a pawn. Low levels pick almost freely among the four;
 * level 20 always plays the best move. A bot's style adds a small lean on top.
 */
object SkillPicker {
    data class Choice(val uci: String, val kind: BotMoveKind)

    private const val PAWN = 100
    /** How much a style can tilt the choice, in centipawns. */
    private const val STYLE_CP = 25.0
    /** A pick this far below the best move counts as a mistake. */
    const val MISTAKE_CP = 90

    fun pick(position: Position, candidates: List<BotCandidate>, level: Int, profile: BotProfile, random: Random): Choice {
        val top = candidates.first().score
        if (level >= 20 || candidates.size == 1) return Choice(candidates.first().uci, BotMoveKind.ENGINE)
        val weakness = 120 - 2 * level
        val delta = min(top - candidates.last().score, PAWN)
        var best = candidates.first()
        var bestValue = Long.MIN_VALUE
        for (candidate in candidates) {
            val gap = (top - candidate.score).toLong()
            val push = (weakness * gap + delta * random.nextInt(weakness)) / 128
            val value = candidate.score + push + styleLean(position, candidate.uci, profile)
            if (value >= bestValue) {
                bestValue = value
                best = candidate
            }
        }
        return Choice(best.uci, if (top - best.score >= MISTAKE_CP) BotMoveKind.MISTAKE else BotMoveKind.ENGINE)
    }

    private fun styleLean(position: Position, uci: String, profile: BotProfile): Long {
        if (profile.aggression == 0f && profile.trades == 0f) return 0
        val move = LegalMoveGenerator.generateLegalMoves(position).firstOrNull { it.uci == uci } ?: return 0
        val lean = profile.aggression * BotStyleScore.aggression(position, move) + profile.trades * BotStyleScore.trade(position, move)
        return (lean * STYLE_CP).toLong()
    }
}

/** Orders Maia's moves for a bot: by chance, weighted by probability ^ (1 / temperature) and style. */
object MaiaSampler {
    private const val STYLE_STRENGTH = 0.6

    /** Up to [limit] distinct moves in the order the bot will try them. */
    fun order(position: Position, probabilities: Map<String, Float>, temperature: Double, profile: BotProfile, random: Random, limit: Int): List<String> {
        val styled = profile.aggression != 0f || profile.trades != 0f
        val moves = if (styled) LegalMoveGenerator.generateLegalMoves(position).associateBy { it.uci } else emptyMap()
        val weights = probabilities.mapValues { (uci, p) ->
            var w = p.toDouble().pow(1.0 / temperature)
            val move = moves[uci]
            if (move != null) {
                val lean = profile.aggression * BotStyleScore.aggression(position, move) + profile.trades * BotStyleScore.trade(position, move)
                w *= exp(STYLE_STRENGTH * lean)
            }
            w
        }.toMutableMap()
        val order = mutableListOf<String>()
        while (order.size < limit && weights.isNotEmpty()) {
            val pick = pickWeighted(weights.toList(), random)
            order += pick
            weights.remove(pick)
        }
        return order
    }
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
