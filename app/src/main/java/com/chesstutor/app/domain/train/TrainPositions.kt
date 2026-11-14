package com.chesstutor.app.domain.train

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where a position comes from in Train (Phase 12, step 3): "Win this" endgames and "Convert this" middlegames. */
enum class PositionKind { ENDGAME, MIDDLEGAME }

/** What the learner must do against Stockfish: win it, hold the draw, or turn a clear edge into a win. */
enum class PositionGoal { WIN, HOLD, CONVERT }

/** One position to play out. [aim] is the short line under the board ("Promote the pawn"). */
data class TrainPosition(
    val id: String,
    val setId: String,
    val title: String,
    val fen: String,
    val goal: PositionGoal,
    val aim: String,
) {
    val side: PieceColor get() = Position.fromFen(fen).sideToMove
}

/** A theme of positions ("King and pawn"), played in order. */
data class PositionSet(
    val id: String,
    val kind: PositionKind,
    val title: String,
    val positions: List<TrainPosition>,
)

/**
 * Reads `assets/positions/positions.json`. Each position is a FEN, or {"fen", "goal", "aim"} to
 * differ from its set; Stockfish checks every goal in `tools/check_positions.py`.
 */
object PositionParser {
    private val adapter = Moshi.Builder().build().adapter(Any::class.java)

    fun parse(json: String, source: String = "positions.json"): List<PositionSet> {
        val root = runCatching { adapter.fromJson(json) as Map<*, *> }.getOrElse { throw IllegalArgumentException("$source: not valid JSON (${it.message})") }
        val sets = root["sets"] as? List<*> ?: throw IllegalArgumentException("$source: no \"sets\"")
        return sets.mapIndexed { i, entry ->
            val set = entry as? Map<*, *> ?: throw IllegalArgumentException("$source: set ${i + 1} is not an object")
            val id = set.text("id", "$source set ${i + 1}")
            val at = "$source set \"$id\""
            val goal = goalOf(set.text("goal", at), at)
            val aim = set.text("aim", at)
            val title = set.text("title", at)
            val kind = when (val k = set.text("kind", at)) {
                "endgame" -> PositionKind.ENDGAME
                "middlegame" -> PositionKind.MIDDLEGAME
                else -> throw IllegalArgumentException("$at: kind \"$k\" is not endgame or middlegame")
            }
            val positions = (set["positions"] as? List<*>).orEmpty().mapIndexed { n, p ->
                val where = "$at position ${n + 1}"
                val (fen, own) = when (p) {
                    is String -> p to null
                    is Map<*, *> -> p.text("fen", where) to p
                    else -> throw IllegalArgumentException("$where: a FEN or an object")
                }
                Position.tryFromFen(fen).getOrElse { throw IllegalArgumentException("$where: bad FEN \"$fen\" (${it.message})") }
                TrainPosition(
                    id = "$id-${n + 1}",
                    setId = id,
                    title = title,
                    fen = fen,
                    goal = (own?.get("goal") as? String)?.let { goalOf(it, where) } ?: goal,
                    aim = own?.get("aim") as? String ?: aim,
                )
            }
            if (positions.isEmpty()) throw IllegalArgumentException("$at: no positions")
            PositionSet(id, kind, title, positions)
        }
    }

    private fun goalOf(text: String, at: String) = when (text) {
        "win" -> PositionGoal.WIN
        "hold" -> PositionGoal.HOLD
        "convert" -> PositionGoal.CONVERT
        else -> throw IllegalArgumentException("$at: goal \"$text\" is not win, hold or convert")
    }

    private fun Map<*, *>.text(key: String, at: String): String =
        (this[key] as? String)?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("$at: \"$key\" is missing")
}

/** How the learner did with one position: ever won, and the day (epoch day) a slipped one comes back. */
data class PositionRecord(val won: Boolean = false, val dueDay: Long? = null)

/** The learner's results with Train's positions, kept on the phone. */
interface PositionRecords {
    fun all(): StateFlow<Map<String, PositionRecord>>

    fun save(id: String, record: PositionRecord)
}

/** For tests and previews. */
class InMemoryPositionRecords(initial: Map<String, PositionRecord> = emptyMap()) : PositionRecords {
    private val state = MutableStateFlow(initial)

    override fun all(): StateFlow<Map<String, PositionRecord>> = state.asStateFlow()

    override fun save(id: String, record: PositionRecord) {
        state.value = state.value + (id to record)
    }
}

/** Which positions of a set to play now. */
object PositionQueue {
    /**
     * The positions not yet won and not waiting for another day, in order; when there are none,
     * the whole set again, so a finished set can always be replayed.
     */
    fun of(set: PositionSet, records: Map<String, PositionRecord>, today: Long): List<TrainPosition> =
        set.positions.filter { p -> records[p.id].let { it == null || (!it.won && (it.dueDay ?: 0) <= today) } }
            .ifEmpty { set.positions }
}
