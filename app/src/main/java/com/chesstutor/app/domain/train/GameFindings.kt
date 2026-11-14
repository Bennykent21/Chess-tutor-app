package com.chesstutor.app.domain.train

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.SanFormatter
import com.chesstutor.app.domain.account.GameOutcome
import com.chesstutor.app.domain.account.GameReview
import com.chesstutor.app.domain.account.GameSummary
import com.chesstutor.app.domain.account.MoveQuality
import com.chesstutor.app.domain.repertoire.RepertoireBook
import com.chesstutor.app.domain.repertoire.RepertoireIndex

/** A repeated pattern in the learner's own games (Phase 12, step 4, mock-up round 4). */
sealed interface GameFinding {
    /** How many games it rests on, for the order of the list. */
    val weight: Int

    /**
     * In one of the learner's openings they left the line at the same place: [misses] of the
     * [reached] games that came here, most often with [played] instead of [right].
     */
    data class LineSlip(
        val bookId: String,
        val bookTitle: String,
        val side: PieceColor,
        val fen: String,
        val path: List<String>,
        /** The opponent's move that led here ("3.e5"), or null at the start. */
        val after: String?,
        val played: String,
        val right: String,
        val misses: Int,
        val reached: Int,
        val note: String?,
    ) : GameFinding {
        override val weight: Int get() = misses * 3
    }

    /** Endings of [kind] the learner reached a pawn or more up, and how many of those they won. */
    data class EndingResult(val kind: EndingKind, val won: Int, val reached: Int) : GameFinding {
        override val weight: Int get() = (reached - won) * 2
    }

    /** In reviewed games the learner made the same mistake in the same position [times] of [reached] times. */
    data class RepeatedMistake(
        val fen: String,
        val opening: String?,
        val after: String?,
        val played: String,
        val better: String?,
        val times: Int,
        val reached: Int,
    ) : GameFinding {
        override val weight: Int get() = times * 3
    }
}

/** Endings with a matching set in Train's endgames. */
enum class EndingKind(val title: String, val setId: String) {
    ROOK("Rook endings", "rook-endings"),
    PAWN("King and pawn", "king-pawn"),
}

/**
 * Finds the learner's repeated mistakes in their saved games. Pure, and cheap enough for a few
 * hundred games: line slips and endings need no engine; repeated mistakes use the reviews the
 * learner already ran. Only findings with enough games behind them are kept.
 */
object GameFindings {
    /** A line slip needs this many games that left the line the same way. */
    const val MIN_SLIPS = 2

    /** An ending result needs this many games that reached it. */
    const val MIN_ENDINGS = 3

    /** A repeated mistake needs this many games. */
    const val MIN_MISTAKES = 2

    /** Line slips and repeated mistakes are looked for in the first this many plies. */
    const val OPENING_PLIES = 30

    fun of(games: List<GameSummary>, books: List<RepertoireBook>, reviews: Map<String, GameReview>): List<GameFinding> =
        (lineSlips(games, books) + endings(games) + mistakes(games, reviews)).sortedByDescending { it.weight }

    fun lineSlips(games: List<GameSummary>, books: List<RepertoireBook>): List<GameFinding.LineSlip> = books.flatMap { book ->
        val reached = HashMap<String, Int>()
        val missed = HashMap<String, MutableList<String>>()
        val before = HashMap<String, Pair<Position, String?>>()
        for (game in games) {
            if (game.startFen != null || game.userColor != book.side) continue
            var position = Position.initial()
            var previous: String? = null
            for ((ply, uci) in game.moves.withIndex()) {
                if (ply >= OPENING_PLIES) break
                val node = book.at(position)
                if (node != null && node.own && book.isCard(node) && node.move != null && position.sideToMove == book.side) {
                    reached.merge(node.key, 1, Int::plus)
                    before.putIfAbsent(node.key, position to previous)
                    if (uci != node.move && uci !in node.also) {
                        missed.getOrPut(node.key) { mutableListOf() } += uci
                        break
                    }
                }
                val next = RepertoireIndex.play(position, uci) ?: break
                previous = numbered(position, uci)
                position = next
            }
        }
        val index = RepertoireIndex(book)
        missed.filterValues { it.size >= MIN_SLIPS }.mapNotNull { (key, moves) ->
            val node = book.nodes[key] ?: return@mapNotNull null
            val (at, after) = before[key] ?: return@mapNotNull null
            val common = moves.groupingBy { it }.eachCount().maxBy { it.value }.key
            GameFinding.LineSlip(
                bookId = book.id,
                bookTitle = book.title,
                side = book.side,
                fen = at.toFen(),
                path = index.pathTo(key).orEmpty(),
                after = after,
                played = numbered(at, common),
                right = numbered(at, node.move!!),
                misses = moves.size,
                reached = reached[key] ?: moves.size,
                note = node.note?.text?.let(::firstSentence),
            )
        }
    }

