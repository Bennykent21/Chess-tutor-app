package com.chesstutor.app.domain.repertoire

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.repetitionKey

/** Core, Standard, Deep (D70): a level opens only when the one before it is mastered. */
enum class RepertoireLevel { CORE, STANDARD, DEEP }

/** Stockfish's verdict on a move, on the game review's scale of lost winning chances (D69). */
enum class MoveVerdict {
    SOUND, INACCURACY, MISTAKE, BLUNDER;

    val isError: Boolean get() = this == MISTAKE || this == BLUNDER
}

/** Why a position has a note: a branch point, a plan or a trap (D74). */
enum class NoteKind { BRANCH, PLAN, TRAP }

data class RepertoireNote(val kind: NoteKind, val text: String)

/** A rating band of the data, by the Chess.com rating of the player making the move (D68). */
data class RatingBand(val id: String, val chessComFrom: Int, val chessComTo: Int?) {
    fun contains(rating: Int): Boolean = rating >= chessComFrom && (chessComTo == null || rating < chessComTo)
}

/** A reply the opponent makes in the tree: how common it is and how good it is, kept apart (D69). */
data class RepertoireReply(
    val uci: String,
    /** Games per band. */
    val games: List<Int>,
    /** Share over every band. */
    val share: Double,
    /** Share in each band, or null where the band had too few games. */
    val bandShares: List<Double?>,
    /** False for the engine's reply where the data ran out. */
    val dataBacked: Boolean,
    val verdict: MoveVerdict?,
    /** Winning chances it gives away, in points. */
    val loss: Double?,
    /** A mistake or blunder the learner's answer punishes. */
    val punish: Boolean,
    /** The level of the card it leads to. */
    val level: RepertoireLevel,
) {
    val totalGames: Int get() = games.sum()

    /** The share among players in [band], or overall when the band is unknown or thin. */
    fun shareFor(band: Int?): Double = band?.let { bandShares.getOrNull(it) } ?: share
}

/** The move players at this level choose most here, when it is a mistake or blunder. */
data class Pitfall(val uci: String, val share: Double, val verdict: MoveVerdict, val loss: Double)

/** One position of the tree. When [own], it is the learner's turn and the position is a card. */
data class RepertoireNode(
    val key: String,
    val ply: Int,
    val own: Boolean,
    val games: List<Int>,
    val dataBacked: Boolean,
    val level: RepertoireLevel,
    /** How often the learner meets this position: the product of the opponent's shares on the way. */
    val reach: Double,
    /** Best play's score, White's view: centipawns, or a mate count. */
    val evalCp: Int?,
    val mate: Int?,
    /** Own nodes: the repertoire move and other moves that are just as good. */
    val move: String?,
    val also: List<String>,
    /** Own nodes: how often players at this level choose [move] here, when the data says. */
    val moveShare: Double? = null,
    val pitfall: Pitfall?,
    /** Opponent nodes: the replies in the tree, most common first. */
    val replies: List<RepertoireReply>,
    val note: RepertoireNote?,
    /** The opening database's name for this position, if a named line ends here. */
    val name: String?,
) {
    val totalGames: Int get() = games.sum()

    fun reply(uci: String): RepertoireReply? = replies.firstOrNull { it.uci == uci }
}

/** Where the data came from (shown on the licences page and in the quality report). */
data class RepertoireSource(
    val data: String?,
    val games: Int?,
    val bands: List<RatingBand>,
    val minGames: Int,
    val minShare: Double,
    val engine: String,
)

/**
 * One taught opening (Phase 10, D68 to D75), read from `assets/repertoire/<id>.json.gz`
 * (tools/build_repertoire.py). Positions are keyed by [repetitionKey]; the move orders that
 * transpose meet at one node.
 */
class RepertoireBook(
    val id: String,
    val title: String,
    val side: PieceColor,
    /** The moves that define the opening, from the start. */
    val start: List<String>,
    val source: RepertoireSource,
    nodeList: List<RepertoireNode>,
) {
    val nodes: Map<String, RepertoireNode> = nodeList.associateBy { it.key }

    fun at(position: Position): RepertoireNode? = nodes[position.repetitionKey()]

    /** Where the opening's defining moves end. They are played for the learner, never asked. */
    val startPosition: Position by lazy { start.fold(Position.initial()) { p, uci -> RepertoireIndex.play(p, uci) ?: p } }

    /** What the opening is about: the note where its defining moves end. */
    val intro: RepertoireNote? get() = at(startPosition)?.note

    /** A card: the learner's turn after the defining moves (1.e4 in the Italian is not something to learn). */
    fun isCard(node: RepertoireNode): Boolean = node.own && node.ply >= start.size

    /** The learner's positions up to [upTo], shallowest first. */
    fun cards(upTo: RepertoireLevel = RepertoireLevel.DEEP): List<RepertoireNode> =
        nodes.values.filter { isCard(it) && it.level <= upTo }.sortedWith(compareBy({ it.ply }, { -it.reach }, { it.key }))

    /** How many of [path]'s first moves are the defining moves, which a run plays by itself. */
    fun startPlies(path: List<String>): Int = if (path.take(start.size) == start) start.size else 0

    /** The data band for a player of [chessComRating], or null when unknown. */
    fun bandFor(chessComRating: Int?): Int? =
        chessComRating?.let { rating -> source.bands.indexOfFirst { it.contains(rating) }.takeIf { it >= 0 } }
}
