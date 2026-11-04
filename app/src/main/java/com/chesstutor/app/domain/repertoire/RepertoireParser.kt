package com.chesstutor.app.domain.repertoire

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.repetitionKey
import com.squareup.moshi.Moshi
import java.io.InputStream
import java.util.zip.GZIPInputStream

/** A repertoire file that cannot be used; [message] says where and why. */
class RepertoireFormatException(message: String) : IllegalArgumentException(message)

/**
 * Reads one repertoire file written by tools/build_repertoire.py (format 1) and checks it
 * completely: every move is legal where it is played, every node is the learner's turn
 * exactly when it says so, and walking the tree from the start (the start moves, the
 * learner's moves and every reply) reaches every node and only nodes that exist. So the
 * app's position keys match the script's, or the file does not load.
 *
 * Shape (numbers per band are in the order of `source.bands`):
 * ```
 * { "format": 1, "id", "title", "side": "white", "start": ["e2e4", ..],
 *   "source": { "data", "games", "bands": [{ "id", "chesscom": [from, to|null] }], "minGames", "minShare", "engine" },
 *   "nodes": [ { "key", "ply", "turn": "own"|"opponent", "games": [..], "data", "level": 0|1|2, "reach",
 *                "eval" | "mate", "name", "note": { "kind": "branch"|"plan"|"trap", "text" },
 *                own: "move", "also": [..], "pitfall": { "uci", "share", "verdict", "loss" },
 *                opponent: "replies": [ { "uci", "games", "share": [pooled, band..], "data", "verdict", "loss",
 *                                         "trap": "punish", "level" } ] } ] }
 * ```
 */
object RepertoireParser {
    private val adapter = Moshi.Builder().build().adapter(Any::class.java)

    const val FORMAT = 1

    fun parse(json: String, source: String = "repertoire file"): RepertoireBook {
        val root = try {
            adapter.fromJson(json)
        } catch (e: Exception) {
            throw RepertoireFormatException("$source: not valid JSON (${e.message})")
        }
        return Reader(source).book(root.obj(source))
    }

    /** Reads gzip'd or plain JSON (asset packaging may unpack the file), by its first bytes. */
    fun fromStream(stream: InputStream, source: String = "repertoire file"): RepertoireBook {
        val buffered = if (stream.markSupported()) stream else stream.buffered()
        buffered.mark(2)
        val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b
        buffered.reset()
        val text = (if (gzip) GZIPInputStream(buffered) else buffered).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parse(text, source)
    }

    private class Reader(private val source: String) {
        fun book(obj: Map<String, Any?>): RepertoireBook {
            val format = obj.int("format", source)
            if (format != FORMAT) fail(source, "format $format is not supported (expected $FORMAT)")
            val id = obj.string("id", source)
            val side = when (val s = obj.string("side", source)) {
                "white" -> PieceColor.WHITE
                "black" -> PieceColor.BLACK
                else -> fail(source, "side \"$s\" must be white or black")
            }
            val start = obj.list("start", source).mapIndexed { i, v -> v as? String ?: fail(source, "start[$i] must be text") }
            val src = source(obj.obj("source", source))
            val nodes = obj.list("nodes", source).mapIndexed { i, v -> node(v.obj("$source nodes[$i]"), "$source nodes[$i]", side, src.bands.size) }
            nodes.groupBy { it.key }.filter { it.value.size > 1 }.keys.firstOrNull()?.let { fail(source, "position \"$it\" appears twice") }
            val book = RepertoireBook(id, obj.string("title", source), side, start, src, nodes)
            walk(book)
            return book
        }

        private fun source(obj: Map<String, Any?>): RepertoireSource {
            val at = "$source source"
            val bands = obj.list("bands", at).mapIndexed { i, v ->
                val band = v.obj("$at bands[$i]")
                val range = band.list("chesscom", "$at bands[$i]")
                RatingBand(
                    id = band.string("id", "$at bands[$i]"),
                    chessComFrom = (range.getOrNull(0) as? Number)?.toInt() ?: 0,
                    chessComTo = (range.getOrNull(1) as? Number)?.toInt(),
                )
            }
            if (bands.isEmpty()) fail(at, "needs at least one rating band")
            return RepertoireSource(
                data = obj["data"] as? String,
                games = (obj["games"] as? Number)?.toInt(),
                bands = bands,
                minGames = obj.int("minGames", at),
                minShare = obj.double("minShare", at),
                engine = obj.string("engine", at),
            )
        }

