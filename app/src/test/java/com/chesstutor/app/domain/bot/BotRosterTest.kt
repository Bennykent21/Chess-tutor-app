package com.chesstutor.app.domain.bot

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.repetitionKey
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.domain.opening.Repertoire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BotRosterTest {

    private val tree by lazy { File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream) }

    private fun keyAfter(uci: String): String {
        val start = Position.initial()
        return LegalMoveGenerator.makeMove(start, LegalMoveGenerator.generateLegalMoves(start).first { it.uci == uci }).repetitionKey()
    }

    @Test
    fun twelveBotsFromBeginnerToFullStrengthWithUniqueIds() {
        val bots = BotRoster.bots
        assertEquals(12, bots.size)
        assertEquals(bots.map { it.id }.distinct(), bots.map { it.id })
        assertEquals("weakest first", bots.sortedBy { it.rating }, bots)
        assertEquals(250, bots.first().rating)
        assertEquals(3000, bots.last().rating)
        assertTrue("Wayne stays from the old Arena", bots.any { it.name == "Wayne" })
    }

    @Test
    fun everyPickedOpeningExistsInTheDatabase() {
        BotRoster.bots.forEach { bot ->
            (bot.profile.whiteOpenings + bot.profile.blackOpenings).forEach { name ->
                assertTrue("${bot.name}: \"$name\" names no line", tree.linesWithin(listOf(name)).isNotEmpty())
            }
        }
    }

    @Test
    fun everyBookBotAnswersBothKingAndQueenPawnOpeningsAsBlack() {
        BotRoster.bots.filter { it.profile.blackOpenings.isNotEmpty() }.forEach { bot ->
            val repertoire = Repertoire.of(tree, bot.profile.whiteOpenings, bot.profile.blackOpenings)
            listOf("e2e4", "d2d4").forEach { first ->
                assertTrue("${bot.name} has no book reply to $first", repertoire.movesFor(PieceColor.BLACK, keyAfter(first)).isNotEmpty())
            }
            assertTrue("${bot.name} has no first move as White", repertoire.movesFor(PieceColor.WHITE, Position.initial().repetitionKey()).isNotEmpty())
        }
    }

    @Test
    fun quickMatchFindsTheClosestBot() {
        assertEquals("wayne", BotRoster.closestTo(650).id)
        assertEquals("bolt", BotRoster.closestTo(3200).id)
        assertEquals("pebble", BotRoster.closestTo(0).id)
    }
}