    fun endings(games: List<GameSummary>): List<GameFinding.EndingResult> = EndingKind.entries.mapNotNull { kind ->
        var reached = 0
        var won = 0
        for (game in games) {
            if (game.outcome == GameOutcome.UNKNOWN) continue
            val positions = positionsOf(game)
            val hit = positions.indices.any { i ->
                val now = positions[i]
                val next = positions.getOrNull(i + 1) ?: now
                kindOf(now) == kind && lead(now, game.userColor) >= 1 && lead(next, game.userColor) >= 1
            }
            if (hit) {
                reached++
                if (game.outcome == GameOutcome.WIN) won++
            }
        }
        GameFinding.EndingResult(kind, won, reached).takeIf { reached >= MIN_ENDINGS }
    }

    fun mistakes(games: List<GameSummary>, reviews: Map<String, GameReview>): List<GameFinding.RepeatedMistake> {
        data class Seen(val position: String, val uci: String)
        val made = HashMap<Seen, MutableList<Pair<GameSummary, Int>>>()
        val reached = HashMap<String, Int>()
        for (game in games) {
            val review = reviews[game.id] ?: continue
            val mine = review.moves.filter { it.mover == game.userColor && it.ply <= OPENING_PLIES }
            mine.map { keyOf(it.fenBefore) }.toSet().forEach { reached.merge(it, 1, Int::plus) }
            mine.filter { it.quality == MoveQuality.MISTAKE || it.quality == MoveQuality.BLUNDER }.forEach { move ->
                made.getOrPut(Seen(keyOf(move.fenBefore), move.uci)) { mutableListOf() } += game to move.ply
            }
        }
        return made.filterValues { it.size >= MIN_MISTAKES }.map { (seen, where) ->
            val (game, ply) = where.first()
            val move = reviews.getValue(game.id).move(ply)!!
            val position = Position.fromFen(move.fenBefore)
            val previous = if (ply >= 2) game.moves.getOrNull(ply - 2) else null
            val prior = previous?.let { reviews.getValue(game.id).move(ply - 1)?.fenBefore }?.let(Position::fromFen)
            GameFinding.RepeatedMistake(
                fen = move.fenBefore,
                opening = where.mapNotNull { it.first.openingName }.firstOrNull(),
                after = prior?.let { numbered(it, previous) },
                played = numbered(position, seen.uci),
                better = move.bestSan?.let { "${move.numberLabel}$it" },
                times = where.size,
                reached = reached[seen.position] ?: where.size,
            )
        }
    }

    /** Which ending the position is, if any: only kings and pawns, or only rooks besides them. */
    fun kindOf(position: Position): EndingKind? {
        val types = position.squares.filterNotNull().map { it.type }.filter { it != PieceType.KING && it != PieceType.PAWN }
        return when {
            types.isEmpty() -> if (position.squares.any { it?.type == PieceType.PAWN }) EndingKind.PAWN else null
            types.all { it == PieceType.ROOK } -> EndingKind.ROOK
            else -> null
        }
    }

    /** The learner's material lead in pawns (pawn 1, knight and bishop 3, rook 5, queen 9). */
    fun lead(position: Position, side: PieceColor): Int = position.squares.filterNotNull().sumOf { piece ->
        val value = when (piece.type) {
            PieceType.PAWN -> 1
            PieceType.KNIGHT, PieceType.BISHOP -> 3
            PieceType.ROOK -> 5
            PieceType.QUEEN -> 9
            PieceType.KING -> 0
        }
        if (piece.color == side) value else -value
    }

    private fun positionsOf(game: GameSummary): List<Position> {
        var position = game.startFen?.let { Position.tryFromFen(it).getOrNull() } ?: Position.initial()
        val out = mutableListOf(position)
        for (uci in game.moves) {
            position = RepertoireIndex.play(position, uci) ?: break
            out += position
        }
        return out
    }

    private fun keyOf(fen: String): String = fen.split(' ').take(4).joinToString(" ")

    /** "3.e5" or "3...e6". */
    private fun numbered(position: Position, uci: String): String {
        val move = LegalMoveGenerator.generateLegalMoves(position).firstOrNull { it.uci == uci } ?: return uci
        val number = "${position.fullmoveNumber}${if (position.sideToMove == PieceColor.WHITE) "." else "..."}"
        return number + SanFormatter.format(position, move)
    }

    private fun firstSentence(text: String): String = text.substringBefore(". ").let { if (it.endsWith(".")) it else "$it." }
}