        private fun node(obj: Map<String, Any?>, at: String, side: PieceColor, bands: Int): RepertoireNode {
            val key = obj.string("key", at)
            val position = Position.tryFromFen("$key 0 1").getOrNull() ?: fail(at, "\"$key\" is not a position")
            if (position.repetitionKey() != key) fail(at, "\"$key\" is not written the way the app writes positions (${position.repetitionKey()})")
            val own = when (val turn = obj.string("turn", at)) {
                "own" -> true
                "opponent" -> false
                else -> fail(at, "turn \"$turn\" must be own or opponent")
            }
            if (own != (position.sideToMove == side)) fail(at, "turn \"${obj["turn"]}\" does not match the side to move")
            val legal = LegalMoveGenerator.generateLegalMoves(position)
            fun legalMove(uci: String, what: String): String =
                uci.also { if (legal.none { m -> m.uci == uci }) fail(at, "$what $uci is not legal here") }
            val move = if (own) legalMove(obj.string("move", at), "move") else null
            val replies = if (own) emptyList() else obj.list("replies", at).mapIndexed { i, v ->
                reply(v.obj("$at replies[$i]"), "$at replies[$i]", bands).also { legalMove(it.uci, "reply") }
            }
            return RepertoireNode(
                key = key,
                ply = obj.int("ply", at),
                own = own,
                games = counts(obj.list("games", at), bands, at),
                dataBacked = obj["data"] as? Boolean ?: fail(at, "\"data\" must be true or false"),
                level = level(obj.int("level", at), at),
                reach = obj.double("reach", at),
                evalCp = (obj["eval"] as? Number)?.toInt(),
                mate = (obj["mate"] as? Number)?.toInt(),
                move = move,
                also = (obj["also"] as? List<*>).orEmpty().mapIndexed { i, v -> legalMove(v as? String ?: fail(at, "also[$i] must be text"), "also") },
                pitfall = (obj["pitfall"] as? Map<*, *>)?.let { pitfall(it.obj("$at pitfall"), "$at pitfall").also { p -> legalMove(p.uci, "pitfall") } },
                replies = replies,
                note = (obj["note"] as? Map<*, *>)?.let { note(it.obj("$at note"), "$at note") },
                name = obj["name"] as? String,
            )
        }

        private fun reply(obj: Map<String, Any?>, at: String, bands: Int): RepertoireReply {
            val shares = (obj["share"] as? List<*>).orEmpty().map { (it as? Number)?.toDouble() }
            return RepertoireReply(
                uci = obj.string("uci", at),
                games = counts(obj.list("games", at), bands, at),
                share = shares.firstOrNull() ?: 0.0,
                bandShares = shares.drop(1),
                dataBacked = obj["data"] as? Boolean ?: fail(at, "\"data\" must be true or false"),
                verdict = (obj["verdict"] as? String)?.let { verdict(it, at) },
                loss = (obj["loss"] as? Number)?.toDouble(),
                punish = obj["trap"] == "punish",
                level = level((obj["level"] as? Number)?.toInt() ?: 2, at),
            )
        }

        private fun pitfall(obj: Map<String, Any?>, at: String) = Pitfall(
            uci = obj.string("uci", at),
            share = obj.double("share", at),
            verdict = verdict(obj.string("verdict", at), at),
            loss = obj.double("loss", at),
        )

        private fun note(obj: Map<String, Any?>, at: String): RepertoireNote {
            val kind = NoteKind.entries.firstOrNull { it.name.equals(obj.string("kind", at), ignoreCase = true) }
                ?: fail(at, "kind \"${obj["kind"]}\" must be branch, plan or trap")
            return RepertoireNote(kind, obj.string("text", at))
        }

        /** Every node is reached from the start, and every reply and start move leads to a node. */
        private fun walk(book: RepertoireBook) {
            val seen = HashSet<String>()
            val queue = ArrayDeque<Pair<Position, Int>>()
            queue += Position.initial() to 0
            while (queue.isNotEmpty()) {
                val (position, ply) = queue.removeFirst()
                val key = position.repetitionKey()
                if (!seen.add(key)) continue
                val node = book.nodes[key] ?: fail(source, "the tree reaches \"$key\" but has no node for it")
                val inStart = ply < book.start.size
                val next = if (node.own) listOf(node.move!!) else node.replies.map { it.uci }
                if (inStart && next != listOf(book.start[ply])) fail(source, "\"$key\" does not follow the start move ${book.start[ply]}")
                for (uci in next) {
                    val child = LegalMoveGenerator.makeMove(position, move(position, uci))
                    // The learner's last move in a line may end the tree; a reply always leads to a card.
                    if (node.own && child.repetitionKey() !in book.nodes) continue
                    queue += child to ply + 1
                }
            }
            book.nodes.keys.firstOrNull { it !in seen }?.let { fail(source, "\"$it\" cannot be reached from the start") }
        }

        private fun move(position: Position, uci: String): Move =
            LegalMoveGenerator.generateLegalMoves(position).first { it.uci == uci }

        private fun counts(list: List<Any?>, bands: Int, at: String): List<Int> {
            if (list.size != bands) fail(at, "games has ${list.size} numbers for $bands bands")
            return list.mapIndexed { i, v -> (v as? Number)?.toInt() ?: fail(at, "games[$i] must be a number") }
        }

        private fun level(value: Int, at: String): RepertoireLevel =
            RepertoireLevel.entries.getOrNull(value) ?: fail(at, "level $value must be 0, 1 or 2")

        private fun verdict(value: String, at: String): MoveVerdict =
            MoveVerdict.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: fail(at, "verdict \"$value\" is unknown")
    }
}

private fun fail(at: String, why: String): Nothing = throw RepertoireFormatException("$at: $why")

@Suppress("UNCHECKED_CAST")
private fun Any?.obj(at: String): Map<String, Any?> = this as? Map<String, Any?> ?: fail(at, "must be an object")

private fun Map<String, Any?>.string(key: String, at: String): String = this[key] as? String ?: fail(at, "\"$key\" must be text")

private fun Map<String, Any?>.int(key: String, at: String): Int = (this[key] as? Number)?.toInt() ?: fail(at, "\"$key\" must be a number")

private fun Map<String, Any?>.double(key: String, at: String): Double = (this[key] as? Number)?.toDouble() ?: fail(at, "\"$key\" must be a number")

private fun Map<String, Any?>.list(key: String, at: String): List<Any?> = this[key] as? List<Any?> ?: fail(at, "\"$key\" must be a list")

private fun Map<String, Any?>.obj(key: String, at: String): Map<String, Any?> = this[key].obj("$at $key")
