package com.chesstutor.app.domain.repertoire

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.GZIPInputStream

/**
 * The sample file (`build_repertoire.py sample`: made-up games, a shallow Stockfish) is a
 * real file from the script, so these tests read exactly what the app will bundle.
 */
class RepertoireParserTest {

    private val sampleJson: String by lazy {
        GZIPInputStream(javaClass.classLoader!!.getResourceAsStream("repertoire/sample.json.gz")!!)
            .bufferedReader().use { it.readText() }
    }
    private val sample: RepertoireBook by lazy { RepertoireParser.parse(sampleJson, "sample") }

    @Test
    fun theSampleLoads() {
        assertEquals("sample", sample.id)
        assertEquals(PieceColor.WHITE, sample.side)
        assertEquals(listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4"), sample.start)
        assertEquals(4, sample.source.bands.size)
        assertTrue(sample.nodes.size > 20)
        assertTrue(sample.cards(RepertoireLevel.CORE).isNotEmpty())
        assertTrue(sample.cards().size > sample.cards(RepertoireLevel.CORE).size)
        assertTrue(sample.cards().all { it.own && it.move != null })
    }

    @Test
    fun gzipAndPlainReadAlike() {
        val gz = RepertoireParser.fromStream(javaClass.classLoader!!.getResourceAsStream("repertoire/sample.json.gz")!!)
        val plain = RepertoireParser.fromStream(sampleJson.byteInputStream())
        assertEquals(gz.nodes.keys, plain.nodes.keys)
    }

    @Test
    fun theStartLineLeadsThroughTheBook() {
        var position = Position.initial()
        for (uci in sample.start) {
            val node = assertNotNullAndGet(sample.at(position))
            if (node.own) assertEquals(uci, node.move) else assertEquals(listOf(uci), node.replies.map { it.uci })
            position = play(position, uci)
        }
        val afterStart = assertNotNullAndGet(sample.at(position))
        assertFalse(afterStart.own)
        assertTrue(afterStart.replies.size > 1)
        assertEquals(RepertoireLevel.CORE, afterStart.level)
    }

    @Test
    fun repliesKeepCommonAndGoodApart() {
        val afterStart = sample.at(sample.start.fold(Position.initial(), ::play))!!
        val shares = afterStart.replies.map { it.share }
        assertEquals(shares.sortedDescending(), shares)
        assertTrue(shares.sum() <= 1.0001)
        assertTrue(afterStart.replies.all { it.verdict != null && it.loss != null })
        val top = afterStart.replies.first()
        assertEquals(top.share, top.shareFor(null), 0.0)
        assertEquals(top.bandShares[2]!!, top.shareFor(2), 0.0)
    }

    @Test
    fun bandsFollowTheChessComRating() {
        assertNull(sample.bandFor(null))
        assertEquals(0, sample.bandFor(650))
        assertEquals(1, sample.bandFor(1000))
        assertEquals(2, sample.bandFor(1799))
        assertEquals(3, sample.bandFor(2600))
    }

    @Test
    fun aCardIsNeverEasierThanTheCardBeforeIt() {
        for (card in sample.cards()) {
            val after = sample.at(play(Position.tryFromFen(card.key + " 0 1").getOrThrow(), card.move!!)) ?: continue
            assertTrue(after.level >= card.level)
        }
    }

    @Test
    fun brokenFilesAreRefused() {
        assertThrows(RepertoireFormatException::class.java) { RepertoireParser.parse("{", "x") }
        assertThrows(RepertoireFormatException::class.java) { edited { it["format"] = 2.0 } }
        // An illegal move.
        assertThrows(RepertoireFormatException::class.java) { edited { root -> nodes(root).first()["move"] = "e2e5" } }
        // A key the app would write differently (an en passant square no pawn can use).
        assertThrows(RepertoireFormatException::class.java) {
            edited { root -> nodes(root).first { it["ply"] == 1.0 }.let { it["key"] = (it["key"] as String).dropLast(1) + "e3" } }
        }
        // A position no move leads to.
        assertThrows(RepertoireFormatException::class.java) {
            edited { root -> nodes(root).add(nodes(root).last().toMutableMap().also { it["key"] = "4k3/8/8/8/8/8/8/4K3 w - -" }) }
        }
        // A reply that leads to no card.
        assertThrows(RepertoireFormatException::class.java) {
            edited { root ->
                val opp = nodes(root).first { it["turn"] == "opponent" && (it["replies"] as List<*>).size > 1 }
                val replies = (opp["replies"] as List<*>).map { @Suppress("UNCHECKED_CAST") (it as Map<String, Any?>).toMutableMap() }
                replies[0]["uci"] = "a7a6"
                opp["replies"] = replies
            }
        }
    }

    private val adapter = Moshi.Builder().build().adapter(Any::class.java)

    @Suppress("UNCHECKED_CAST")
    private fun nodes(root: MutableMap<String, Any?>): MutableList<MutableMap<String, Any?>> =
        root["nodes"] as MutableList<MutableMap<String, Any?>>

    @Suppress("UNCHECKED_CAST")
    private fun edited(change: (MutableMap<String, Any?>) -> Unit): RepertoireBook {
        val root = (adapter.fromJson(sampleJson) as Map<String, Any?>).toMutableMap()
        root["nodes"] = (root["nodes"] as List<Map<String, Any?>>).map { it.toMutableMap() }.toMutableList()
        change(root)
        return RepertoireParser.parse(adapter.toJson(root), "edited")
    }

    private fun play(position: Position, uci: String): Position =
        LegalMoveGenerator.makeMove(position, LegalMoveGenerator.generateLegalMoves(position).first { it.uci == uci })

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
